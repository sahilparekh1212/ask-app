package com.askapp.audit.rag.eval;

import com.askapp.audit.rag.CodeChunker;
import com.askapp.audit.rag.CorpusLoader;
import com.askapp.audit.rag.DocChunk;
import com.askapp.audit.rag.EmbeddingClient;
import com.askapp.audit.rag.InMemoryVectorStore;
import com.askapp.audit.rag.MarkdownChunker;
import com.askapp.audit.rag.RagProperties;
import com.askapp.audit.rag.RagService;
import com.askapp.audit.rag.ScoredChunk;
import com.askapp.audit.rag.VectorStore;
import com.askapp.audit.rag.VoyageEmbeddingClient;
import com.askapp.audit.rag.rerank.RerankProperties;
import com.askapp.audit.rag.rerank.Reranker;
import com.askapp.audit.rag.rerank.VoyageReranker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The RAG retrieval-quality gate (ADR-0013; see {@code Backend/docs/rag-eval.md}). Tagged
 * {@code eval}, so it is excluded from the normal keyless {@code test} task and run only by the
 * {@code ragEval} Gradle task (which supplies {@code VOYAGE_API_KEY}); without the key it skips.
 *
 * <p>It builds the <em>real</em> retrieval pipeline — the bundled corpus, Voyage embeddings, the
 * exact in-memory store, and {@code RagService} including the reranker (ADR-0012) — runs all 100
 * ground-truth questions, computes recall@k / MRR / nDCG@k / hit-rate with {@link RagEvalMetrics},
 * writes a report, and fails the build if any aggregate is below its configured threshold.
 */
@Tag("eval")
class RagEvalTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Test
	void retrievalMeetsConfiguredQualityThresholds() throws IOException {
		String apiKey = System.getenv("VOYAGE_API_KEY");
		assumeTrue(apiKey != null && !apiKey.isBlank(),
			"VOYAGE_API_KEY not set — skipping RAG eval (it needs the live embeddings + rerank API)");

		Thresholds thresholds = Thresholds.load();
		List<GroundTruthQuestion> questions = loadGroundTruth();
		RagService rag = buildPipeline(apiKey);

		List<QuestionScore> scores = new ArrayList<>(questions.size());
		for (GroundTruthQuestion q : questions) {
			// Score over DISTINCT sources: retrieval returns chunks, but relevance labels are
			// per-file, and one file yields many chunks. Deduping keeps each source to one rank slot,
			// so nDCG stays in [0, 1] (otherwise several chunks of the expected file inflate DCG past
			// the ideal) and RR/recall read at the file granularity the labels are written in.
			List<String> ranked = rag.search(q.question(), thresholds.topK()).stream()
				.map(ScoredChunk::source)
				.distinct()
				.toList();
			scores.add(new QuestionScore(q.id(), q.expectedSources(), ranked,
				RagEvalMetrics.hit(ranked, q.expectedSources()),
				RagEvalMetrics.recall(ranked, q.expectedSources()),
				RagEvalMetrics.reciprocalRank(ranked, q.expectedSources()),
				RagEvalMetrics.ndcg(ranked, q.expectedSources())));
		}

		Aggregate agg = Aggregate.of(scores);
		writeReport(scores, agg, thresholds);
		printSummary(scores, agg, thresholds);

		List<String> failures = new ArrayList<>();
		record Bar(String name, double value, double min) {
		}
		for (Bar bar : List.of(
			new Bar("hit-rate", agg.hitRate(), thresholds.minHitRate()),
			new Bar("recall@k", agg.recall(), thresholds.minRecallAtK()),
			new Bar("MRR", agg.mrr(), thresholds.minMrr()),
			new Bar("nDCG@k", agg.ndcg(), thresholds.minNdcgAtK()))) {
			if (bar.value() < bar.min()) {
				failures.add(String.format("%s %.3f < %.3f", bar.name(), bar.value(), bar.min()));
			}
		}
		assertThat(failures)
			.as("RAG retrieval quality below threshold (top-k=%d, %d questions) — %s",
				thresholds.topK(), scores.size(), failures)
			.isEmpty();
	}

	private RagService buildPipeline(String apiKey) {
		RagProperties props = new RagProperties(true, apiKey, "voyage-3.5-lite", 1024, 2000, 5);
		EmbeddingClient embeddingClient = new VoyageEmbeddingClient(props);
		VectorStore store = new InMemoryVectorStore();
		indexCorpus(props, embeddingClient, store);
		RerankProperties rerankProps = new RerankProperties(true, "rerank-2.5-lite", 20);
		Reranker reranker = new VoyageReranker(props, rerankProps);
		return new RagService(props, embeddingClient, store, reranker);
	}

	/**
	 * Index the bundled docs + source corpus the same way {@code RagIndexer} does, minus the
	 * security-master reference data (which needs a datasource; the ground truth targets docs/code).
	 */
	private void indexCorpus(RagProperties props, EmbeddingClient embeddingClient, VectorStore store) {
		CorpusLoader loader = new CorpusLoader();
		MarkdownChunker markdownChunker = new MarkdownChunker(props);
		CodeChunker codeChunker = new CodeChunker(props);
		List<DocChunk> corpus = new ArrayList<>();
		for (CorpusLoader.CorpusDocument doc : loader.load()) {
			corpus.addAll(doc.source().endsWith(".md")
				? markdownChunker.chunk(doc.source(), doc.content())
				: codeChunker.chunk(doc.source(), doc.content()));
		}
		if (corpus.isEmpty()) {
			throw new IllegalStateException("RAG corpus is empty — is rag-corpus/ on the test classpath?");
		}
		List<float[]> vectors = embeddingClient.embedDocuments(corpus.stream().map(DocChunk::content).toList());
		store.upsert(corpus, vectors);
		System.out.printf("[rag-eval] indexed %d chunks from the bundled corpus%n", corpus.size());
	}

	private List<GroundTruthQuestion> loadGroundTruth() throws IOException {
		List<GroundTruthQuestion> questions = new ArrayList<>();
		try (InputStream in = resource("/rag-eval/ground-truth.jsonl")) {
			for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
				if (!line.isBlank()) {
					questions.add(MAPPER.readValue(line, GroundTruthQuestion.class));
				}
			}
		}
		if (questions.isEmpty()) {
			throw new IllegalStateException("No ground-truth questions loaded");
		}
		return questions;
	}

	private static InputStream resource(String path) {
		InputStream in = RagEvalTest.class.getResourceAsStream(path);
		if (in == null) {
			throw new IllegalStateException("Missing eval resource on classpath: " + path);
		}
		return in;
	}

	private void printSummary(List<QuestionScore> scores, Aggregate agg, Thresholds thresholds) {
		System.out.printf("%n===== RAG retrieval eval (top-k=%d, %d questions) =====%n",
			thresholds.topK(), scores.size());
		System.out.printf("hit-rate %.3f (>= %.2f)   recall@k %.3f (>= %.2f)   MRR %.3f (>= %.2f)   nDCG@k %.3f (>= %.2f)%n",
			agg.hitRate(), thresholds.minHitRate(), agg.recall(), thresholds.minRecallAtK(),
			agg.mrr(), thresholds.minMrr(), agg.ndcg(), thresholds.minNdcgAtK());
		List<QuestionScore> misses = scores.stream().filter(s -> s.hit() == 0.0).toList();
		if (misses.isEmpty()) {
			System.out.println("all questions retrieved at least one expected source");
		} else {
			System.out.printf("%d question(s) missed (no expected source in top-k):%n", misses.size());
			misses.forEach(m -> System.out.printf("  - %s  expected=%s  got=%s%n",
				m.id(), m.expectedSources(), m.ranked()));
		}
		System.out.println("======================================================");
	}

	private void writeReport(List<QuestionScore> scores, Aggregate agg, Thresholds thresholds) {
		String dir = System.getProperty("rag.eval.report.dir", "build/rag-eval");
		try {
			Path out = Path.of(dir);
			Files.createDirectories(out);
			Map<String, Object> json = new LinkedHashMap<>();
			json.put("topK", thresholds.topK());
			json.put("questionCount", scores.size());
			json.put("hitRate", agg.hitRate());
			json.put("recallAtK", agg.recall());
			json.put("mrr", agg.mrr());
			json.put("ndcgAtK", agg.ndcg());
			json.put("thresholds", Map.of("minHitRate", thresholds.minHitRate(),
				"minRecallAtK", thresholds.minRecallAtK(), "minMrr", thresholds.minMrr(),
				"minNdcgAtK", thresholds.minNdcgAtK()));
			json.put("questions", scores);
			Files.writeString(out.resolve("report.json"),
				MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(json));
			Files.writeString(out.resolve("report.md"), markdown(scores, agg, thresholds));
			System.out.printf("[rag-eval] report written to %s%n", out.toAbsolutePath());
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to write RAG eval report", e);
		}
	}

	private String markdown(List<QuestionScore> scores, Aggregate agg, Thresholds thresholds) {
		StringBuilder sb = new StringBuilder("# RAG retrieval eval report\n\n");
		sb.append(String.format("Top-k **%d** over **%d** questions.%n%n", thresholds.topK(), scores.size()));
		sb.append("| Metric | Score | Threshold | Pass |\n|---|---|---|---|\n");
		sb.append(row("hit-rate", agg.hitRate(), thresholds.minHitRate()));
		sb.append(row("recall@k", agg.recall(), thresholds.minRecallAtK()));
		sb.append(row("MRR", agg.mrr(), thresholds.minMrr()));
		sb.append(row("nDCG@k", agg.ndcg(), thresholds.minNdcgAtK()));
		List<QuestionScore> misses = scores.stream().filter(s -> s.hit() == 0.0).toList();
		sb.append("\n## Misses (").append(misses.size()).append(")\n\n");
		if (misses.isEmpty()) {
			sb.append("_None — every question retrieved at least one expected source._\n");
		} else {
			for (QuestionScore m : misses) {
				sb.append("- `").append(m.id()).append("` expected ").append(m.expectedSources())
					.append("\n");
			}
		}
		return sb.toString();
	}

	private static String row(String name, double value, double min) {
		return String.format("| %s | %.3f | %.2f | %s |%n", name, value, min, value >= min ? "✅" : "❌");
	}

	// ---- eval-local value types ------------------------------------------------------------

	private record QuestionScore(String id, List<String> expectedSources, List<String> ranked,
			double hit, double recall, double reciprocalRank, double ndcg) {
	}

	private record Aggregate(double hitRate, double recall, double mrr, double ndcg) {
		static Aggregate of(List<QuestionScore> scores) {
			return new Aggregate(
				mean(scores, QuestionScore::hit),
				mean(scores, QuestionScore::recall),
				mean(scores, QuestionScore::reciprocalRank),
				mean(scores, QuestionScore::ndcg));
		}

		private static double mean(List<QuestionScore> scores, java.util.function.ToDoubleFunction<QuestionScore> f) {
			return scores.stream().mapToDouble(f).average().orElse(0.0);
		}
	}

	private record Thresholds(int topK, double minHitRate, double minRecallAtK, double minMrr, double minNdcgAtK) {
		static Thresholds load() throws IOException {
			Properties p = new Properties();
			try (InputStream in = resource("/rag-eval/thresholds.properties")) {
				p.load(in);
			}
			return new Thresholds(
				intProp(p, "top-k"),
				dblProp(p, "min-hit-rate"),
				dblProp(p, "min-recall-at-k"),
				dblProp(p, "min-mrr"),
				dblProp(p, "min-ndcg-at-k"));
		}

		// A system property (rag.eval.<key>) overrides the file value, so CI can tune without an edit.
		private static int intProp(Properties p, String key) {
			return Integer.parseInt(System.getProperty("rag.eval." + key, p.getProperty(key)));
		}

		private static double dblProp(Properties p, String key) {
			return Double.parseDouble(System.getProperty("rag.eval." + key, p.getProperty(key)));
		}
	}

}

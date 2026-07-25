# ADR-0013: RAG retrieval evaluation and a build quality gate

**Status:** Accepted

## Context

Reranking (ADR-0012) is a claim — "the top-k is more relevant now" — with no number behind it, and
retrieval quality can silently regress: a doc edit, a chunking change, a model swap, or a reranker
change can push the answering chunk out of the top-k and nothing would catch it. The project already
gates *code* quality on every change (90% coverage, mutation baseline, CVE scan); retrieval quality
deserves the same treatment — a measured number and a threshold the build enforces.

## Decision — a retrieval eval over a fixed ground-truth set, wired as a secret-gated build gate

A ground-truth set of **100 questions**, each labelled with the corpus source(s) that should be
retrieved to answer it. An eval runner builds the **real** retrieval pipeline — the bundled corpus,
Voyage embeddings, the exact in-memory vector store, and `RagService` *including the reranker* — runs
every question through it, and scores the ranked results against the labels with four standard
retrieval metrics:

- **hit@k** — did any expected source make the top-k (the "is the answer even in context" number);
- **recall@k** — fraction of a question's expected sources that made the top-k;
- **MRR** — mean reciprocal rank of the first expected source (rewards ranking it *first*);
- **nDCG@k** — rank-discounted gain, the metric the reranker is meant to move.

The run writes a JSON + Markdown report and then asserts each aggregate against a configured
threshold; below any threshold it exits non-zero and **fails the build**.

### Retrieval-only, no LLM judge

The gate measures **retrieval**, not generated answers. Retrieval scoring is deterministic and cheap
(embeddings + rerank, no generation), so it makes a stable pass/fail a build can hang on — an
LLM-graded answer score is non-deterministic and costs a generation per question, which is a poor
fit for a gate. Answer-quality evaluation (an LLM judge over the assistant's replies) is a possible
later addition, but as a **report**, never the gate.

### The real pipeline, rebuilt per run

The eval embeds the actual bundled corpus and runs the shipped `RagService` + `VoyageReranker`, so it
measures what deploys, not a mock. It uses the **exact in-memory vector store** (same store LOCAL and
tests use), so no pgvector/Postgres is needed in CI, and the scores are exact rather than ANN
approximations. Reference data (the security master) is excluded — it needs a datasource, and the
ground truth targets the docs/code corpus.

### Config, not code

Both the questions (`rag-eval/ground-truth.jsonl`) and the thresholds
(`rag-eval/thresholds.properties`) are resource files, and every threshold is overridable by a
system property, so the bar can be tuned or a question added/relabelled **without touching Java**.
Source labels are matched by **path suffix** (`audit/rag/RagService.java`, not the full package
path), so they survive package renames and path-prefix changes.

### Secret-gated, dedicated CI job

The eval needs a real `VOYAGE_API_KEY`, which fork PRs can't provide. So it is a **`ragEval` Gradle
task** carrying the JUnit `eval` tag — **excluded from the normal `test` task**, so the default
keyless build/coverage gate is unaffected — run by a dedicated `rag-eval` workflow **only when the
Voyage secret is present** (this repo's PRs and pushes); without the secret it skips with a clear
message. It is intentionally *not* a required status check: it gates on the number when it can run,
without making every docs-only PR wait on an embeddings run or blocking forks.

## Alternatives considered

- **LLM-judge answer eval as the gate.** Measures the thing users actually see, but non-deterministic
  and a generation-per-question in cost — unfit for a hard build gate. Deferred as a report.
- **Pinned/mocked embeddings for a keyless gate.** Would run on every PR with no secret, but it would
  measure a fixture, not the real provider — the regressions worth catching (a model/rerank change)
  are exactly the ones a mock can't see.
- **Required PR check.** Strongest, but makes every PR wait on an embeddings+rerank run and can't pass
  on forks (no secret) — friction out of proportion to this repo's PR volume.
- **A handful of smoke questions in the normal test suite.** Cheap, but can't run keyless (needs the
  provider) and too few to be a meaningful quality bar.

## Consequences

- Each eval run re-embeds the corpus (the in-memory store starts empty), so it spends Voyage
  embedding calls plus one rerank + one query-embed per question — acceptable for a portfolio's PR
  volume, and the reason the gate is its own job rather than part of every build.
- The thresholds in `thresholds.properties` are the retrieval-quality contract; a change that drops a
  metric below its bar fails the build until the pipeline or the threshold is deliberately changed.
- Ground truth is maintained alongside the corpus: a doc removed or heavily rewritten may need its
  question relabelled — the report names which questions missed, so drift is visible, not silent.
- The report artifact gives a trend line for retrieval quality over time, and doubles as the live,
  measured verification of the ADR-0012 reranker.

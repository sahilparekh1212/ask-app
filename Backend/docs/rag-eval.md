# RAG retrieval evaluation

How retrieval quality is measured and gated. The decision and its trade-offs are
[ADR-0013](adr/0013-rag-evaluation-and-quality-gate.md); this is the operator's guide — what the
numbers mean, where the config lives, and how to run and tune it.

## What it measures

A fixed set of **100 ground-truth questions**, each labelled with the corpus source(s) that should
be retrieved to answer it. The eval builds the **real** retrieval pipeline — the bundled corpus,
Voyage embeddings, the exact in-memory vector store, and `RagService` *including the reranker*
(ADR-0012) — runs every question, and scores the ranked results against the labels. It measures
**retrieval**, not generated answers (deterministic and cheap, so it can gate a build).

## The metrics

Each is computed per question over the top-k results, then averaged across all questions. `k` is
`top-k` from the thresholds file (default 5). A retrieved chunk *matches* an expected label when its
source path ends with the label (suffix match, so labels survive package/path renames).

| Metric | Per-question definition | Answers |
|---|---|---|
| **hit@k** | 1 if any expected source is in the top-k, else 0 | "Is the answer even in the context window?" |
| **recall@k** | (# expected sources found in top-k) / (# expected sources) | "How much of what should be there is?" |
| **MRR** | 1 / (rank of the first expected source), 0 if none in top-k | "Is the right source ranked *first*?" |
| **nDCG@k** | DCG(binary relevance) / ideal DCG | Rank-discounted quality — the number reranking targets |

## Config — questions and thresholds

Both are resource files under `Backend/Audit/src/test/resources/rag-eval/`, so the eval can be tuned
or extended **without touching Java**:

- **`ground-truth.jsonl`** — one question per line:

  ```json
  {"id": "adr-rerank", "question": "Why is retrieval two-stage with a reranker?", "expectedSources": ["docs/adr/0012-query-reranking.md"], "tags": ["adr", "rag"]}
  ```

  `expectedSources` are path suffixes; a question passes retrieval when a chunk from one of them
  appears in the top-k. Use as much of the tail as needed to be unambiguous
  (`audit/config/SecurityConfig.java`, not just `SecurityConfig.java`).

- **`thresholds.properties`** — the quality bar the build enforces:

  ```properties
  top-k=5
  min-hit-rate=0.92
  min-recall-at-k=0.90
  min-mrr=0.74
  min-ndcg-at-k=0.80
  ```

  These are calibrated below the current pipeline's measured baseline (hit-rate 1.00, recall@k
  1.00, MRR 0.83, nDCG@k 0.88) with headroom — tight enough to catch a real regression, loose
  enough not to flake on normal run-to-run variation.

  Any value is overridable at run time with a system property of the same dotted name, e.g.
  `-Drag.eval.min-mrr=0.70` — so CI can tighten or relax the bar without editing the file.

## Running it

Locally (needs a real key — the eval calls the live embeddings + rerank API):

```bash
export VOYAGE_API_KEY=pa-...
cd Backend && ./gradlew :Audit:ragEval
```

The task prints the aggregate scores, writes a report (`build/rag-eval/report.md` and
`report.json`), and **fails** if any aggregate is below its threshold. Without `VOYAGE_API_KEY` the
task **skips** (it can't retrieve), so it never fails a keyless build.

## The build gate

`ragEval` carries the JUnit `eval` tag and is **excluded from the normal `test` task**, so the
default keyless build and its 90% coverage gate are unaffected. A dedicated **`rag-eval` workflow**
runs it only when the `VOYAGE_API_KEY` secret is available (this repo's own PRs and pushes to
`Backend/**`); fork PRs without the secret skip it. Below-threshold ⇒ non-zero exit ⇒ the job (and
the build) fails. It is deliberately not a required status check — see ADR-0013.

## Maintaining ground truth

The report lists every question that missed, so drift is visible. When a corpus doc is removed or
heavily rewritten, relabel the affected question's `expectedSources` (or drop the question) rather
than lowering a threshold to hide a real regression.

# ADR-0012: Two-stage retrieval — cosine recall, then a reranker for precision

**Status:** Accepted

## Context

RAG retrieval (ADR-0010) returns the top-k chunks by a single signal: cosine similarity between
the query embedding and each chunk embedding. A bi-encoder score is cheap and recalls the right
*neighbourhood*, but it embeds the query and each chunk independently — it never compares them
against each other — so the ordering *inside* that neighbourhood is often wrong: the chunk that
literally answers the question can sit at rank 4 while three topically-near-but-unhelpful chunks
rank above it. The assistant grounds on `rag.default-top-k=5` chunks and the MCP `search_knowledge`
tool returns whatever order retrieval gives, so this mis-ordering degrades answer quality directly.

## Decision — retrieve wide by embedding, then rerank to top-k

Retrieval becomes two stages behind the same `RagService.search` facade:

1. **Recall (unchanged):** embed the query and pull a *wider* candidate pool
   (`rag.rerank.candidate-pool-size`, default 20) from the `VectorStore` by cosine — cast a wider
   net than the k we will actually return.
2. **Precision (new):** re-score those candidates against the query with a **cross-encoder
   reranker** (Voyage `rerank-2.5-lite`), which reads the query and each candidate *together* and
   returns a direct relevance score, and keep the best k.

A `Reranker` interface has two implementations, selected once at startup:

- **`VoyageReranker`** — one `POST /v1/rerank` call, the same server-side-key posture and lazy
  client as `VoyageEmbeddingClient` (it reuses `VOYAGE_API_KEY`). It is **fail-soft**: any rerank
  error falls back to the first-stage cosine order, so a reranker outage degrades *ordering* rather
  than blanking retrieval — the same "retrieval is an enhancement, not a dependency" contract
  ADR-0010 set for the whole feature.
- **`IdentityReranker`** — returns the first k candidates untouched and asks for a candidate pool of
  exactly k (no wider fetch). Selected when `rag.rerank.enabled=false` or RAG is unconfigured, so
  the reranker is free to disable and never fetches embeddings it won't use.

## Why a reranker rather than "better" first-stage retrieval

- **A cross-encoder is a different, stronger signal**, not a tuned version of the same one.
  Query-aware scoring reorders the top of the list where it matters; embedding similarity alone
  can't, however good the embedding model.
- **It is cheap at this corpus size.** Reranking runs over ~20 candidates for one query, not the
  whole index — one extra API call on the retrieval path, no new store, no index rebuild.
- **Same key, same posture.** Voyage already backs embeddings; the reranker is the same account and
  the same graceful-degradation story, so it adds an env var's worth of surface, not a dependency.

## Alternatives considered

- **Keep single-stage cosine.** Simplest, but leaves the top-of-list ordering problem — the thing
  that most affects a 5-chunk grounding budget — unaddressed.
- **Local cross-encoder (ONNX MiniLM reranker).** Keyless, but the same runtime-weight / native-
  dependency cost ADR-0010 rejected for embeddings, for the same reason.
- **LLM-as-reranker** (ask Claude to order the candidates). Higher ceiling, but a second LLM
  round-trip of latency and token cost on every retrieval — disproportionate for reordering 20
  short chunks.
- **Widen `default-top-k` instead.** Feeds the model more chunks so the right one is *somewhere* in
  context, but spends tokens and dilutes the prompt with off-topic text — treats the symptom, not
  the ordering.

## Consequences

- Retrieval makes one extra provider call (rerank) when configured; fail-soft keeps that off the
  critical path.
- Three knobs, all config/env: `rag.rerank.enabled` (default on), `rag.rerank.candidate-pool-size`
  (how wide to recall before reranking), and `rag.rerank.model`. Turning it off restores exact
  ADR-0010 behaviour.
- The candidate pool is capped server-side (`RagService.MAX_CANDIDATE_POOL`) so
  `candidate-pool-size` can't make retrieval fetch an unbounded number of chunks.
- The reranked score replaces the cosine score on the returned `ScoredChunk`, so
  `search_knowledge`'s reported score now reflects **relevance**, not embedding distance — called
  out in the LLM/RAG/MCP how-to.

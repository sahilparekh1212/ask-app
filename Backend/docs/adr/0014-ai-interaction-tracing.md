# ADR-0014: AI interaction tracing for answer-quality measurement

**Status:** Accepted

## Context

Reranking (ADR-0012) and the eval gate (ADR-0013) measure retrieval **offline**, against a fixed
ground-truth set. To actually *improve* the assistant we also need to see the **real** interactions
it serves: what was asked, what retrieval returned and how the reranker reordered it, what the model
answered, and how long each stage took.

The existing audit events (ADR-0006 — the `Assistant/CHAT` and `Rag/SEARCH` rows) are deliberately
**content-free**: they record the model, latency and counts, never the query or the reply. That is
exactly right for the *audit trail* — it answers "what did users and agents do", stays non-PII, and
feeds the in-app dashboard — but it cannot answer "**why** was this answer weak, and which chunks
grounded it". Those are different questions that need the content the audit trail intentionally omits.

## Decision — a separate `ai_trace` table plus Micrometer metrics

Capture each CHAT and MCP-search interaction in a **purpose-built `ai_trace` table**: the query, the
**pre- and post-rerank candidates** (source, heading, score, rank), the model, the reply, per-stage
latencies, token usage, and the blocked flag — and emit **Micrometer meters** alongside for Grafana
time-series. This measures the **system** (retrieval and answer quality, latency), not the **user**:
no `userId` or email is stored, only the request-correlation id the rest of the stack already uses.

### Kept separate from the audit trail, on purpose

Not extra columns on the audit event — a distinct table, because the two differ on every axis:

| | Audit trail (`audit_logs`) | AI trace (`ai_trace`) |
|---|---|---|
| Purpose | activity / "what happened" | answer-quality debugging + the eval loop |
| Content | strictly non-PII (counts, categories) | query + reply + rankings |
| Consumer | the in-app dashboard | offline SQL analysis + Grafana |

Keeping them separate **preserves the audit trail's non-PII guarantee** — the reversal of the
"never log content" posture is scoped to this one table, which exists precisely to hold content.

### Why a table (not logs or metrics alone)

The goal is to *analyse and improve*: mine low-top-score queries, compare pre- vs post-rerank
ordering, join against the eval set. That is SQL over a table. Metrics lose per-interaction detail,
and logs are awkward to aggregate and join — so metrics are added **as well**, for dashboards and
alerting, but the table is the substrate for the improvement loop.

### Config, safety, and non-blocking writes

- `ai.trace.enabled` (default on) and `ai.trace.capture-content` (default on). The content flag gates
  only the **raw text** (query, reply); the retrieval scores/ranks/sources are public corpus metadata
  and are always captured, so quality analysis keeps working even with content capture off.
- Writes are **`@Async` fire-and-forget with errors swallowed** (the ADR-0006 philosophy): tracing is
  a side effect that must never add latency to — or fail — a chat or search. Content is length-capped.
- The corpus and data are synthetic + demo-login; the trace holds no third-party PII.

## Alternatives considered

- **Enrich the existing audit event with content.** Rejected: it poisons the audit trail's non-PII
  guarantee and pushes query/reply text onto the activity dashboard.
- **Metrics only.** Rejected: great for dashboards, but you can't open up a single weak answer and see
  what grounded it — the whole point of "look at the data to improve".
- **Logs only.** Rejected: searchable, but awkward to aggregate/join for the improvement loop, and
  content in logs is the same leak concern in a worse-structured place.
- **Synchronous write.** Rejected: puts a DB write on the chat-latency path, and a trace failure could
  break a reply — the opposite of a best-effort side effect.

## Consequences

- Content capture is **on** for this table (a flag disables it); the audit trail is unchanged.
- The table grows unbounded until a retention policy is added — a noted follow-up that mirrors the
  existing audit-log purge (`AuditLogPurgeService`).
- Pre/post-rerank capture required `RagService` to expose both retrieval stages (`retrieve()` returns
  the reranked results *and* the candidate pool), and token capture required the `LlmClient` seam to
  return usage alongside the reply.
- The offline eval (ADR-0013) and the online trace close the loop: the gate catches regressions before
  merge; the trace is the evidence for what to improve next.

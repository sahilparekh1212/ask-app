# How To — LLM chat, RAG & MCP

_Enable, access, and try the AI features (provider keys, indexing, chat, MCP clients) at every
level of the app — LOCAL, DEV, and PROD — plus the guardrails that make them deployable._

**Jump to:** [LOCAL / DEV](#local--dev) ·
[Try MCP](#try-mcp-any-level-no-auth--the-corpus-is-public-repo-docs) · [Try chat](#try-chat) ·
[Guardrails](#the-guardrails-what-makes-this-deployable)

All three features live inside the Audit service: a guarded **Claude chat proxy**
(`POST /api/v1/assistant/chat`), **RAG** over the repo's own docs + bundled source (pgvector +
Voyage embeddings), and an **MCP server** (`POST /mcp`) exposing that knowledge to any MCP
client. Two keys control everything — both optional, everything degrades gracefully:

| Key | Enables | Without it |
|---|---|---|
| `ANTHROPIC_API_KEY` | the chat assistant | chat returns 503; rest of the app unaffected |
| `VOYAGE_API_KEY` | RAG indexing + semantic search | tools report "not configured"; chat answers ungrounded |

## At each level

| Level | Chat | MCP endpoint |
|---|---|---|
| LOCAL (bare Gradle) | export keys before `:Audit:bootRun` | `http://localhost:8083/mcp` |
| DEV (compose stack) | export keys before `docker compose up` | `http://localhost:8083/mcp` |
| PROD | keys are repo secrets, shipped by the deploy | `https://ask-app.sahilparekh1212.com/audit-api/mcp` |

### LOCAL / DEV

Export the keys in the shell that starts Audit (or compose — the file passes them through):

```bash
export ANTHROPIC_API_KEY=sk-ant-...
export VOYAGE_API_KEY=pa-...
cd Backend && docker compose up -d --build     # or ./gradlew :Audit:bootRun
```

On startup a background thread indexes the corpus incrementally (content-hashed — unchanged
chunks cost zero embedding calls; watch for `RAG index ready: N chunks from M docs` in the
logs). Under LOCAL the vector store is in-memory; the compose stack uses **pgvector**
(`RAG_VECTOR_STORE=pgvector`).

### Try MCP (any level, no auth — the corpus is public repo docs)

```bash
claude mcp add --transport http ask-app https://ask-app.sahilparekh1212.com/audit-api/mcp
# or locally: claude mcp add --transport http ask-app http://localhost:8083/mcp
# then, inside Claude Code: "why is there no API gateway?" → grounded in ADR-0005
```

Tools: `search_knowledge` (top-k chunks by cosine similarity with source/heading/score) and
`list_sources` (index inventory). Raw JSON-RPC works too — `initialize`, `tools/list`,
`tools/call`.

### Try chat

Sign in on the UI → **Chat** → the pre-filled question ("How does the RAG pipeline behind this
chat work?") demonstrates the grounding. Or via API: demo-login for a token, then
`POST /api/v1/assistant/chat` with `{"message": "...", "history": []}`.

## The guardrails (what makes this deployable)

- **Server-side key only** — the browser never talks to a provider.
- **Prompt screening before any provider call**: JWT/token shapes, credential assignments,
  emails, card-like numbers → refused locally with a canned answer; only the *category* is
  logged, never the matched value. Replayed history is re-screened.
- **Role-scoped grounding in one allowlist class**: every user gets docs + aggregate stats;
  `ROLE_ADMIN` additionally the 20 newest raw rows
  ([ADR-0009](../../Backend/docs/adr/0009-llm-chat-assistant-data-flow.md)).
- **Corpus boundary**: public repo docs + synthetic reference data only — audit rows and user
  data are never indexed, which is why `/mcp` can be unauthenticated
  ([ADR-0010](../../Backend/docs/adr/0010-rag-mcp-server.md)).
- Tunables: `ASSISTANT_MODEL` (default `claude-opus-4-8`; `claude-haiku-4-5` for cost),
  `ASSISTANT_MAX_TOKENS`.

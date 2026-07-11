# ADR-0010: RAG MCP server — pgvector, Voyage embeddings, and a hand-rolled protocol endpoint

**Status:** Accepted

## Context

The chat assistant (ADR-0009) grounds its answers on one static context block
(`app-context.md`). The next step in the roadmap was retrieval-augmented generation over the
repo's *whole* knowledge — README, the ADRs, `docs/` — exposed two ways: to the existing
assistant, and to any MCP-capable client (Claude Code, Claude Desktop, IDEs) as a Model
Context Protocol server, so an external agent can ground its answers in this project's own
documentation:

```
claude mcp add --transport http ai-sandbox http://localhost:8083/mcp
```

Five decisions were scoped up front (TODO item); each is recorded here.

## Decision 1 — Vector store: pgvector on the existing Postgres, with an in-memory fallback

pgvector over a dedicated engine (Qdrant/Chroma): the compose stack already runs Postgres
with Liquibase-owned schema, so a vector store arrives as one image swap
(`postgres:16-alpine` → `pgvector/pgvector:pg16`, drop-in on the same volume) and one
`dbms: postgresql`-gated changeset (`CREATE EXTENSION vector` + `rag_chunk` with a
`vector(1024)` column) instead of a new service to deploy, monitor, and back up. At this
corpus size (a few hundred chunks) no engine's ANN performance is relevant; what matters is
operational surface, and pgvector's is zero-added.

The store is behind a `VectorStore` interface with two implementations selected by
`rag.vector-store`: `pgvector` (JdbcTemplate + the `<=>` cosine-distance operator — no JPA,
because the `vector` type has no sane JPA mapping) and `memory` (exact brute-force cosine).
The fallback exists because LOCAL/tests run H2 (ADR-0003), where a Postgres extension can't
exist: tests exercise real code paths against the in-memory store, and the pgvector SQL
contract is unit-tested against a mocked JdbcTemplate. The Docker/DEV+ stack — the thing
being showcased — runs the real vector database.

## Decision 2 — Embeddings: Voyage AI API with the server-side-key posture

Anthropic has no embeddings endpoint; Voyage is the provider Anthropic's docs recommend.
A provider API was chosen over a local ONNX model (all-MiniLM et al.) because a local model
adds ~100MB of runtime weights and a native inference dependency to a service whose jar is
otherwise lean — significant image/startup cost to avoid an env var. The key handling
mirrors ADR-0009 exactly: `VOYAGE_API_KEY` is server-side only, the client is built lazily,
and with no key the feature degrades (indexing skipped, MCP tool answers "not configured",
assistant falls back to its static context) rather than failing anything.

Model `voyage-3.5-lite` (config-tunable), `output_dimension` pinned to 1024 to match the
`vector(1024)` column. Documents and queries are embedded with their respective
`input_type` hints.

## Decision 3 — Chunking/indexing: heading-based chunks, content-hash incremental, at startup

- **Corpus at build time:** Gradle `processResources` copies the backend README + `docs/`
  into the jar (`rag-corpus/`), alongside the existing `app-context.md`. The image is
  self-contained — same jar, same corpus, anywhere — and CI needs no filesystem layout
  assumptions. "What re-indexes when docs change" is answered by the normal build: rebuild →
  restart → diff.
- **Chunking:** split on markdown headings (a chunk = one coherent topic, e.g. an ADR's
  Decision section), paragraph-packing only for oversized sections.
- **Incremental indexing at startup** (the `DemoDataSeeder` precedent): a chunk's id is a
  SHA-256 of source + content, so unchanged chunks cost zero embedding calls on restart, an
  edited chunk re-embeds under a new id, and a retain-only sweep deletes ids the corpus no
  longer produces. Indexing failures are logged, never fatal — a provider outage must not
  take down the audit API.
- Not chosen: CI-time indexing (would need the pipeline to reach both the embeddings
  provider and the runtime database — wrong coupling for this repo's GitHub-Actions-only CI).

## Decision 4 — Assistant seam: retrieval composes with the allowlist, never bypasses it

`AssistantContextBuilder` (ADR-0009's single data-access allowlist) stays the only assembly
point. The chat turn now retrieves top-k chunks for the user's question and appends them in
a `<retrieved_docs>` tag under the same data-not-instructions rule as every other tag. Two
properties make this safe by construction:

1. **The corpus contains zero audit data** — only the repo's public docs. Retrieval widens
   *grounding*, not *data access*; the ADMIN/USER gate on audit rows is untouched.
2. **Order of operations:** the prompt screener still runs first, so a message carrying a
   credential is answered locally and never reaches the embeddings provider either. Retrieval
   is best-effort — any failure degrades to the pre-RAG prompt instead of failing the chat.

## Decision 5 — Transport/auth: stateless Streamable HTTP, hand-rolled, unauthenticated

- **Transport:** MCP's Streamable HTTP (`POST /mcp`), not stdio. The server lives inside a
  long-running Spring service whose stdout is logs; HTTP is the transport that already fits
  the deployment (and the compose port map). Only the *stateless* subset is implemented —
  single request in, single JSON response out; GET answers 405 (no server-initiated SSE
  stream), per spec.
- **Hand-rolled JSON-RPC controller, not the MCP Java SDK:** the stateless subset is ~1
  controller (initialize with version negotiation, ping, tools/list, tools/call, notification
  acknowledgement) and is MockMvc-testable end to end. The SDK's value is session management
  and SSE streaming — machinery this server deliberately doesn't use. Revisit trigger:
  needing resource subscriptions, server-initiated streams, or sampling; at that point adopt
  the SDK rather than growing a protocol implementation.
- **Unauthenticated (`permitAll`):** the corpus is exclusively public repository
  documentation — the same files anyone can read on GitHub — so a JWT gate would protect
  nothing while making every MCP client integration carry a token. Revisit trigger: any
  non-public data entering the corpus (at which point the JWT resource-server machinery is
  already in place — the endpoint moves behind it and roles scope the tools, the same way
  the assistant's context is role-scoped today).

## Alternatives considered

- **Spring AI (VectorStore + MCP server starters).** Rejected for the same reason ADR-0009
  rejected it for the chat proxy: it's a large abstraction whose value is provider
  portability across many call sites; this feature has one embeddings call site, one store,
  and a protocol subset small enough to own. Owning it also means the wire format is
  fully understood — which is half the point of a portfolio feature.
- **Qdrant/Chroma.** A dedicated engine earns its ops cost at scales this corpus will never
  reach; see Decision 1.
- **Local ONNX embeddings.** Keyless demo appeal, but heavy runtime cost; see Decision 2.

## Consequences

- The compose demo needs `VOYAGE_API_KEY` exported for retrieval to index; without it the
  stack behaves exactly as before this feature (same graceful-degradation contract as
  `ANTHROPIC_API_KEY`).
- `rag.embedding-dimension` is pinned by the `vector(1024)` column; changing models is free
  only while the dimension stays 1024 (Voyage models accept `output_dimension=1024`).
- The MCP endpoint is stateless; a client that requires session semantics or SSE resumption
  won't get them — acceptable for the current clients (Claude Code's HTTP transport handles
  plain JSON responses).

## Addendum — indexing the backend source itself (repo-as-corpus)

The corpus was later extended from documentation to include **the backend source itself**
(Auth + Audit + common Java, their resources, and the Gradle build files), so the assistant can
answer questions about the actual deployed code — not only its docs. Chunking dispatches on file
type: markdown by heading (`MarkdownChunker`), source by size on line boundaries with a
line-range label (`CodeChunker`).

The requirement was "let the prod chat read the repo's `main` branch." Two shapes were weighed:

- **Runtime clone on the VM** (`git clone main` at startup, re-index on a schedule). Tracks the
  latest `main` independent of the deploy, but adds a runtime git + network dependency and drops
  the self-contained-image property this ADR (Decision 3) deliberately chose — it would need its
  own refresh/failure story.
- **Build-time bundling (chosen).** The corpus is copied into the jar at build time, exactly as
  the docs already were. Because the image is built from `main` (merge → CD → deploy), the indexed
  source is *by construction* the deployed code — no drift, no extra moving parts, no network or
  auth (the repo is public, so read-only is inherent). This keeps the "same jar indexes the same
  corpus anywhere" property intact.

Scope limit worth recording: only files inside the Audit image's Docker build context (`Backend/`,
specifically what `Audit/Dockerfile` COPYs into the build stage) can be bundled. Backend source and
Gradle files qualify; the Angular `UI/` and the compose/CI YAML that live *above* `Backend/` do not
— they would only ever populate a host/CI build, not the shipped container (the same trap that made
bundling the repo-root README a host-only artifact). The `docs/code-map.md` and `docs/ui-guide.md`
descriptions cover those instead. If the UI source is wanted in the index later, the deliberate move
is to widen the Docker build context, not to sneak a host-only `from` into `processResources`.

### Addendum 2 — extending the corpus to the UI and observability stack

The corpus was then widened again to cover the **Angular UI source** and the **observability +
deployment configs** (Prometheus/Loki/Tempo/Grafana, the compose stack, the OpenShift manifests),
so the assistant can read the whole system, not just the backend.

The observability/deploy configs live under `Backend/` (already in the build context), so they were
just added to the `processResources` copy and `Audit/Dockerfile`. The UI was the interesting case:
`UI/` is a *sibling* of `Backend/`, outside the Audit image's build context — the exact limitation
the first addendum recorded. Rather than widen the context to the repo root (which would have
re-pathed every `COPY` and needed a repo-root `.dockerignore`), the UI is supplied as a **named
build context** (`--build-context ui=../UI`, `additional_contexts` in compose) and copied to an
absolute `/UI` — a sibling of the Gradle root `/workspace` — so `processResources`'
`"${rootProject}/../UI"` resolves to it in-container exactly as it resolves to `<repo>/UI` on a host
build. That keeps host and container corpora identical (no host-only artifact) with a one-line
`COPY --from=ui`. Every place the Audit image is built now passes that context: `docker-compose.yml`
(`additional_contexts`), `cd.yml` (`build-contexts`), and the Trivy build in `backend-ci.yml`
(`--build-context`). `package-lock.json` and `node_modules` are excluded (no explanatory value /
never in the context); empty files (e.g. an empty Angular `*.scss`) are skipped by `CorpusLoader`.

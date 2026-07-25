# ask-app

ask-app is a full-stack application you can interrogate about itself: an **LLM chat assistant**
grounded by **RAG** over the repo's own docs and source code, the same knowledge exposed to any
MCP client through a public **MCP server**, and **observability** on both axes — an in-app audit
dashboard for what users and agents *did* (event-sourced over Kafka), and
Grafana/Prometheus/Loki/Tempo for how the system is *performing*.

**Live app:** <a href="https://ask-app.sahilparekh1212.com" target="_blank" rel="noopener noreferrer">ask-app.sahilparekh1212.com</a> — demo login `demo` / `demo`.

What's in it:

- Angular 21 SPA served by nginx;
- Spring Boot Auth and Audit services;
- Google OAuth2, RSA-signed JWTs, JWKS, role-based access control;
- asynchronous audit events through Apache Kafka;
- PostgreSQL + pgvector for audit data and repository RAG;
- a batch-loaded security-master reference dataset with a scheduled daily batch;
- a server-side Claude assistant and a public MCP knowledge-search endpoint;
- Prometheus, Loki, Tempo, Grafana, GA4, and Sentry;
- Docker/OpenShift deployment plus CI/CD and supply-chain checks.

## 📖 Architecture documentation

| Page | What it covers |
|---|---|
| [🗺️ System diagram](docs/system-diagram.md) | End-to-end runtime topology (Mermaid) |
| [🎨 Frontend (UI)](docs/frontend.md) | Angular 21 SPA, signals-first state, same-origin auth |
| [🔄 Core runtime flows](docs/runtime-flows.md) | Auth/authz, event-driven audit trail, assistant + RAG + MCP |
| [🔌 API endpoints & access](docs/api-endpoints.md) | Every endpoint on both services and who can reach it (directly/indirectly) |
| [🧩 Component map](docs/component-map.md) | Each area's responsibility and where it lives |
| [⚖️ State, scaling & resilience](docs/state-scaling-resilience.md) | State models, replication, the newest-wins limiter |
| [🗄️ Data, schema & profiles](docs/data-and-profiles.md) | Liquibase schema, indexes, views, retention, H2/Postgres by profile |
| [📊 Observability](docs/observability.md) | Prometheus, Loki, Tempo, Grafana, GA4, Sentry |
| [🚦 CI/CD](docs/ci-cd.md) | Quality gates on every PR and delivery on merge |
| [⚙️ Backend guide](Backend/README.md) | Services, concepts, rate limiting, deployment |
| [🖼️ Frontend guide](UI/README.md) | Routes, design decisions, dev workflow |
| [🧠 Architecture decisions](Backend/docs/adr/README.md) | The non-obvious "why" behind each choice |
| [🚀 Deployment plan](Backend/docs/deployment.md) | Commit → registry → environments → rollout |

## 🚀 Run it — onboarding by level

| Level | Guide | What you get |
|---|---|---|
| 🧑‍💻 [LOCAL](docs/onboarding-local.md) | Bare metal — JDK + Node, no Docker | Both services on H2 + the UI dev server |
| 🐳 [DEV](docs/onboarding-dev.md) | `docker compose up --build` | The full 12-container stack: Postgres, Kafka, observability |
| ☁️ [PROD](docs/onboarding-prod.md) | Merge → CD → deploy (GCE VM) | The live deployment and how to operate it |

## 🛠️ How-to, by technology

| Guide | Covers (setup + access, LOCAL → PROD) |
|---|---|
| [Grafana](docs/how-to/grafana.md) | Dashboards & Explore; anonymous read-only posture; admin access |
| [Kafka](docs/how-to/kafka.md) | Broker, Kafka UI, CLI, topics & DLT |
| [Postgres](docs/how-to/postgres.md) | pgvector, Adminer, psql, backups; H2 at LOCAL |
| [Redis](docs/how-to/redis.md) | Refresh-token store, Redis Insight, the 2-replica proof |
| [Prometheus, Loki & Tempo](docs/how-to/prometheus-loki-tempo.md) | Reaching the telemetry backends directly at each level |
| [LLM chat, RAG & MCP](docs/how-to/llm-rag-mcp.md) | Provider keys, indexing, trying MCP, the guardrails |

## 🗺️ Roadmap

Planned enhancements, all under one theme — **make the assistant's answers measurably good, and
keep them that way** — plus a hands-free way to use them.

| Status | Item | What it adds |
|---|---|---|
| ✅ Done | **Query reranking** | A second-stage reranker (Voyage `rerank-2.5-lite`) over a wider candidate set, so the top-K chunks the assistant and MCP tool are grounded on are the *most* relevant, not just the nearest by cosine ([ADR-0012](Backend/docs/adr/0012-query-reranking.md)). |
| ✅ Done | **RAG evaluation & quality gate** | A ground-truth set of 100 questions with retrieval metrics (recall@k, MRR, nDCG@k, hit-rate). Thresholds live in config, and a CI job fails the build when retrieval quality drops below standard — the same "gate on every PR" posture already applied to coverage and CVEs ([ADR-0013](Backend/docs/adr/0013-rag-evaluation-and-quality-gate.md), [rag-eval.md](Backend/docs/rag-eval.md)). |
| ✅ Done | **AI answer-quality observability** | Each retrieval + answer — the query, the candidate rankings before/after rerank, model input/output, latencies and token counts — is recorded to an `ai_trace` table (plus Grafana metrics) so retrieval and answer accuracy can be measured and improved over time. Quality signals for the system, not user profiling ([ADR-0014](Backend/docs/adr/0014-ai-interaction-tracing.md)). |
| 🧭 Planned | **Voice chat** | Ask by voice and hear the answer back: speech-to-text on the question, the existing grounded chat assistant in the middle, and text-to-speech on the reply — hands-free Q&A over the same pipeline. |

## 📄 License

**Proprietary — all rights reserved.** Published for viewing/portfolio evaluation only;
see <a href="https://github.com/sahilparekh1212/ask-app/blob/main/LICENSE" target="_blank" rel="noopener noreferrer">LICENSE</a>.

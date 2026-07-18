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

## 📄 License

**Proprietary — all rights reserved.** Published for viewing/portfolio evaluation only;
see <a href="https://github.com/sahilparekh1212/ask-app/blob/main/LICENSE" target="_blank" rel="noopener noreferrer">LICENSE</a>.

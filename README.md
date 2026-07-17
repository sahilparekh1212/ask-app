# ask-app

👋 **Welcome!** Thanks for stopping by. This is a production-shaped full-stack
portfolio project — a live, deployed system rather than a demo. This README gives
you the overview; the [documentation](#-documentation) links dive into how the
pieces connect and the CI/CD that ships it. Feel free to poke around the <a href="https://ask-app.sahilparekh1212.com" target="_blank" rel="noopener noreferrer">live app</a> (demo login `demo` / `demo`)
while you read.

Source: <a href="https://github.com/sahilparekh1212/ask-app" target="_blank" rel="noopener noreferrer">github.com/sahilparekh1212/ask-app</a>

## 📦 What this project is

ask-app is a production-shaped full-stack portfolio application:

- Angular 21 single-page application served by nginx;
- Spring Boot Auth and Audit services;
- Google OAuth2, RSA-signed JWTs, JWKS, and role-based access control;
- asynchronous audit events through Apache Kafka;
- PostgreSQL and pgvector for audit data and repository RAG;
- a server-side Claude assistant and public MCP knowledge-search endpoint;
- Prometheus, Loki, Tempo, Grafana, Google Analytics 4, and Sentry; and
- Docker/OpenShift deployment definitions plus CI/CD and supply-chain checks.

The live app is available at
<a href="https://ask-app.sahilparekh1212.com" target="_blank" rel="noopener noreferrer">ask-app.sahilparekh1212.com</a>. The demo
account is `demo` / `demo`.

## 📖 Documentation

The detailed, code-verified architecture is split into focused pages under
[`docs/`](docs/). Each is intentionally focused on how the deployed system is
connected and why its boundaries exist.

| Page | What it covers |
|---|---|
| [🗺️ System diagram](docs/system-diagram.md) | End-to-end runtime topology (Mermaid) |
| [🎨 Frontend (UI)](docs/frontend.md) | Angular 21 SPA, signals-first state, same-origin auth |
| [🔄 Core runtime flows](docs/runtime-flows.md) | Auth/authz, event-driven audit trail, assistant + RAG + MCP |
| [🧩 Component map](docs/component-map.md) | Each area's responsibility and where it lives |
| [⚖️ State, scaling & resilience](docs/state-scaling-resilience.md) | State models, replication, the newest-wins limiter |
| [🗄️ Data, schema & profiles](docs/data-and-profiles.md) | Liquibase schema, H2/Postgres by Spring profile |
| [📊 Observability](docs/observability.md) | Prometheus, Loki, Tempo, Grafana, GA4, Sentry |
| [🚦 CI/CD](docs/ci-cd.md) | Quality gates on every PR and delivery on merge |
| [🖥️ Run locally](docs/running-locally.md) | One `docker compose up` to bring the stack up |

## 📚 Further reading

- <a href="https://github.com/sahilparekh1212/ask-app/blob/main/UI/README.md" target="_blank" rel="noopener noreferrer">Frontend guide</a>
- <a href="https://github.com/sahilparekh1212/ask-app/blob/main/Backend/README.md" target="_blank" rel="noopener noreferrer">Backend guide</a>
- <a href="https://github.com/sahilparekh1212/ask-app/blob/main/Backend/docs/adr/README.md" target="_blank" rel="noopener noreferrer">Architecture decisions</a>
- <a href="https://github.com/sahilparekh1212/ask-app/blob/main/Backend/docs/deployment.md" target="_blank" rel="noopener noreferrer">Deployment guide</a>

## 📄 License

**Proprietary — all rights reserved.** This repository is published for
viewing/portfolio evaluation only; no right to use, run, copy, modify, or
distribute the code is granted. See
<a href="https://github.com/sahilparekh1212/ask-app/blob/main/LICENSE" target="_blank" rel="noopener noreferrer">LICENSE</a>.

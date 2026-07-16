# 🖥️ Run locally

_Part of the [ask-app](../README.md) documentation._

```bash
cd Backend
docker compose up --build
```

This starts the SPA at `http://localhost:4200`, Auth on port `8085`, Audit on
port `8083`, PostgreSQL, Redis, Redpanda, and the observability stack. Chat and
RAG are optional: export `ANTHROPIC_API_KEY` and `VOYAGE_API_KEY` to enable
them; the rest of the system remains usable without either key.

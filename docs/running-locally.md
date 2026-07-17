# 🖥️ Run locally

_Part of the [ask-app](../README.md) documentation._

This page has grown into per-level onboarding guides — start there:

- **[Onboarding — LOCAL](onboarding-local.md)**: bare metal, JDK + Node only (H2, no Docker).
- **[Onboarding — DEV](onboarding-dev.md)**: the full Docker Compose stack in one command.
- **[Onboarding — PROD](onboarding-prod.md)**: the GCE deployment and how to operate it.

The short version:

```bash
cd Backend
docker compose up --build     # → http://localhost:4200, demo login demo / demo
```

Chat and RAG are optional: export `ANTHROPIC_API_KEY` and `VOYAGE_API_KEY` to enable them —
see [How To — LLM, RAG & MCP](how-to/llm-rag-mcp.md).

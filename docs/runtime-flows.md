# 🔄 Core runtime flows

_Part of the [ask-app](../README.md) documentation._

## 🔐 Authentication and authorization

The SPA calls Auth through nginx's same-origin `/auth-api` proxy. Auth supports
the demo login and Google OAuth2, issues RSA-signed access JWTs, and exposes a
JWKS endpoint. Audit retrieves the public JWKS to verify tokens locally; it
never receives Auth's private signing key and does not call Auth for each API
request.

Refresh tokens are single-use and stored with a TTL in Redis. The browser keeps
the access and refresh tokens locally; its HTTP interceptor attaches the bearer
token and performs one refresh-and-retry after a `401`.

## 📝 Event-driven audit trail

Auth publishes login, refresh, and logout `AuditEvent`s asynchronously to the
`audit.events` Kafka topic. Audit also publishes its own chat, RAG, and MCP
activity to that same topic. The Audit consumer processes messages
transactionally, deduplicates at-least-once delivery by `eventId`, and persists
audit rows in PostgreSQL. A slow broker does not block the original feature or
authentication request; missed events during an outage are an accepted
fire-and-forget trade-off.

## 🤖 Assistant, RAG, and MCP

The UI sends chat requests only to Audit, keeping provider credentials outside
the browser. Audit screens prompts for credentials and PII before any external
call. It embeds a question with Voyage, searches pgvector for repository
chunks, builds a role-scoped prompt, and submits only readable retrieved text to
Claude. Vectors never reach Claude.

At startup, Audit incrementally indexes bundled documentation and source code:
new or changed chunks are embedded with Voyage and stored in pgvector. The same
RAG service powers `/mcp`; it exposes repository knowledge only and does not
expose audit rows.

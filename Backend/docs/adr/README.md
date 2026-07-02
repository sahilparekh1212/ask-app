# Architecture Decision Records

Short records of the non-obvious "why" behind decisions in this codebase — what was chosen,
what else was considered, and the tradeoff being accepted. The README explains *what* the
system does; these explain *why* it's built this way.

| ADR | Decision |
|-----|----------|
| [0001](0001-rsa-jwt-signing.md) | RSA (asymmetric) JWT signing over HMAC |
| [0002](0002-in-memory-rate-limit-and-refresh-store.md) | In-memory store for rate limiting & refresh tokens over Redis |
| [0003](0003-h2-over-testcontainers.md) | H2 for tests over Testcontainers |
| [0004](0004-liquibase-schema-ownership.md) | Liquibase owns the schema instead of Hibernate `ddl-auto` |
| [0005](0005-no-api-gateway-yet.md) | No API gateway / BFF yet — the SPA calls each service directly |
| [0006](0006-fire-and-forget-audit-events.md) | Fire-and-forget audit events — at-most-once on broker outage, no outbox |

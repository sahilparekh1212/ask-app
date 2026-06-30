# TODO — Interview-readiness punch list (Fullstack Engineer)

Findings
## High impact
- [ ] **`UI/` frontend is an empty stub.** No package.json, no source files — "fullstack" is currently unsubstantiated. Either build a minimal real frontend (e.g. React/Next hitting Auth via OAuth2+JWT, and Audit/Notification) or stop billing the project as fullstack.
- [ ] **Business logic is generic CRUD with no real complexity.** Audit logs and notifications are basic create/read/soft-delete. Add real complexity to one module — e.g. notification delivery with retries/backoff, or audit log querying/aggregation with pagination and filters backed by indexes.
- [ ] **Audit module's "immutability" claim isn't real.** `AuditLog.java` still exposes public setters (`setEntityType`, `setDetails`, `setDeleted`); immutability is only enforced by the absence of a PUT/PATCH route, not by entity design. Lock this down at the entity level (e.g. make fields final / remove setters, use a builder).
- [ ] **Notification module doesn't notify anyone.** It persists a `channel` field but never dispatches anything through it (no email/SMS/push integration or adapter interface). Either implement real dispatch or rename/reframe the module honestly.
- [ ] **Git history reads as AI-bulk-generated, not iterative.** Only ~9 real commits: a couple of giant "dump everything" commits followed by cleanup PRs deleting Account/Transaction/Report/Survey modules, several co-authored by Claude. Going forward, commit in smaller, narratively coherent units that survive "walk me through how you built this."

## Medium impact

- [ ] **No ADRs or design-tradeoff docs.** README explains "what" well but never "why" — e.g. why `ConcurrentHashMap` over Redis for rate limiting/refresh tokens, why RSA over HMAC for JWT signing, why H2 over Testcontainers for tests. Add a short `docs/adr/` with 3–4 real decisions and alternatives considered.
- [ ] **Auth refresh-token store is in-memory only.** `TokenService.java` explicitly comments it as a placeholder — tokens vanish on restart and don't work across replicas. Same issue applies to the ephemeral RSA keypair generated when no `AUTH_RSA_PRIVATE_KEY` env var is supplied. Replace with Redis/DB-backed storage, or at minimum document this as a known limitation.
- [ ] **Auth refresh flow drops claims on rotation.** `AuthController.refresh()` calls `generateTokens(userId, null, null)`, losing email/name on refresh — minor logic bug worth fixing alongside the token tests.
- [ ] **Observability is configured but unproven end-to-end.** Prometheus/Grafana/Loki wiring is real (working PromQL/LogQL panels, not a template) but there's no evidence it was run against real traffic. Capture a dashboard screenshot from an actual load test and include it in the README.
- [ ] **OpenShift manifests use `emptyDir`, no PVCs.** README already flags this, but if the project is framed as "production-ready" elsewhere this undercuts credibility — either add a PVC variant or state the limitation explicitly.
- [ ] **Minor security smells in Auth:** no `@Valid`/`@NotBlank` on `RefreshRequest`; no CORS policy configured anywhere; `MdcLoggingFilter.extractSubFromJwt()` does a naive unverified base64 substring search for logging purposes (not an auth bypass, but fragile/non-idiomatic — should use a JSON parser at minimum).

## Low impact

- [ ] **Duplicated `ratelimit/` package across all 3 modules.** Byte-for-byte identical code in Audit/Auth/Notification with no shared `:common` Gradle module — extract it. Also won't scale past ~6–8 services without a shared platform/BOM module; worth being able to articulate this in interviews even if not built.
- [ ] **Rate limiter's "high concurrency" claim is unsubstantiated.** It's genuinely sophisticated engineering (atomic `compute`, thread-interrupt cancellation, transactional rollback on superseded requests) but unproven. Add a k6/JMeter script with results in the README to back up the claim.

# End-to-end deployment plan

How AI-Sandbox goes from a commit to a running system. This is the design doc for deploying the
whole application — the two Spring Boot services, the Angular SPA, and their backing
infrastructure — end to end. It ties together artifacts that already exist in the repo
(Dockerfiles, `docker-compose.yml`, `openshift/`, GitHub Actions) and names the gaps that the
CI/CD roadmap in [TODO.md](../TODO.md) closes.

Status legend: **[built]** exists in the repo today · **[planned]** on the roadmap · **[env]**
an environment-specific value the operator supplies.

---

## 1. What we deploy

| Component | Artifact | Port | Notes |
|-----------|----------|------|-------|
| Auth service | `Auth/Dockerfile` (multi-stage → JRE) | 8085 | Stateless; ≥2 replicas; needs Redis + a shared RSA key |
| Audit service | `Audit/Dockerfile` | 8083 | Stateless; ≥2 replicas; owns the audit schema via Liquibase; hosts the LLM assistant proxy |
| SPA | `UI/Dockerfile` (node build → `nginx:1.28-alpine`) | 80/4200 | Serves static bundles + reverse-proxies `/auth-api`, `/audit-api` |
| PostgreSQL | managed or `postgres` image | 5432 | Audit's database of record |
| Kafka (Redpanda) | `redpanda` | 9092 | `audit.events` topic + `audit.events.DLT` |
| Redis | `openshift/redis/` | 6379 | Auth's shared refresh-token store (statelessness) |
| Observability | Prometheus, Grafana, Loki, Tempo | — | Metrics, logs, traces; all tagged by pod |

The two services depend only on Postgres, Kafka, and Redis; the SPA depends only on the two
services (same-origin, via the nginx reverse proxy — no CORS on that path).

---

## 2. Environments

| Env | Purpose | Profile (`SPRING_PROFILES_ACTIVE`) | Data |
|-----|---------|-----------------------------------|------|
| LOCAL | dev laptop | `LOCAL` | H2 in-memory, demo seed |
| DEV | shared integration | `DEV` | Postgres, demo seed on |
| SIT/UAT | pre-prod verification | `SIT` / `UAT` | Postgres, no demo seed, real IdP |
| PROD | production | `PROD` | Postgres (HA), no seed |

The profile selects `application-<PROFILE>.properties` (datasource, JWKS URI, seed toggle). The
same image runs in every environment — only config and secrets differ (12-factor). One
`docker compose up --build` from `Backend/` brings the whole stack up locally; the sections
below are about promoting that beyond a laptop.

---

## 3. Image registry — build once, promote by digest  [built: GHCR CD]

CI builds the boot jars and Docker images to **verify** them (and Trivy-scans them), and CD
publishes them:

1. **[built]** On merge to `main`, the `CD` workflow (`.github/workflows/cd.yml`) builds each
   image (audit, auth, ui) once and pushes it to **GitHub Container Registry** with three tags:
   a generated SemVer (`0.1.<run>` — monotonic until real release tags exist), the git SHA
   (`ghcr.io/<owner>/ai-sandbox/audit:sha-<short>`), and `latest`.
2. **[env]** Deployments should reference images **by digest** (`@sha256:…`) or at least the
   `sha-` tag, not `:latest`, so a rollout is reproducible and a rollback is "point at the
   previous digest".
3. **[built]** `docker-compose.ghcr.yml` is the pull-instead-of-build override — reviewers run
   the exact CI-built artifacts:
   `docker compose -f docker-compose.yml -f docker-compose.ghcr.yml up -d --no-build`
   (pin a build with `AI_SANDBOX_TAG=sha-<short>`).
4. Supply chain **[built]**: `cd.yml` signs every pushed digest with **cosign** (keyless — the
   signature binds the image to the workflow's OIDC identity, recorded in the Rekor transparency
   log; no key to store or rotate) and attaches a **syft** SPDX SBOM both as a workflow artifact
   and as a signed cosign attestation on the image. Verify before deploying:
   ```
   cosign verify \
     --certificate-identity-regexp 'https://github.com/sahilparekh1212/AI-Sandbox/\.github/workflows/cd\.yml@.*' \
     --certificate-oidc-issuer https://token.actions.githubusercontent.com \
     ghcr.io/sahilparekh1212/ai-sandbox/<name>@<digest>
   ```
   Wiring that verification into an actual deploy step stays **[planned]** with the deploy half
   of CD.

---

## 4. Configuration & secrets

**Config** is non-secret and lives in ConfigMaps / compose `environment:` — profile, service
URLs (`AUTH_JWK_SET_URI`, `KAFKA_BOOTSTRAP_SERVERS`, `REDIS_HOST`), `CORS_ALLOWED_ORIGINS`,
`FRONTEND_URL`, observability endpoints (`LOKI_URL`, `OTEL_EXPORTER_OTLP_ENDPOINT`), and the
assistant knobs (`ASSISTANT_MODEL`, `ASSISTANT_MAX_TOKENS`).

**Secrets are never committed** — the repo ships only `*.example.yaml` templates; real
`secret.yaml` files are gitignored (see [Backend/README.md](../README.md) → Secrets). Per
environment, provide:

| Secret | Consumer | Why it matters |
|--------|----------|----------------|
| `AUTH_RSA_PRIVATE_KEY` | Auth | Every replica must sign JWTs with the **same** key, or tokens fail cross-pod validation |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Auth | Google OAuth2; the exact redirect URI must be registered in the Google console |
| DB username/password | Audit | Datasource credentials |
| Redis password **[env]** | Auth | If the managed Redis requires auth |
| `ANTHROPIC_API_KEY` | Audit | Enables the LLM assistant + flashcards; **server-side only**, never reaches the browser. Absent ⇒ those endpoints 503, rest of the app unaffected |
| Grafana admin password | Grafana | Dashboard access; reset to the secret on every deploy (`deploy.yml`) since the env var only applies on first DB init |
| `SENTRY_AUTH_TOKEN` | Grafana | Lets the provisioned Sentry datasource read UI error data (org/project/event read scopes only) |

In production, source these from the platform's secret manager (OpenShift Secrets backed by
Vault / Sealed Secrets / External Secrets Operator) rather than hand-applied files.

---

## 5. Stateful backing services

- **PostgreSQL** — a managed instance (RDS/Cloud SQL/Crunchy) in DEV+; point
  `SPRING_DATASOURCE_URL` at it. **Liquibase owns the schema** and runs on Audit startup
  (`ddl-auto=none`), so a fresh database is migrated automatically; PROD runs the same changelog.
  Single points of failure to remove for real prod: run Postgres HA (primary + replica).
- **Kafka** — the dev container is a single-broker Redpanda; production wants a multi-broker
  cluster (managed MSK/Redpanda Cloud/Strimzi on the cluster) with replication ≥3 for
  `audit.events` and its DLT.
- **Redis** — `openshift/redis/` is single-replica; production wants Sentinel or a managed HA
  Redis. Auth degrades safely if Redis is down (refresh fails, login still works), but a
  scaled Auth **requires** a shared Redis (see [ADR-0007](adr/0007-redis-refresh-token-store-for-statelessness.md)).

These three are the remaining "no SPOF" items tracked under the statelessness task in the TODO.

---

## 6. Orchestration — OpenShift manifests  [built]

`openshift/<service>/` holds Deployment, Service, Route, ConfigMap, and an HPA per service (plus
a Secret template for Auth); `openshift/redis/` and `openshift/monitoring/<component>/` cover the
rest. Apply order (also in [README → Deploying to OpenShift](../README.md)):

```bash
oc apply -f openshift/namespace.yaml
oc apply -f openshift/auth/secret.yaml        # from the gitignored, filled-in template
oc apply -f openshift/redis/                  # shared refresh-token store — before Auth scales
oc apply -f openshift/auth/  openshift/audit/ # both default to 2 replicas
oc apply -f openshift/monitoring/prometheus/ openshift/monitoring/loki/ openshift/monitoring/grafana/
```

Each service scales independently via its HPA; monitoring components get RWO PVCs and
`strategy: Recreate` (a RWO volume can't be mounted by old+new pods during a rolling update).
The SPA is served either as an nginx Deployment+Route, or as static assets on a CDN with the
`/auth-api` and `/audit-api` proxy rules moved to the ingress.

> Note: an `openshift/notification/` directory is left over from a removed module and is not
> part of the deploy — apply only `auth`, `audit`, `redis`, and `monitoring`.

---

## 7. CI → CD promotion flow

```
 PR ──► CI (backend-ci: build+test+90% coverage, k6, CodeQL, Trivy jars+images;
 │        Frontend CI: lint+build+headless tests; commit-lint; pre-commit hygiene)
 │        required checks gate the merge (branch protection, PR-only on main)
 ▼
 main ─► CD [publish built]: build+tag images (SemVer+SHA) ─► push GHCR ─► deploy DEV by digest
 │        └─ smoke check (health endpoints, one demo login) ─► promote to SIT/UAT   [planned]
 ▼
 tag vX.Y.Z ─► deploy PROD by the tag's digest (manual approval gate)               [planned]
```

- **CI [built]** — every PR runs the full suite; four+ required contexts must pass, and `main`
  is PR-only (no direct pushes, admins included).
- **CD publish [built]** — merge to `main` publishes versioned images to GHCR (`cd.yml`); the
  pull variant `docker-compose.ghcr.yml` runs them anywhere.
- **CD deploy [planned]** — rolling DEV automatically and promoting SIT/UAT/PROD by digest
  (manual approval for PROD) needs a live environment to deploy to. No rebuild between
  environments — the artifact that passed CI is the artifact that ships.

---

## 8. Rollout & rollback

- **Rollout** — services use rolling updates (stateless, ≥2 replicas) behind readiness probes on
  `/actuator/health`; traffic shifts only as new pods report ready. DB changes are Liquibase
  changesets applied on startup — keep them **backward-compatible** (expand/contract) so an old
  replica and a new replica can run against the same schema during the rollout.
- **Rollback** — `oc rollout undo deployment/<svc>` (or re-apply the previous image digest).
  Because images are immutable and pinned by digest, rollback is deterministic. A
  non-backward-compatible migration is the one thing rollback can't undo cleanly — hence the
  expand/contract discipline above.
- **Health & readiness** — liveness/readiness both hit `/actuator/health`; the SPA's nginx has a
  static fallback so it stays up even if an API is briefly unavailable.

---

## 9. Networking, DNS & TLS

- **Ingress** — one hostname (e.g. `ai-sandbox.example.com`) routes to the SPA's nginx, which
  reverse-proxies `/auth-api/*` → Auth and `/audit-api/*` → Audit on the cluster network, so the
  browser is same-origin and needs no CORS. Alternatively an OpenShift Route/Ingress per service.
- **TLS** — terminate at the edge (Route/Ingress with cert-manager or the platform's ACME
  integration); redirect HTTP→HTTPS. Set `FRONTEND_URL`/`CORS_ALLOWED_ORIGINS` to the HTTPS
  origin, and register the HTTPS OAuth redirect URI with Google.
- **Forwarded headers** — Auth runs `SERVER_FORWARD_HEADERS_STRATEGY=framework` so the OAuth
  redirect URI is derived from the external host/proto behind the proxy (the war story is in the
  UI containerization notes).

---

## 10. Observability in production

Prometheus scrapes `/actuator/prometheus` on both services; Grafana reads Prometheus (metrics,
incl. p95/p99 latency histograms) and Loki (structured logs with `requestId`/`userId`
correlation); Tempo receives OTLP traces that follow a request across the Kafka hop. Every log
line, metric, and trace is tagged with the emitting pod, so a signal can be traced to an
instance once >1 replica runs. First production task on this front: capture a dashboard from a
real load run (the open observability item in the TODO).

**Access on the live deployment [built]:** Grafana is published **read-only** at
https://ai-sandbox.sahilparekh1212.com/grafana — anonymous visitors get the Viewer role
(dashboards + Explore, no edits; sign-up disabled), routed by Caddy's `/grafana` handle with
Grafana serving the sub-path itself (`GF_SERVER_SERVE_FROM_SUB_PATH`). The admin password is
the `GRAFANA_ADMIN_PASSWORD` repo secret, shipped to the VM's `.env` by `deploy.yml` (no more
`admin`/`admin`). Prometheus (:9090), Loki (:3100) and Tempo (:3200) publish **no** host ports
in prod (`ports: !reset []`); to reach one directly, SSH into the VM
(`gcloud compute ssh ai-sandbox-vm --zone=us-east1-b`) and curl the container over the compose
network, or temporarily tunnel via `docker exec`. For everyday inspection, the published
Grafana's Explore covers all three datasources.

---

## 11. First-deploy checklist

1. Provision Postgres, Kafka, Redis (managed/HA in prod).
2. Create the namespace and all Secrets from the filled-in templates / secret manager.
3. Publish images to GHCR (any merge to `main` does via `cd.yml`) and pin deployments to digests.
4. Apply `redis/` → `auth/` → `audit/` → `monitoring/`.
5. Verify: `/actuator/health` green on both services; a demo login issues a JWT; an audit search
   returns rows; (if `ANTHROPIC_API_KEY` set) the assistant answers.
6. Point DNS at the ingress, confirm TLS, register the OAuth redirect URI.
7. Capture a Grafana dashboard under load.

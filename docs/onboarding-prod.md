# 🚀 Onboarding — PROD (GCE VM, deployed from GitHub)

_How production ships and how to operate it — one GCE VM running the compose stack behind Caddy
TLS at **https://ask-app.sahilparekh1212.com**, deployed keylessly from GitHub Actions (full
design: the [deployment plan](../Backend/docs/deployment.md))._

**Contents:** [How a change reaches production](#how-a-change-reaches-production) ·
[One-time setup](#one-time-setup-already-done-for-this-deployment) ·
[What runs on the VM](#what-runs-on-the-vm) · [Verify a deployment](#verify-a-deployment) ·
[Operate the VM](#operate-the-vm) · [Rollback](#rollback) ·
[Verify provenance](#verify-provenance-anyone-can)

## How a change reaches production

```
merge to main → CD (cd.yml): build images once, push to GHCR
  (tags 0.1.<run> / sha-<short> / latest), cosign keyless signing + syft SBOM attestation
→ Deploy (deploy.yml): WIF keyless auth to GCP → ship compose bundle + .env + signing key
  → docker compose pull && up -d --no-build → smoke checks through the public origin
```

No long-lived cloud keys anywhere: GitHub's OIDC token is exchanged via **Workload Identity
Federation** (provider pinned to this repo), and image signatures carry the workflow's identity.
Gated by the `DEPLOY_ENABLED` repo variable; `workflow_dispatch` allows a manual run.

## One-time setup (already done for this deployment)

| Piece | What |
|---|---|
| GCP | Project + static IP + firewall (80/443 only) + `ask-app-vm` (e2-standard-2, Debian, Docker) |
| WIF | Pool/provider pinned to `sahilparekh1212/ask-app` + deploy SA with `workloadIdentityUser` |
| DNS/TLS | A record → static IP; Caddy auto-issues Let's Encrypt for `$DOMAIN` on first boot |
| Repo **variables** | `DEPLOY_ENABLED`, `DEPLOY_DOMAIN`, `SENTRY_ORG_SLUG` |
| Repo **secrets** | `AUTH_RSA_PRIVATE_KEY`, `DB_PASSWORD`, `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET`, `ANTHROPIC_API_KEY`, `VOYAGE_API_KEY`, `GRAFANA_ADMIN_PASSWORD`, `SENTRY_AUTH_TOKEN`, `SENTRY_DSN_AUTH`/`SENTRY_DSN_AUDIT` |

The multi-line RSA key travels as a 600-mode file (`secrets/auth_key.pem`) exported into the
compose shell — `.env` files can't carry multi-line values.

## What runs on the VM

The same compose stack as [DEV](onboarding-dev.md) layered with
`docker-compose.ghcr.yml` (pull, don't build) and `docker-compose.prod.yml`:
**Caddy on 80/443 is the only published port** — every other port is withdrawn, and the dev GUIs
(Kafka UI, Adminer, Redis Insight) don't run at all (`local-tools` profile). `PROD` profile:
WARN logging, no seeders or demo-data endpoints; the recruiter demo login stays.

## Verify a deployment

The Deploy workflow smoke-checks the homepage, audit health, and an MCP `ping` (40×10s patience
each). Manual spot checks:

```bash
curl -s https://ask-app.sahilparekh1212.com/audit-api/actuator/health   # {"status":"UP"}
curl -s https://ask-app.sahilparekh1212.com/grafana/api/health          # database: ok
# end-to-end: demo login → its LOGIN event appears in the audit dashboard within seconds
```

## Operate the VM

```bash
gcloud compute ssh ask-app-vm --zone=us-east1-b            # OS Login
cd /opt/ask-app
docker compose ps                                          # stack status
docker logs askapp-audit --tail 100                        # one service's logs
```

- **Grafana** is public read-only at `/grafana` (anonymous Viewer); the admin password is the
  `GRAFANA_ADMIN_PASSWORD` secret, **reset on every deploy** (drift-proof — the env var only
  applies on first DB init). Prometheus/Loki/Tempo publish no ports: access them from inside the
  compose network over SSH ([How To — Prometheus, Loki & Tempo](how-to/prometheus-loki-tempo.md)).
- **Backups**: [`deploy/backup.sh`](../Backend/deploy/backup.sh) streams `pg_dump` (audit rows
  only — the RAG index self-heals from the bundled corpus) to GCS with a 30-day lifecycle rule;
  install as a VM cron per [deployment.md §5.1](../Backend/docs/deployment.md).

## Rollback

Images are promoted by digest: every merge publishes `sha-<short>` tags. To pin the stack to a
known-good build, set `AI_SANDBOX_TAG=sha-abc1234` in `/opt/ask-app/.env` and re-run
`docker compose ... up -d --no-build` (or temporarily set it in `deploy.yml`'s shipped `.env`).
Database changes are expand/contract Liquibase changesets, so the previous image runs against the
newer schema.

## Verify provenance (anyone can)

```bash
cosign verify \
  --certificate-identity-regexp 'https://github.com/sahilparekh1212/ask-app/\.github/workflows/cd\.yml@.*' \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com \
  ghcr.io/sahilparekh1212/ask-app/audit:latest
```

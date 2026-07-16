# 🚦 Continuous integration and delivery

_Part of the [ask-app](../README.md) documentation._

Everything below runs in GitHub Actions — enforced in CI rather than via local
git hooks — so every change is gated the same way no matter who pushes it.

## ✅ Quality gates (on every pull request)

- 🧹 **Lint** — repo-wide hygiene, 🔑 secret scanning (gitleaks +
  `detect-private-key`), and commit-message linting, all reusing
  `.pre-commit-config.yaml` as the single source of truth.
- ☕ **Backend CI** — Spotless formatting, JUnit tests with a JaCoCo **90%
  coverage gate**, `diff-cover` on changed lines, a 🏋️ k6 load test, and 🛡️
  Trivy CVE scans of both the built jars and the container images (results
  uploaded as SARIF to the Security tab).
- 🅰️ **Frontend CI** — ESLint, Prettier, Angular unit tests, and a production
  build.
- 🔎 **CodeQL** — static application security testing (SAST) across the code.
- 📜 **API contract** — regenerates each service's OpenAPI spec from the running
  code and fails on breaking changes via `openapi-diff` (additive changes pass;
  an approved break is opt-in by label).
- 🎭 **E2E** — Playwright drives the full compose stack end to end
  (browser → nginx → Auth → Kafka → Audit → PostgreSQL), nothing mocked.
- 🧬 **Mutation testing** — PIT runs report-only to surface weak assertions that
  line coverage alone would miss.

## 🚀 Delivery (on merge to `main`)

- 📦 **CD** — builds each deployable image once and pushes to GitHub Container
  Registry with three tags (SemVer `0.1.<run>`, `sha-<short>`, and `latest`),
  generates an 📋 SBOM with syft, and ✍️ signs each image **digest** keylessly
  with cosign (GitHub OIDC identity + the public Rekor transparency log — no
  signing key to store or rotate).
- ☁️ **Deploy** — ships the compose stack to the production GCE VM, authenticating
  keylessly through Workload Identity Federation (no exported cloud credentials
  anywhere), then smoke-checks the live origin.

import { Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { environment } from '../../../environments/environment';

interface TechGroup {
  area: string;
  items: string[];
}

interface Decision {
  title: string;
  why: string;
  how: string;
  /** Optional external proof link (e.g. the live Grafana) rendered under the why/how pair. */
  link?: { label: string; href: string };
}

interface DecisionGroup {
  heading: string;
  blurb: string;
  items: Decision[];
}

interface Feature {
  title: string;
  blurb: string;
  link?: { label: string; to: string };
  /** External counterpart to `link` — opens outside the SPA (e.g. the live Grafana). */
  extLink?: { label: string; href: string };
}

@Component({
  selector: 'app-home',
  imports: [RouterLink],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent {
  readonly repoUrl = 'https://github.com/sahilparekh1212/AI-Sandbox';
  readonly linkedInUrl = 'https://www.linkedin.com/in/sahilparekh1212/';
  // The deployment's read-only Grafana (anonymous Viewer) — the live "system view" the
  // observability decision talks about. Environment-driven: same-origin /grafana in the
  // served builds (Caddy in prod, the ui nginx locally), direct Grafana under ng serve.
  readonly grafanaUrl = environment.grafanaUrl;

  // Which "Design decisions" group is shown; the others stay in the DOM but hidden, so
  // switching is instant (no re-render) and there's no long scroll through every group.
  readonly activeDecision = signal(0);

  readonly stack: TechGroup[] = [
    {
      area: 'Backend',
      items: ['Java 17', 'Spring Boot 3', 'Spring Security (OAuth2 / JWT)', 'Spring Data JPA'],
    },
    { area: 'Frontend', items: ['Angular 19', 'TypeScript', 'RxJS + Signals', 'SCSS'] },
    {
      area: 'Messaging & data',
      items: ['Apache Kafka (Redpanda)', 'PostgreSQL', 'Redis', 'Liquibase'],
    },
    { area: 'Observability', items: ['Prometheus', 'Grafana', 'Loki', 'Tempo (OpenTelemetry)'] },
    {
      area: 'Build & CI/CD',
      items: ['Gradle', 'Docker', 'GitHub Actions', 'k6', 'CodeQL + Trivy'],
    },
    {
      area: 'AI',
      items: [
        'Claude (Anthropic Java SDK)',
        'RAG (pgvector + Voyage embeddings)',
        'MCP server (Model Context Protocol)',
      ],
    },
  ];

  readonly decisionGroups: DecisionGroup[] = [
    {
      heading: 'Tech stack — why each choice',
      blurb: 'What each technology was picked for, not just that it is used.',
      items: [
        {
          title: 'Java 17 + Spring Boot',
          why: 'A production REST service needs security, data access, validation, messaging, and health/metrics to be solved problems — not hand-rolled. The JVM ecosystem and Spring Boot 3 provide all of that with first-class OAuth2 resource-server and JWT support.',
          how: 'Two Spring Boot services composed from starters (web, security, oauth2-resource-server, data-jpa, actuator, kafka); business logic stays in services, plumbing in configuration.',
        },
        {
          title: 'Angular (standalone) for the SPA',
          why: 'For a structured, multi-feature app, an opinionated batteries-included framework (router, typed forms, HTTP, DI, testing) beats assembling micro-libraries — consistency and long-term maintainability matter more than minimal bundle size.',
          how: 'Angular 19 standalone components, a functional HTTP interceptor + route guard, and signal-based state; Karma/Jasmine unit tests run headless in CI.',
        },
        {
          title: 'Kafka (Redpanda locally)',
          why: 'The audit trail is a side effect of an action, not part of it — a durable log lets Auth emit an event and move on, decoupling the two services and giving replay and at-least-once delivery. Redpanda gives the Kafka API locally without the JVM/ZooKeeper weight.',
          how: 'Auth produces to the audit.events topic; Audit consumes it. Same Kafka protocol in every environment; managed/multi-broker Kafka is the production target.',
        },
        {
          title: 'PostgreSQL for the audit store',
          why: 'Audit rows are relational and are queried by filters, ranges, and grouped aggregations that want real indexes and durability — a relational database is the right tool, and Postgres is the robust open default.',
          how: 'Spring Data JPA over Postgres 16, with indexes backing the search and stats queries; H2 is used only for tests and the LOCAL profile.',
        },
        {
          title: 'Redis for the refresh-token store',
          why: 'To scale Auth past one replica, a single-use refresh token must be shared across pods and consumed atomically so it can never be redeemed twice — exactly what Redis GETDEL + key TTL provide.',
          how: 'A RefreshTokenStore strategy with a Redis implementation (atomic GETDEL) selected by configuration; the in-memory implementation stays the dev/test default.',
        },
        {
          title: 'Official Anthropic SDK, not Spring AI',
          why: 'The LLM assistant has exactly one call site, and its hard requirement is knowing precisely which bytes leave for the provider. A portability/prompt-templating abstraction earns nothing here and would obscure that boundary.',
          how: 'A thin server-side proxy builds the request explicitly from screened text plus a server-assembled system prompt — see ADR-0009.',
        },
      ],
    },
    {
      heading: 'Design patterns — why each one',
      blurb: 'The patterns are chosen to solve a specific problem, and each carries a tradeoff.',
      items: [
        {
          title: 'Event-driven audit (fire-and-forget + idempotent consumer)',
          why: 'Auth must record what happened without being coupled to — or blocked by — the audit store or the broker being up; and at-least-once delivery means the consumer will occasionally see a duplicate.',
          how: 'Auth publishes login/refresh/logout events @Async fire-and-forget; Audit consumes idempotently (dedup by eventId), retries, then dead-letters to audit.events.DLT. A trace follows the request across the Kafka hop.',
        },
        {
          title: 'RBAC via RSA-signed JWTs (asymmetric)',
          why: 'Stateless services must verify identity and role locally, with no shared session store and no call back to Auth per request. Asymmetric signing means verifiers hold only the public key — they can validate without being able to mint tokens.',
          how: 'Auth signs with an RSA private key and publishes the public key at a JWKS endpoint; each service verifies locally and maps the roles claim to Spring authorities. Admin-only actions use @PreAuthorize (ADR-0001).',
        },
        {
          title: 'Strategy pattern for statelessness',
          why: 'Swapping the refresh-token store between in-memory (dev) and Redis (scaled) shouldn’t touch a single caller — and not every component belongs in Redis.',
          how: 'A RefreshTokenStore interface with two implementations selected by @ConditionalOnProperty. The rate limiter is deliberately left per-pod — it’s a thread-interrupt dedup, not a counter, so it must stay process-local (ADR-0007).',
        },
        {
          title: 'Newest-wins rate limiting',
          why: 'Under burst load a user’s newer request should win rather than queue behind a stale one — graceful shedding beats blocking.',
          how: 'A HandlerInterceptor + ThreadLocal enforces "newest wins per user+endpoint"; a superseded request is rolled back transactionally and returns 429 with Retry-After — proven under a k6 burst.',
        },
        {
          title: 'One JPA Specification for search and stats',
          why: 'The paginated rows and the aggregated counts must always agree on what "matching" means, or the dashboard lies.',
          how: 'Both the search query and the database-side GROUP BY aggregation are built from the same AuditLogSpecifications, so the WHERE clause is identical and both ride the same indexes.',
        },
        {
          title: 'Domain dashboard vs system observability',
          why: 'Two different questions need two different views: "what did users and agents do?" is a business/domain question, while "how are the servers performing?" is a system question — conflating them buries one in the other.',
          how: 'The audit dashboard is the domain view, fed by the event-sourced audit trail (every feature emits a domain event; Audit is the sink). The system view is a separate self-hosted Grafana/Prometheus/Loki/Tempo stack: request rates, p95/p99 latency, logs, and traces that follow a login across the Kafka hop — published read-only from the production deployment.',
          link: {
            label: 'Open the live Grafana (read-only) →',
            href: this.grafanaUrl,
          },
        },
        {
          title: 'Guarded LLM proxy with a one-class allowlist',
          why: 'Sending app data to a third-party model makes the data flow the security decision; the browser must never hold the API key or reach the provider directly, and a user must not be able to pull data their role can’t see.',
          how: 'A server-side proxy holds the key, screens inbound text for secrets/PII, never forwards auth headers, and scopes context by role in a single context-builder class — the whole allowlist has a one-class answer (ADR-0009).',
        },
      ],
    },
    {
      heading: 'Data & migrations — schema as code (Liquibase)',
      blurb: 'The database schema is versioned and reviewable, exactly like the application code.',
      items: [
        {
          title: 'Liquibase owns the schema',
          why: 'Letting Hibernate auto-generate DDL drifts silently between environments and leaves nothing for a PROD `validate` to check against. The schema should be an explicit, versioned, code-reviewed artifact.',
          how: 'A changelog at Liquibase’s default path creates the audit_logs table and its indexes (including the unique idx_audit_event_id that backs idempotent consumption). It runs on startup in every profile.',
        },
        {
          title: 'Hibernate runs ddl-auto=none',
          why: 'With Liquibase as the single source of truth for schema, Hibernate must neither create nor mutate tables — one owner, no races between the two.',
          how: 'spring.jpa.hibernate.ddl-auto=none in every profile; Hibernate only maps to the schema Liquibase already applied.',
        },
        {
          title: 'Expand / contract for safe rollouts',
          why: 'During a rolling deploy an old replica and a new replica run against the same database at once, so a migration can’t break the schema the old code still expects.',
          how: 'Changesets are kept backward-compatible (add before remove), so a rollout — and a rollback — is safe. Detailed in the deployment plan.',
        },
      ],
    },
    {
      heading: 'CI / CD — how quality is enforced',
      blurb: 'Every PR is gated; nothing reaches main unproven. Built with GitHub Actions.',
      items: [
        {
          title: 'Tests + a meaningful coverage gate',
          why: 'Coverage is only useful as a gate, not a vanity number — and a PR should be judged on the lines it changes.',
          how: 'JaCoCo enforces a 90% line-coverage minimum per module (the build fails below it); diff-cover checks coverage on changed lines. Backend and frontend test suites both run on every PR.',
        },
        {
          title: 'Load & performance proof (k6)',
          why: 'Claims like "handles high concurrency" and "sheds load gracefully" need evidence, not assertion.',
          how: 'A k6 job drives the API under load in CI (search/stats throughput and a rate-limit burst), asserting p95 latency and that 429s shed cleanly with no 5xx.',
        },
        {
          title: 'Security scanning in depth',
          why: 'Vulnerabilities hide in three places — your code, your dependencies, and your base images — so all three are scanned, and secrets must never be committed.',
          how: 'CodeQL SAST on the Java code, Trivy CVE scans of both the boot jars and the Docker images (fixable HIGH/CRITICAL gate the merge), Dependabot for dependency updates/alerts, and gitleaks + private-key detection in a pre-commit hygiene job.',
        },
        {
          title: 'Release discipline & supply chain',
          why: 'main must always be releasable and its history reviewable; and the current supply-chain bar is provenance, not just scanning.',
          how: 'Branch protection makes main PR-only (admins included) with required status checks and conventional-commit linting. Planned next: versioned images published to GHCR on merge, plus an SBOM (syft) and image signing (cosign).',
        },
      ],
    },
  ];

  readonly features: Feature[] = [
    {
      title: 'Observability',
      blurb:
        'The domain view of the system — what users and agents did. KPI cards, events-over-time, and database-side aggregations over the event-sourced audit trail, complementing the Grafana/Prometheus/Loki/Tempo stack’s system view of how the servers are performing.',
      link: { label: 'Open the audit dashboard →', to: '/observability' },
      extLink: { label: 'Open the system view (Grafana) →', href: this.grafanaUrl },
    },
    {
      title: 'Chat',
      blurb:
        'Ask a Claude model about this application. Answers are grounded via RAG — each question retrieves the most relevant chunks of the repo’s own docs (README, ADRs) from a pgvector index using Voyage embeddings — plus role-scoped audit data, with server-side guardrails. The same index is exposed to any MCP client as a Model Context Protocol server.',
      link: { label: 'Open the chat →', to: '/chat' },
    },
    {
      title: 'Flashcards',
      blurb:
        'Generate an LLM study deck about the app — architecture, design decisions, and tradeoffs — through the same guarded Claude proxy and doc-grounded context as the chat.',
      link: { label: 'Open the flashcards →', to: '/flashcards' },
    },
    {
      title: 'Auth',
      blurb:
        'Google OAuth2 or a zero-setup demo login, both issuing the same RSA-signed JWTs; the SPA attaches them as Bearer tokens and silently refreshes on 401.',
      link: { label: 'Sign in →', to: '/login' },
    },
  ];
}

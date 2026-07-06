import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

interface TechGroup {
  area: string;
  items: string[];
}

interface DesignDecision {
  title: string;
  why: string;
  how: string;
}

interface Feature {
  title: string;
  blurb: string;
  link?: { label: string; to: string };
}

@Component({
  selector: 'app-home',
  imports: [RouterLink],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent {
  readonly repoUrl = 'https://github.com/sahilparekh1212/AI-Sandbox';

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
    { area: 'AI', items: ['Claude (Anthropic Java SDK)'] },
  ];

  readonly decisions: DesignDecision[] = [
    {
      title: 'Event-driven audit trail',
      why: 'Auth should record what happened without being coupled to — or blocked by — the audit store or the broker being up.',
      how: 'Auth publishes login/refresh/logout events to Kafka fire-and-forget; Audit consumes them idempotently (dedup by eventId), retries, and dead-letters failures. A trace follows the request across the Kafka hop.',
    },
    {
      title: 'RBAC with RSA-signed JWTs',
      why: 'Stateless services need to verify identity and role without a shared session store or a call back to Auth on every request.',
      how: 'Auth signs JWTs with an RSA key and publishes the public key at a JWKS endpoint; each service verifies locally and maps the roles claim to Spring authorities. Admin-only actions use @PreAuthorize.',
    },
    {
      title: 'Stateless, horizontally scalable services',
      why: 'To run more than one replica behind a load balancer, no request may depend on in-process state.',
      how: 'Refresh tokens move to Redis behind a strategy interface (atomic GETDEL for single-use across replicas); the RSA key is externalized. The rate limiter stays deliberately per-pod — it is a thread-interrupt dedup, not a counter.',
    },
    {
      title: 'Graceful load shedding (rate limiting)',
      why: 'Under burst load, a newer request from a user should win rather than pile up behind a slow one.',
      how: 'A HandlerInterceptor + ThreadLocal enforces "newest wins per user+endpoint"; a superseded request is rolled back transactionally and returns 429 with Retry-After — verified under a k6 burst.',
    },
    {
      title: 'Observability as three pillars',
      why: '"It works on my machine" is not evidence; a reviewer should be able to see metrics, logs, and traces for real traffic.',
      how: 'Micrometer metrics (with p95/p99 latency histograms) to Prometheus/Grafana, structured logs with requestId/userId correlation to Loki, and OpenTelemetry traces to Tempo — all tagged by pod.',
    },
    {
      title: 'LLM assistant behind a guarded proxy',
      why: 'Sending app data to a third-party model makes the data flow the security decision; the browser must never hold the API key or reach the provider directly.',
      how: 'A server-side proxy holds the key, screens inbound text for secrets/PII before forwarding, never proxies auth headers, and scopes context by role (aggregate stats for users, recent rows for admins).',
    },
  ];

  readonly features: Feature[] = [
    {
      title: 'Audit dashboard',
      blurb:
        'Server-side paginated, sortable, filterable table over the audit trail, with database-side aggregation shown as dependency-free bar charts.',
      link: { label: 'Open the audit dashboard →', to: '/audit' },
    },
    {
      title: 'Assistant',
      blurb:
        'Ask a Claude model about this application; answers are grounded on the app docs and role-scoped audit data, with server-side guardrails.',
      link: { label: 'Open the assistant →', to: '/assistant' },
    },
    {
      title: 'Auth',
      blurb:
        'Google OAuth2 or a zero-setup demo login, both issuing the same RSA-signed JWTs; the SPA attaches them as Bearer tokens and silently refreshes on 401.',
      link: { label: 'Sign in →', to: '/login' },
    },
  ];
}

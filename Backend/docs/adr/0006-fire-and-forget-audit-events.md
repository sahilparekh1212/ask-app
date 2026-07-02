# ADR-0006: Fire-and-forget audit event publishing — at-most-once on broker outage, no outbox

**Status:** Accepted (deliberately not built — this ADR is the "accept the tradeoff and document
it" half of the TODO item, not a decision the gap doesn't matter)

## Context

`AuditEventPublisher.publish()` is `@Async` and fire-and-forget: it calls `kafkaTemplate.send()`
and returns immediately, logging (not propagating) any delivery failure. If Kafka is down or
unreachable at the moment of a LOGIN/TOKEN_REFRESH/LOGOUT, the `AuditEvent` is silently lost —
Auth's own action (the login, the refresh) already succeeded and committed; only its audit trail
entry never makes it to Audit.

The textbook fix for "don't lose the event if the broker is down" is the transactional outbox
pattern: write the event to an `outbox` table in the *same database transaction* as the source
action, then relay outbox rows to Kafka via a poller or CDC (e.g. Debezium). That relay only ever
touches Kafka after the source action's transaction has already durably committed, so a broker
outage delays delivery instead of losing the event.

## Decision

Keep fire-and-forget. Don't build an outbox.

The blocking reason isn't effort, it's that **Auth has no datastore** — outbox requires a
transactional store to write the event *into*, atomically with the source action. Auth is
deliberately stateless/in-memory today (see ADR-0002: `ConcurrentHashMap` refresh-token store, no
database at all). Building an outbox would mean giving Auth a database first, purely to back a
delivery guarantee for a side-channel audit trail — a much bigger architectural change than the
audit item alone justifies, and one that should be a deliberate decision (see the "make services
stateless" TODO item), not a side effect of hardening event delivery.

## Alternatives considered

- **Transactional outbox with Auth's own new database.** The correct long-term fix, and worth
  building *if* Auth gets a datastore for other reasons (e.g. externalizing refresh tokens to a
  real store instead of Redis/in-memory). Rejected as a standalone change — see Decision.
- **Outbox against Audit's existing Postgres instead.** Doesn't work: the outbox pattern requires
  the event to be written in the *same transaction* as the source action (the login/refresh), and
  the source action happens in Auth, not Audit. Writing to Audit's DB from Auth would be a
  cross-service transaction, which is exactly the distributed-transaction problem the outbox
  pattern exists to avoid.
- **Kafka producer `acks=all` + retries + idempotence.** Already effectively in place via Spring
  Kafka's producer defaults and doesn't change the failure mode this ADR is about: those settings
  make delivery reliable *once the broker is reachable*, but do nothing for the case where the
  broker is down for the entire window the async publish attempt runs in — there's no local
  durable record to retry from once that window passes.
- **Make the publish synchronous and fail the auth request if Kafka is down.** Explicitly rejected
  by the existing code comment and by design: authentication must not be coupled to the audit
  pipeline's availability. A Kafka outage should never turn into recruiters/reviewers unable to
  log in. This tradeoff is arguably the more important one — losing an audit log row is bad;
  breaking login over an unrelated system being down is worse.

## Consequences

- **At-most-once delivery on broker outage**, not at-least-once. A LOGIN/TOKEN_REFRESH/LOGOUT
  that happens while Kafka is unreachable produces no `AuditLog` row in Audit — the action itself
  still succeeds. This is a real, accepted gap in the audit trail's completeness, not a corner
  case that happens to be untested.
- Once delivered, the rest of the pipeline is already sound and doesn't need this ADR to explain
  it: idempotent consumption (`existsByEventId`), retry + dead-letter (`audit.events.DLT`) for
  processing failures on the *Audit* side. This ADR is specifically about the publish-time gap,
  before the message exists anywhere durable.
- If audit-trail completeness under broker outages becomes a real requirement (e.g. a compliance
  need, not just a nice-to-have), the trigger to revisit this is Auth getting a datastore for any
  reason — at that point, outbox is the natural next step, not a separate project.

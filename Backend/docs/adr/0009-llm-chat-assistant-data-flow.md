# ADR-0009: LLM chat assistant — server-side proxy, and exactly what can reach the provider

**Status:** Accepted

## Context

The SPA gained a chat page where a user asks questions about the application ("what does the
audit service do?", "which actions were most frequent?") and a Claude model answers, grounded
on the app's own documentation and live audit data. Sending application data to a third-party
LLM provider makes the *data flow* the security-relevant design decision — this ADR documents
what can and cannot reach the provider, and why the pieces sit where they do.

## Decision

### A thin server-side proxy in the Audit service — the SPA never talks to the provider

`POST /api/v1/assistant/chat` (new `assistant/` package in Audit) is the only path to the LLM.
The Anthropic API key lives exclusively in the Audit service's environment
(`ANTHROPIC_API_KEY`); the browser never sees it and no provider endpoint is reachable from
the SPA. Without a key the endpoint degrades to 503 and nothing else is affected.

The proxy lives in **Audit** (not Auth, not a third service) because the only live data the
assistant is grounded on is audit data, already behind this service's `AuditLogService` — and
adding a third service would re-open the API-gateway calculus deliberately settled in
ADR-0005.

### Official Anthropic Java SDK, not Spring AI

Spring AI was considered (it has Anthropic support) and rejected for this use: it's a large
abstraction layer whose value is provider portability and prompt templating across many call
sites — this feature has exactly one call site, and its hard requirement is *knowing precisely
which bytes leave for the provider*. The official SDK gives that control directly; the model
id and token limits stay config-tunable (`assistant.model`, default `claude-opus-4-8`;
`ASSISTANT_MODEL=claude-haiku-4-5` for cost).

### What CAN reach the provider (allowlist, assembled server-side)

1. The static grounding doc (`assistant/app-context.md` on the classpath — a description of
   the app, no secrets, no user data).
2. **Aggregate** audit statistics: total row count and counts grouped by action/entityType —
   the same numbers the public `/stats` endpoint serves. No row contents.
3. **ROLE_ADMIN only:** the 20 most recent raw audit rows (whose `details` may carry user
   identifiers). The role comes from the verified JWT's `roles` claim; an ordinary user's
   request never triggers the row query at all — the gate is in the context *builder*, not in
   the prompt.
4. The user's message and replayed chat history — but only after screening (below).

`AssistantContextBuilder` is the entire allowlist: no other code path feeds the outbound
request, so "what can the assistant see?" has a one-class answer.

### What CANNOT reach the provider

- **`Authorization` headers, JWTs, cookies** — never forwarded *by construction*: the
  outbound request is built from the three inputs above; nothing copies inbound HTTP headers.
  The JWT is used only to authenticate the proxy call and derive the role.
- **Screened content** — `PromptScreener` runs over the message and every history turn
  *before* the provider call. It rejects token-shaped strings (JWT `eyJ…` segments, `Bearer`
  values), credential keywords being assigned a value (`password=…`, `api_key: …` — asking
  *about* passwords passes), email addresses, and card-like digit runs. A match is answered
  locally with a canned "can't help with credentials/personal data" reply (`blocked: true`)
  and nothing is forwarded. This protects against the realistic accident: a user pasting a
  live token while asking why it doesn't work.
- **Log leakage** — on a block, only the violation *category* and message length are logged,
  never the matched text; reply/message contents are not logged on the happy path either.

### Prompt-injection posture

Retrieved data (docs, stats, rows) is wrapped in tags and the system prompt instructs the
model to treat tag contents as reference data, never instructions — so a maliciously crafted
audit row (`details = "ignore previous instructions…"`) is inert context, not a steering
channel. The system prompt also tells the model the caller's role and, for USER, that raw
rows don't exist in its context and must not be guessed at.

### Rate limiting and validation

The existing newest-wins rate limiter applies to the endpoint automatically (it's registered
for all MVC endpoints), so a user can't fan out concurrent provider calls. Bean Validation
caps message length (2000), history length (20 turns × 4000 chars), and restricts roles to
`user|assistant` — bounding the maximum prompt a client can construct.

## Consequences

- The provider sees aggregate numbers for every chat, and recent raw audit rows for admin
  chats. That is an accepted, documented disclosure — bounded by role, by row count (20), and
  by the screening rules; it is the phase-2 "read-only aggregate data" scope of the original
  TODO item plus the deliberate admin extension.
- The screen is pattern-based and can false-positive (a user typing any email is refused).
  That's the right tradeoff for a guardrail whose failure mode is exfiltration to a third
  party.
- Chat history round-trips through the client (the server is stateless, consistent with the
  rest of the platform), which is why history is re-screened on every request — the client
  is not trusted to replay only clean turns.
- If the assistant ever needs *tool use* (live queries decided by the model), the allowlist
  moves from "context builder" to "tool definitions" — the same one-class-answer property
  should be preserved.

## Addendum — the flashcard generator reuses this data flow

`POST /api/v1/assistant/flashcards` (a second LLM feature) generates a study deck about the
app. It deliberately reuses the same seams rather than duplicating them: the same `LlmClient`
proxy (server-side key, no auth headers forwarded), and the **same allowlist** — the context
builder's role-scoped grounding block was extracted into a shared `groundingContext(admin)`
method that both `buildSystemPrompt` (chat) and the flashcard prompt compose. So the RBAC
disclosure boundary is identical: a USER-role deck can only draw on docs + aggregate stats, an
ADMIN deck additionally on recent rows. Differences from chat: there's no free-text user input
(the request is just a count 1..20), so `PromptScreener` isn't in this path — the injection
surface is a fixed internal prompt, and the retrieved data is still tag-wrapped as
non-instructional. The model is asked for strict JSON; an unreadable/empty reply is treated as
a provider failure (503), and malformed cards are dropped so the UI never renders a blank.

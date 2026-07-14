# Frontend (UI) guide — the Angular SPA, page by page

This is a component-level guide to the `UI/` Angular 19 single-page app: for each page it names
the component and template file, the interactive elements on it, the Angular service it calls, and
the **backend endpoint** that call hits. It complements the file index in
[`code-map.md`](code-map.md) — use that for "which file is X", and this for "which component has
control Y and what endpoint does it hit". All paths are under `UI/src/app/` unless noted.

## How the SPA talks to the backend

- `UI/src/environments/environment.ts` — production base URLs are **same-origin** paths:
  `authApiUrl = /auth-api`, `auditApiUrl = /audit-api`. `UI/nginx.conf` proxies `/auth-api/` →
  `auth:8085` and `/audit-api/` → `audit:8083`, so the browser needs no CORS.
  `environment.development.ts` points at the services' host ports for `ng serve`.
- `core/auth/auth.interceptor.ts` — attaches `Authorization: Bearer <token>` to API calls and does
  one silent refresh-and-retry on a 401.
- `core/auth/auth.guard.ts` — route guard; redirects to `/login` with a `returnUrl` when signed out.
- `core/auth/token-storage.service.ts` — the localStorage token store.

## App shell & navigation (header tabs)

- `app.component.html` / `app.component.ts` — the shell, a VS Code-style layout: an **activity rail**
  (`<nav class="rail">`) with the primary sections **About** → `/about`, **Chat** → `/chat`,
  **Observability** → `/observability`; the account slot pinned at the rail's bottom is a circular
  avatar link → `/profile` when authenticated (`isAuthenticated()` signal), or a **Sign in** link →
  `/login` when not, with the language switcher just above it. A contextual sidebar ("on this page",
  scroll-spy) and a top bar (page title + GitHub/LinkedIn links) complete the chrome. The routed
  page renders in `<router-outlet>` inside `<main class="app-main">`.

## Routes → components

Defined in `app.routes.ts`:

| Route | Component | Guarded |
| --- | --- | --- |
| `/about` | `features/home/home.component` (About) | no |
| `/login` | `features/login/login.component` | no |
| `/login/callback` | `features/auth-callback/auth-callback.component` | no |
| `/profile` | `features/profile/profile.component` | yes |
| `/observability` | `features/audit/audit.component` (Dashboard) | yes |
| `/chat` | `features/assistant/assistant.component` (Chat) | yes |
| `**` | redirect to `/about` | — |

Legacy paths `/audit`, `/assistant`, and `/flashcards` redirect to `/observability` and `/chat`
(flashcards was removed and folded into the chat assistant).

## Chat (Assistant) page — the chat input line

- Route: `/chat` (guarded). Component: `features/assistant/assistant.component.ts` +
  `assistant.component.html`.
- **The chat input line** is the text input in the composer form at the bottom of
  `assistant.component.html`: `<form class="composer" (ngSubmit)="send()">` containing
  `<input type="text" formControlName="message" placeholder="Ask a question about this app…"
  maxlength="2000">` and a **Send** button. Above it, `<div class="chat-log">` renders each turn
  (You / Assistant bubbles) and a "Thinking…" placeholder while `busy()` is true.
- Service: `features/assistant/assistant.service.ts` → `chat(message, history)` does
  **`POST {auditApiUrl}/api/v1/assistant/chat`** with body `{ message, history }` (history capped at
  the last 20 turns).
- Backend: `AssistantController.chat()` in the Audit service (`assistant/AssistantController.java`).

## Audit dashboard page

- Route: `/audit` (guarded). Component: `features/audit/audit.component.ts` +
  `audit.component.html`. This is the main dashboard.
- **Filters form** (`<form class="filters">`): an **Entity type** `<select>`, an **Action**
  `<select>` (both populated from the accumulated union of `/stats` bucket keys), a **Details**
  "contains…" text input, **From** / **To** `datetime-local` inputs, an **Include deleted**
  checkbox, and an **Apply filters** button.
- **Demo logs tools** (only rendered when `demoAvailable()` — the backend endpoint exists in
  LOCAL/DEV only): a count number input (1–500) and an **Add demo logs** button.
- **Stats** section: total-entries count plus two dependency-free CSS bar charts, **By action** and
  **By entity type**.
- **Table**: columns Time / Entity / Action / Details, with sortable Time/Entity/Action headers
  (click to toggle asc/desc). Below it a **pager** (Previous / Next + page-of-total).
- Service: `features/audit/audit.service.ts`:
  - `search(filter, page)` → **`GET {auditApiUrl}/api/v1/audit-logs/search`** (filter + page/size/sort params)
  - `stats(filter)` → **`GET /api/v1/audit-logs/stats`**
  - `features()` → **`GET /api/v1/meta/features`** (drives `demoAvailable()`)
  - `addDemoLogs(count)` → **`POST /api/v1/audit-logs/demo`**
- Backend: `AuditLogController` (search/stats), `MetaController` (features), `DemoDataController` (demo).

## Login page

- Route: `/login`. Component: `features/login/login.component.ts` + `login.component.html`.
- Controls: **Username** and **Password** inputs, a **Role** `<select>` (`ROLE_USER` / `ROLE_ADMIN`,
  for the demo login), a **Demo login** submit button, and a **Sign in with Google** button.
- Service: `core/auth/auth.service.ts`:
  - `demoLogin({username, password, role})` → **`POST {authApiUrl}/auth/login`**, stores the returned tokens.
  - `loginWithGoogle()` → navigates the browser to **`{authApiUrl}/oauth2/authorization/google`**.
- Backend: `AuthController` (demo login) and `OAuth2LoginSuccessHandler` (Google → redirect to `/login/callback`).

## OAuth callback page

- Route: `/login/callback`. Component: `features/auth-callback/auth-callback.component.ts`.
- After Google sign-in, the Auth server redirects here with the tokens in the URL **fragment**
  (`#access_token=…&refresh_token=…&expires_in=…`). The component calls
  `AuthService.consumeOAuthFragment(...)` to read and store them, then navigates on. No backend call
  of its own — the tokens were already issued by Auth.

## Profile page

- Route: `/profile` (guarded). Component: `features/profile/profile.component.ts` +
  `profile.component.html`.
- Shows the current user's **User ID / Email / Name** and a **Log out** button.
- Service: `core/auth/auth.service.ts`:
  - `loadProfile()` → **`GET {authApiUrl}/auth/me`**
  - `logout()` → **`POST {authApiUrl}/auth/logout`** (then clears local tokens)
- Also uses `refresh()` → **`POST /auth/refresh`** (invoked by the interceptor on 401, not from this page).

## Home / About page

- Route: `/`. Component: `features/home/home.component.ts` + `home.component.html`.
- Static portfolio content rendered from typed arrays: a tech-stack overview, design decisions with
  Why/How, and a feature tour linking into the app. No backend calls.

## Auth plumbing (`core/auth/`) and the endpoints it uses

- `auth.service.ts` — the auth lifecycle (demo/Google login, OAuth fragment handoff, refresh,
  logout, profile) and the `isAuthenticated` signal the shell/guards read.
- `auth.interceptor.ts` — Bearer attach + single silent refresh-and-retry on 401.
- `auth.guard.ts` — route protection with `returnUrl`.
- `token-storage.service.ts` — localStorage token store.
- `auth.models.ts` — auth DTO types.
- Auth endpoints used by the SPA: `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`,
  `GET /auth/me`, and the browser redirect `GET /oauth2/authorization/google`.

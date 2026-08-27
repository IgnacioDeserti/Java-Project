# Kanban Board

A full-stack Kanban board application for organizing tasks across customizable boards and columns, similar to Trello. The backend is built with Java, Spring Boot, and Spring Web, exposing a REST API for managing boards, columns, and cards, with Hibernate/JPA handling persistence to a PostgreSQL database (schema managed by Flyway) and Maven managing the build. Authentication is handled via JWT (with refresh tokens, email verification and password reset), so each user has their own private boards. The frontend is built with React, featuring drag-and-drop functionality to move cards — and columns — in real time, synced live across sessions over WebSockets. The entire stack is containerized with Docker Compose for easy local setup and deployment, with a CI pipeline building and testing both sides on every push.

## Stack

- **Backend:** Java 17, Spring Boot 3, Spring Security, Hibernate/JPA, Flyway, Maven, JWT (jjwt), Spring Mail, Spring WebSocket (STOMP)
- **Frontend:** React 18, Vite, @hello-pangea/dnd (drag-and-drop), Axios, @stomp/stompjs
- **Database:** PostgreSQL
- **Infra:** Docker, Docker Compose, nginx, GitHub Actions CI

## Features

- Register / log in with short-lived JWT access tokens (15 min) plus long-lived, revocable refresh tokens (30 days). The frontend transparently refreshes an expired access token once before falling back to login.
- Email verification: new accounts can use the app immediately, but a **future** login is blocked until the email is verified (toggle via `REQUIRE_EMAIL_VERIFICATION`). Resend-verification available from the login screen.
- Forgot / reset password, with a link emailed to the user. Resetting a password revokes every existing session.
- Rate limiting on login, register, forgot-password and resend-verification, per client IP, with configurable thresholds.
- Every board belongs to exactly one user — boards, columns and cards are all looked up through the owner, so ids from another account resolve to `404`.
- Boards, columns and cards: create, rename/edit, delete. A new board starts with **To Do / In Progress / Done**.
- Drag-and-drop cards within/across columns, and drag-and-drop to reorder columns themselves. The UI updates optimistically and rolls back if the server rejects the move.
- **Live updates over WebSockets**: any change to a board (card moved, column added, etc.) is broadcast to every connected session viewing it — open the same board in two tabs and watch changes appear without a refresh. Subscriptions are authenticated with the JWT and re-checked against board ownership per-subscribe, so one user can't listen in on another's board.
- Card and column positions stay a dense `0..n-1` sequence: moving or deleting one re-indexes the rest server-side, so ordering never drifts.
- Account settings: rename, change password (logs out every other session), delete account (cascades to every board you own) — all from an in-app modal, no need to log out first.
- Responsive layout down to small screens; icon-only buttons carry `aria-label`s and the "rename column" control is a real, keyboard-reachable button rather than a clickable `<h3>`.
- **Sign in with Google** (optional, off until configured — see [Google OAuth setup](#google-oauth-setup)): links to an existing account by email if one exists, otherwise creates a new one with no local password. Such accounts can add a password later from Account settings — set once, no old one to prove — which then also unlocks email/password login for them.

## Running locally

### With Docker (everything at once)

```bash
cp .env.example .env
# then edit .env: set DB_PASSWORD and JWT_SECRET (openssl rand -base64 48)
docker compose up --build
```

- Frontend: http://localhost:5173 (nginx, which also proxies `/api` and `/ws` to the backend — no CORS involved)
- Backend API: http://localhost:8080/api
- Postgres: localhost:5433 (not 5432 — avoids clashing with a native Postgres install on the host; the backend still talks to the container internally on 5432)

Without SMTP configured (`MAIL_HOST` empty), verification and password-reset emails are logged to the backend console instead of sent — copy the link from `docker compose logs backend` to test those flows locally.

### Without Docker

```bash
# backend (needs a Postgres on localhost:5432 — see application.yml for defaults)
cd backend && mvn spring-boot:run

# frontend (talks to http://localhost:8080/api, allowed by CORS)
cd frontend && npm install && npm run dev
```

## Tests

```bash
cd backend && mvn test        # 26 tests
cd frontend && npm test       # 18 tests
```

Backend:
- `KanbanApiTest` — the whole stack against an in-memory H2 in PostgreSQL mode: auth, validation, board/column/card CRUD, card/column re-indexing on move and delete, and the ownership boundary between two users. Runs with `require-email-verification=false` for convenience.
- `AuthFlowsTest` — email verification gating login, token reuse rejection, refresh-token rotation, logout revocation, and password reset revoking all sessions. Overrides verification back to `true` to exercise the enforced path.
- `ProfileManagementTest` — renaming, changing password (and the old refresh token dying with it), rejecting a wrong current password, and account deletion cascading to boards and killing the session.
- `GoogleLoginTest` — account creation vs. linking-by-email for `AuthService.loginOrRegisterWithGoogle` (the real OAuth2 redirect dance needs a live Google app, so this calls that method directly, the same way `GoogleOAuthSuccessHandler` does once Spring Security has already verified the identity), plus the password-optional change-password/delete-account paths a Google-only account exercises.
- `RateLimitTest` — proves the login throttle actually returns `429` once its limit is exhausted.
- `WebSocketBroadcastTest` — a real STOMP client against a live embedded server: connects with a JWT, subscribes to a board's topic, and asserts a REST mutation produces a broadcast; a second test asserts subscribing to another user's board is rejected.

Frontend: Vitest + Testing Library, covering the API client's error-handling helpers, `LoginForm`'s login/register/verification-resend flows, and `AccountSettings`' rename/change-password/delete-account flows (both the normal and password-optional variants).

CI (`.github/workflows/ci.yml`) runs the backend test suite, the frontend test suite + build, and builds both Docker images, on every push/PR to `main`.

## API overview

All `/api/boards/**` routes require an `Authorization: Bearer <token>` header.

| Method | Endpoint                                          | Description                       |
|--------|----------------------------------------------------|-----------------------------------|
| POST   | `/api/auth/register`                               | Create an account, get a session   |
| POST   | `/api/auth/login`                                   | Log in, get a session               |
| POST   | `/api/auth/refresh`                                 | Trade a refresh token for a new session (rotates it) |
| POST   | `/api/auth/logout`                                  | Revoke a refresh token              |
| POST   | `/api/auth/verify-email`                            | Verify an account with its emailed token |
| POST   | `/api/auth/resend-verification`                     | Re-send the verification email      |
| POST   | `/api/auth/forgot-password`                         | Send a password-reset email (if the account exists) |
| POST   | `/api/auth/reset-password`                          | Set a new password with its emailed token |
| GET    | `/api/auth/me`                                      | Current user (validates the token) |
| PUT    | `/api/auth/me`                                      | Update display name                 |
| DELETE | `/api/auth/me`                                      | Delete the account (with password confirmation) |
| POST   | `/api/auth/change-password`                         | Change password while logged in (revokes other sessions) |
| GET    | `/api/boards`                                       | List my boards                     |
| POST   | `/api/boards`                                       | Create a board (+ default columns) |
| GET    | `/api/boards/{boardId}`                             | Board with columns and cards       |
| PUT    | `/api/boards/{boardId}`                             | Rename a board                     |
| DELETE | `/api/boards/{boardId}`                             | Delete a board and its contents    |
| POST   | `/api/boards/{boardId}/columns`                     | Add a column                       |
| PUT    | `/api/boards/{boardId}/columns/{columnId}`          | Rename a column                    |
| PATCH  | `/api/boards/{boardId}/columns/{columnId}/move`     | Move a column (drag-and-drop)      |
| DELETE | `/api/boards/{boardId}/columns/{columnId}`          | Delete a column and its cards      |
| POST   | `/api/boards/{boardId}/columns/{columnId}/cards`    | Add a card                         |
| PUT    | `/api/boards/{boardId}/cards/{cardId}`              | Edit a card                        |
| DELETE | `/api/boards/{boardId}/cards/{cardId}`              | Delete a card                      |
| PATCH  | `/api/boards/{boardId}/cards/{cardId}/move`         | Move a card (drag-and-drop)        |
| WS     | `/ws` (STOMP)                                       | Subscribe to `/topic/boards/{boardId}` for live updates; authenticate via an `Authorization` STOMP header on CONNECT |

Errors come back as `{ "error": "..." }` (plus `"code": "EMAIL_NOT_VERIFIED"` for that specific case) with `400` (validation), `401` (missing/expired token or bad credentials), `403` (unverified email or access denied), `404` (not found / not yours), or `429` (rate limited).

## Configuration

Copy `.env.example` to `.env` for Docker; the same variables apply when running the backend directly.

| Variable                              | Default            | Purpose                                        |
|----------------------------------------|---------------------|-------------------------------------------------|
| `DB_HOST` / `DB_PORT`                  | `localhost` / `5432`| Postgres location                                |
| `DB_NAME` / `DB_USER` / `DB_PASSWORD`  | —                    | Postgres credentials — **required**, no default |
| `JWT_SECRET`                           | —                    | HMAC key — **required, ≥ 32 bytes** (`openssl rand -base64 48`) |
| `SERVER_PORT`                          | `8080`               | HTTP port                                        |
| `CORS_ALLOWED_ORIGINS`                 | `http://localhost:5173,http://localhost:3000` | Comma-separated origins        |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | empty | SMTP credentials; leave `MAIL_HOST` empty to log emails instead of sending |
| `MAIL_FROM`                            | `no-reply@example.com` | From address on outgoing email                |
| `APP_BASE_URL`                         | `http://localhost:5173` | Used to build links inside emails             |
| `REQUIRE_EMAIL_VERIFICATION`           | `true`               | Set `false` to allow login before verifying (dev only) |
| `RATE_LIMIT_LOGIN_MAX` / `_WINDOW_SECONDS` | `10` / `60`       | Login attempts per IP                            |
| `RATE_LIMIT_REGISTER_MAX` / `_WINDOW_SECONDS` | `5` / `600`     | Registrations per IP                             |
| `RATE_LIMIT_PASSWORD_RESET_MAX` / `_WINDOW_SECONDS` | `5` / `600` | Forgot-password / resend-verification per IP |

The frontend reads `VITE_API_URL` at build time (`/api` in Docker, `http://localhost:8080/api` otherwise); the WebSocket URL is derived from the same value.

### Sending real email through Gmail

No code change needed — `EmailService` already speaks SMTP via `spring-boot-starter-mail`; it's just not configured by default (unconfigured mail is a fully-supported state: emails get logged to the console instead). To send for real through a Gmail account:

1. Turn on 2-Step Verification on the Google account, if it isn't already (required for the next step): https://myaccount.google.com/security
2. Generate an **App Password**: https://myaccount.google.com/apppasswords → choose "Mail" → copy the 16-character password it gives you (not the account's normal password).
3. Set in `.env`:
   ```
   MAIL_HOST=smtp.gmail.com
   MAIL_PORT=587
   MAIL_USERNAME=youraddress@gmail.com
   MAIL_PASSWORD=<the 16-character app password, no spaces>
   MAIL_FROM=youraddress@gmail.com
   ```
4. Restart the backend (`docker compose up -d backend` or `mvn spring-boot:run`). Verification and password-reset emails now actually arrive instead of being logged.

### Google OAuth setup

"Sign in with Google" is off by default (`GoogleOAuthConfig` — the login button still shows, but clicking it lands on the ordinary "authentication required" response) until real credentials are supplied. To turn it on:

1. Go to the [Google Cloud Console](https://console.cloud.google.com/) → create a project (or pick an existing one).
2. **APIs & Services → OAuth consent screen**: choose "External", fill in the required app name/support email, and add your own Google account as a test user if the app is left in "Testing" publish status (fine for local/portfolio use — no Google review needed for test users).
3. **APIs & Services → Credentials → Create Credentials → OAuth client ID** → Application type: **Web application**.
4. Under **Authorized redirect URIs**, add exactly:
   - `http://localhost:8080/login/oauth2/code/google` (running the backend directly, `mvn spring-boot:run`)
   - `http://localhost:5173/login/oauth2/code/google` (running through Docker/nginx — nginx proxies this path to the backend, see `frontend/nginx.conf`)

   Add your real domain's equivalent too if/when this gets deployed.
5. Copy the generated **Client ID** and **Client secret** into `.env`:
   ```
   GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
   GOOGLE_CLIENT_SECRET=your-client-secret
   ```
6. Restart the backend. The "Continue with Google" button on the login screen now works.

A Google sign-in links to an existing account with the same email (trusting Google's verification of that email, since it already required proving ownership to have a Google account with it) rather than creating a duplicate, and a brand-new Google sign-up gets no local password — `hasPassword: false` on the user — until they set one from Account settings.

## Database migrations

Schema is owned by Flyway (`backend/src/main/resources/db/migration/`), not `hibernate.ddl-auto` (set to `validate`, so a mismatch between entities and the real schema fails fast at startup instead of silently drifting). To change the schema: add a new `V{n}__description.sql` file — never edit an already-applied one.

## Project structure

```
kanban-board/
├── .github/workflows/ci.yml   # backend tests, frontend tests+build, Docker builds
├── backend/          # Spring Boot API
│   ├── src/main/java/com/ignaciodeserti/kanban/
│   │   ├── entity/       # User, Board, BoardColumn, Card, UserToken
│   │   ├── repository/   # Spring Data JPA repositories
│   │   ├── security/     # JWT filter, JwtService, UserDetailsService, Google OAuth handlers
│   │   ├── service/      # AuthService, BoardService, EmailService, UserTokenService, BoardBroadcaster
│   │   ├── controller/   # AuthController, BoardController
│   │   ├── dto/          # Request/response records
│   │   └── config/       # SecurityConfig, AuthBeansConfig, GoogleOAuthConfig, WebSocketConfig, GlobalExceptionHandler, rate limiting
│   ├── src/main/resources/db/migration/  # Flyway migrations
│   └── src/test/java/    # KanbanApiTest, AuthFlowsTest, ProfileManagementTest, GoogleLoginTest, RateLimitTest, WebSocketBroadcastTest
├── frontend/         # React + Vite app
│   ├── src/
│   │   ├── components/   # Board, ColumnItem, CardItem, LoginForm, AccountSettings, Modal, VerifyEmailPage, ResetPasswordPage, OAuthCallbackPage
│   │   ├── context/       # ToastContext, DialogContext (in-app replacements for window.alert/confirm/prompt)
│   │   └── api/          # Axios client (token refresh), realtime.js (STOMP)
│   └── nginx.conf        # SPA routing + /api, /ws and /oauth2 proxy
├── .env.example      # Copy to .env — never commit the real one
└── docker-compose.yml
```

## Notes / possible next steps

- The rate limiter and refresh-token/verification tokens are stored in-process/in the app's own database — fine for a single backend instance; a multi-instance deployment would want a shared store (Redis) for the rate limiter specifically, and a shared message broker (RabbitMQ/ActiveMQ via STOMP relay) instead of the in-memory simple broker for WebSocket fan-out.
- No HTTPS termination is set up here — put a real TLS certificate in front of this (a reverse proxy, load balancer, or platform-managed cert) before exposing it publicly.
- The board is per-user; sharing a board with other users would be the natural next feature — the WebSocket infrastructure (per-board topics, ownership-checked subscriptions) is already built to support it.

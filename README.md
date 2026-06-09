# OAuth 2.0 Authentication Backend (Spring Boot + PostgreSQL)

A production-shaped **Spring Boot 3.4 / Java 21** authentication backend. It issues JWTs
(in HttpOnly cookies) for two login paths:

1. **Social sign-in (SSO)** via OAuth2 / OpenID Connect — **Google works out of the box**;
   Spotify, Apple, and SoundCloud are scaffolded (see [Adding more OAuth providers](#adding-more-oauth-providers)).
2. **Email + password**, with email verification and password reset.

On top of auth it ships role-based access control, refresh-token rotation, rate limiting,
audit logging, internationalized emails, Actuator/Prometheus metrics, and Swagger docs.
Persistence is **PostgreSQL** with a Flyway-managed schema validated against the JPA
entities (`ddl-auto: validate`).

> This is a **backend only** — there is no bundled UI. It expects a separate frontend
> (default `http://localhost:3000`). After a successful OAuth login the backend sets auth
> cookies and 302-redirects the browser to `FRONTEND_URL` + `/home`.

---

## Table of contents

- [Architecture at a glance](#architecture-at-a-glance)
- [Prerequisites](#prerequisites)
- [Quick start (local dev)](#quick-start-local-dev)
- [Configuration & environment variables](#configuration--environment-variables)
- [Setting up Google SSO](#setting-up-google-sso)
- [Adding more OAuth providers](#adding-more-oauth-providers)
- [Email (verification & password reset)](#email-verification--password-reset)
- [How authentication works](#how-authentication-works)
- [Roles & authorization](#roles--authorization)
- [API surface](#api-surface)
- [Running with Docker](#running-with-docker)
- [Testing](#testing)
- [Production checklist](#production-checklist)
- [Project structure](#project-structure)

---

## Architecture at a glance

| Concern            | Choice                                                                 |
|--------------------|------------------------------------------------------------------------|
| Language / runtime | Java 21                                                                |
| Framework          | Spring Boot 3.4.3 (Web, Security, OAuth2 Client, Data JPA, Mail, Actuator) |
| Database           | PostgreSQL 14+ (schema via Flyway, `V1__init_schema.sql`)              |
| Migrations         | Flyway (`flyway-database-postgresql`)                                   |
| Tokens             | JWT (HS256) access + opaque refresh tokens, both stored in HttpOnly cookies |
| Rate limiting      | Bucket4j (per-IP), optionally backed by Redis                          |
| Image build        | Jib (daemonless, no Dockerfile)                                        |
| API docs           | springdoc-openapi (Swagger UI)                                         |
| i18n               | English, German, Spanish, French                                       |

---

## Prerequisites

- **Java 21+** (`JAVA_HOME` pointing at a JDK 21)
- **PostgreSQL 14+**
- **Maven** — use the bundled wrapper (`./mvnw`); no separate install required
- **Docker & Docker Compose** (optional — for containerized runs and the test DB)
- A **Google Cloud OAuth client** if you want social login (see below)
- An **SMTP account** (e.g. Mailtrap for dev) if you want email verification / password reset

---

## Quick start (local dev)

```bash
# 1. Clone
git clone https://github.com/Nootje88/OAuth-PostgreSQL.git
cd OAuth-PostgreSQL

# 2. Start a local PostgreSQL (or use your own)
docker run -d --name oauth-pg \
  -e POSTGRES_DB=oauth_template \
  -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 postgres:16-alpine

# 3. Create your dev env file from the template and fill it in
cp .env.template .env.dev
#   -> edit .env.dev: DB creds, GOOGLE_CLIENT_ID/SECRET, JWT_SECRET, EMAIL_* ...

# 4. Load the env vars and run (dev profile is the default)
set -a; source .env.dev; set +a
./mvnw spring-boot:run
```

Then open:

- API base: <http://localhost:8080>
- Swagger UI: <http://localhost:8080/swagger-ui.html>
- Health: <http://localhost:8080/management/health>

> Flyway runs `V1__init_schema.sql` on first boot to create the schema. Hibernate is set to
> `validate`, so the app refuses to start if the entities and the migrated schema disagree —
> this is intentional and catches drift early.

> ⚠️ **Never commit `.env.*` files** — they hold secrets. `.gitignore` already excludes them.

---

## Configuration & environment variables

Configuration lives in `src/main/resources/application*.yaml` and is driven by environment
variables. Copy `.env.template` per environment (`.env.dev`, `.env.pat`, `.env.prod`) and
populate it. The most important variables:

| Variable | Purpose | Example |
|----------|---------|---------|
| `DB_URL` | JDBC URL to PostgreSQL | `jdbc:postgresql://localhost:5432/oauth_template` |
| `DB_USERNAME` / `DB_PASSWORD` | DB credentials | `postgres` / `postgres` |
| `JWT_SECRET` | HS256 signing key — **min 32 chars**, random | `openssl rand -base64 48` |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Google OAuth client | from Google Cloud Console |
| `ADMIN_EMAILS` | Comma-separated emails auto-granted `ADMIN` on first login | `you@example.com` |
| `FRONTEND_URL` | Allowed CORS origin + OAuth success redirect base | `http://localhost:3000` |
| `LOGIN_SUCCESS_REDIRECT_URL` | Path appended to `FRONTEND_URL` after OAuth login | `/home` |
| `EMAIL_HOST` / `EMAIL_PORT` | SMTP server | `sandbox.smtp.mailtrap.io` / `587` |
| `EMAIL_USERNAME` / `EMAIL_PASSWORD` | SMTP credentials | — |
| `EMAIL_FROM_ADDRESS` / `EMAIL_FROM_NAME` | "From" header on outgoing mail | `no-reply@yourdomain.com` |
| `APP_BASE_URL` | Public base URL used in email links | `http://localhost:3000` |
| `REDIS_HOST` / `REDIS_PORT` | Optional Redis for rate limiting | `localhost` / `6379` |
| `SERVER_PORT` | HTTP port | `8080` |

Token lifetimes and cookie behavior are set per profile (not via env):

| Setting | dev | prod |
|---------|-----|------|
| Access token TTL | 1 hour | 30 minutes |
| Refresh token TTL | 7 days | 30 days |
| Cookie `Secure` | `false` | `true` |
| Cookie `SameSite` | `Lax` | `None` (cross-site) |
| Cookie domain | (none) | `.yourdomain.com` |

---

## Setting up Google SSO

Google is the reference provider and is the one wired in `application.yaml` out of the box.

1. Go to the [Google Cloud Console](https://console.cloud.google.com/) → **APIs & Services → Credentials**.
2. Configure the **OAuth consent screen** (External is fine for testing).
3. Create an **OAuth client ID** of type **Web application**.
4. Add an **Authorized redirect URI**:
   - Dev: `http://localhost:8080/login/oauth2/code/google`
   - Prod: `https://your-api-domain.com/login/oauth2/code/google`
   - (The path is `{baseUrl}/login/oauth2/code/{registrationId}` — Spring's standard callback.)
5. Copy the **Client ID** and **Client Secret** into `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`.
6. Restart the app. Your frontend starts the flow by sending the browser to:

   ```
   GET http://localhost:8080/oauth2/authorization/google
   ```

   (The backend also exposes `GET /auth/login-url`, which returns this path.)

The Google registration requests the `openid`, `profile`, and `email` scopes — the user's
email is the account key.

---

## Adding more OAuth providers

The codebase carries **scaffolding** for Spotify, Apple, and SoundCloud — the `AuthProvider`
enum values, dedicated `*_id` columns on the `users` table, `.env.template` placeholders, and
the `UserService` mapping all exist. **They are not active yet:** only Google has a
`spring.security.oauth2.client.registration` entry, and the success flow currently assumes an
**OpenID Connect** (OIDC) provider.

To enable another provider:

1. Add a `registration` (and, for non-Google providers, a `provider`) block under
   `spring.security.oauth2.client` in `application.yaml`, wiring its `*_CLIENT_ID` /
   `*_CLIENT_SECRET` env vars.
2. **Google & Apple are OIDC** — they fit the existing `OidcUser` success handler. **Spotify
   and SoundCloud are plain OAuth2** (no `openid` scope), so they additionally need a custom
   `OAuth2UserService` to map their userinfo response onto a `User`; the current
   `OAuth2SuccessHandler` only handles `OidcUser` principals.
3. Add the provider's authorized redirect URI:
   `{baseUrl}/login/oauth2/code/{registrationId}`.

In short: Google is turn-key; the others are a deliberate extension point, not a finished
integration.

---

## Email (verification & password reset)

Email/password accounts are created **disabled** and must verify before they can log in.

- **Register** → `POST /auth/register` sends a verification email.
- **Verify** → the link hits `GET /auth/verify-email?token=…`, enables the account, and
  redirects to `APP_BASE_URL/login?verified=1`.
- **Resend** → `POST /auth/resend-verification`.
- **Forgot password** → `POST /auth/forgot-password` emails a reset link.
- **Reset** → `POST /auth/reset-password` with the token + new password.

SMTP is configured via `spring.mail.*` (the `EMAIL_*` env vars). The dev profile defaults to
**Mailtrap's sandbox** so you can inspect mail without sending real messages. Email subjects
and bodies are internationalized (`src/main/resources/i18n/` + Thymeleaf templates under
`templates/email/`).

---

## How authentication works

Both login paths converge on the same token model:

1. **Authenticate** — either via OAuth2 (`/oauth2/authorization/google`) or
   `POST /auth/email-login`.
2. The backend issues a short-lived **JWT access token** (cookie `jwt`, path `/`) and an
   **opaque refresh token** (cookie `refresh_token`, path `/refresh-token`). Both are
   **HttpOnly**; `Secure`/`SameSite`/`Domain` follow the active profile.
3. **Authenticated requests** carry the `jwt` cookie; `JwtAuthenticationFilter` validates it
   and populates the security context.
4. **Refresh** — when the access token expires, `POST /refresh-token` (sending the
   `refresh_token` cookie) mints a new access token and rotates the refresh token.
5. **Logout** — `POST /auth/logout` clears both cookies and revokes the user's refresh tokens.

Other security defaults:

- **CSRF** protection is on (cookie-based), but **ignored** for `/auth/**`, `/oauth2/**`,
  `/login/oauth2/**`, and `/refresh-token` (token endpoints that don't rely on session cookies).
- **CORS** allows only the origins in `app.cors.allowed-origins` (`FRONTEND_URL`).
- Sessions are **stateless**; a Content-Security-Policy header is set.
- Unauthenticated access to a protected endpoint returns **401** (no redirect to a login page).

---

## Roles & authorization

Four roles exist: `USER`, `ADMIN`, `MODERATOR`, `PREMIUM`. New users get `USER`; any email
listed in `ADMIN_EMAILS` also gets `ADMIN` on first login. URL-level rules (from
`SecurityConfig`):

| Path prefix | Required role |
|-------------|---------------|
| `/api/admin/**` | `ADMIN` |
| `/api/moderator/**` | `ADMIN` or `MODERATOR` |
| `/api/premium/**` | `ADMIN` or `PREMIUM` |
| `/api/user/**`, `/api/profile/**` | any authenticated user |
| `/management/**` (except `/health`, `/info`) | `ADMIN` |

---

## API surface

Public (no auth) endpoints are under `/auth/**`, plus Swagger and `/management/{health,info}`.
Highlights — see Swagger UI for the full, live contract.

**Authentication — `/auth`**

| Method | Path | Description |
|--------|------|-------------|
| POST | `/auth/register` | Register an email/password account |
| GET  | `/auth/verify-email?token=` | Verify email, enable account |
| POST | `/auth/resend-verification` | Resend verification email |
| POST | `/auth/forgot-password` | Start password reset |
| POST | `/auth/reset-password` | Complete password reset |
| POST | `/auth/email-login` | Email/password login (sets cookies) |
| POST | `/auth/logout` | Clear cookies, revoke refresh tokens |
| GET  | `/auth/user` | Current user from the JWT |
| GET  | `/auth/login-url` | Returns the Google OAuth start URL |
| GET  | `/oauth2/authorization/google` | Begin Google SSO |
| POST | `/refresh-token` | Rotate access token via refresh cookie |

**User & profile**

| Method | Path | Role |
|--------|------|------|
| GET | `/api/user/profile` · `/api/user/{email}` | authenticated |
| GET/PUT | `/api/profile` | authenticated (get / update profile) |
| PUT | `/api/profile/notifications` | authenticated |
| POST | `/api/profile/picture` | authenticated (multipart upload) |
| GET | `/api/profile/history` | authenticated |
| POST/GET | `/api/language/{change,available,current}` | authenticated |

**Admin / moderator / premium**

| Method | Path | Role |
|--------|------|------|
| POST | `/api/admin/assign-role` · `/api/admin/remove-role` | `ADMIN` |
| GET | `/api/admin/users` | `ADMIN`/`MODERATOR` |
| GET | `/api/admin/audit/**` | `ADMIN` |
| GET | `/api/admin/monitoring/**` | `ADMIN` |
| GET | `/api/moderator/users` | `ADMIN`/`MODERATOR` |
| GET | `/api/premium/content` | `ADMIN`/`PREMIUM` |

---

## Running with Docker

The application image is built **daemonlessly with Jib** (no Dockerfile). Build once, then
bring an environment up with Compose:

```bash
# Build the image into the local Docker daemon
./mvnw -DskipTests package jib:dockerBuild     # produces oauth-postgresql:<version> + :latest

# Bring up the dev stack (app + PostgreSQL + Redis)
SPRING_PROFILES_ACTIVE=dev docker-compose \
  -f docker/compose/docker-compose.yml \
  -f docker/compose/docker-compose.dev.yml up -d
```

Or use the helper, which builds the image and starts the stack:

```bash
docker/scripts/deploy.sh dev    # or: pat | prod
```

`pat` and `prod` use their respective `docker-compose.<env>.yml` overlays. To push to a
registry instead of the local daemon, use `./mvnw compile jib:build`.

---

## Testing

All Spring-backed tests (`*Test` slice/context tests and `*IT` end-to-end tests) run against
a **real PostgreSQL** — not H2 or Testcontainers. Start a throwaway test DB on port **5433**
first:

```bash
docker run -d --name oauth-pg-test \
  -e POSTGRES_DB=oauth_template_test \
  -e POSTGRES_USER=test -e POSTGRES_PASSWORD=test \
  -p 5433:5432 postgres:16-alpine
```

The `test` profile defaults to `jdbc:postgresql://localhost:5433/oauth_template_test`
(override with `TEST_DB_URL` / `TEST_DB_USERNAME` / `TEST_DB_PASSWORD`).

```bash
./mvnw test                         # unit + Spring slice/context tests (Surefire)
./mvnw verify                       # the above + *IT integration tests (Failsafe)
./mvnw test -Dtest=UserServiceTest  # a single test
```

`docker/scripts/deploy.sh test` provisions the test DB, runs `./mvnw verify`, and tears the
DB down afterward.

---

## Production checklist

- [ ] `JWT_SECRET` is strong, random (≥ 32 chars), and stored in a secrets manager
- [ ] `DB_*` point at a managed PostgreSQL with TLS (`sslmode=require`)
- [ ] Cookies are `Secure` + `SameSite=None` and scoped to your domain (prod profile does this)
- [ ] Serve over HTTPS only; set the OAuth redirect URIs to your real API domain
- [ ] `FRONTEND_URL` / CORS origins list only your real frontend origins
- [ ] OAuth client secrets and SMTP credentials come from a secrets manager, not `.env` files
- [ ] `.env.*` files are never committed

> **CI/CD:** there is no pipeline in this template by design (see
> `docs/adr/0002-no-cicd-for-now.md`). When you wire one up, the integration tests need a
> reachable PostgreSQL on `localhost:5433` (or `TEST_DB_URL` pointed elsewhere) — they do not
> self-provision a database (see `docs/adr/0003-testcontainers-postgres-for-tests.md`).

---

## Project structure

```
src/main/java/com/template/OAuth/
├── config/        Security, JWT, OAuth2 handlers, CORS, properties
├── controller/    REST endpoints (auth, profile, admin, premium, …)
├── dto/           Request/response DTOs
├── entities/      JPA entities (User, RefreshToken, AuditEvent, …)
├── enums/         Role, AuthProvider, AuditEventType, NotificationType, …
├── repositories/  Spring Data JPA repositories
├── security/      JwtAuthenticationFilter, CustomUserDetailsService
├── service/       Auth, User, Email, Audit, Metrics, RateLimit, RefreshToken
├── aspect/        AOP auditing
├── filter/        Rate-limiting / auth filters
└── health/        Custom health indicators
src/main/resources/
├── application*.yaml   Per-profile config (dev / pat / prod / test)
├── db/migration/       Flyway migrations (V1__init_schema.sql)
├── i18n/               messages_*.properties (en, de, es, fr)
└── templates/email/    Thymeleaf email templates
docs/adr/               Architecture Decision Records
docker/                 Compose stacks + deploy script (images via Jib)
```

See `docs/adr/` for the rationale behind the PostgreSQL conversion, the Jib image story, and
the testing strategy.

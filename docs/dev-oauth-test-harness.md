# Dev OAuth test harness — manual login runbook

A dev-profile-only page to eyeball that a real **Google** / **Microsoft** sign-in round-trips
end-to-end. The automated suite proves everything except the interactive consent screen (a real
password + MFA, which a headless build can't drive); this page is where a human closes that gap.

- **Page:** `GET http://localhost:8080/dev/test-login` (served only under the `dev` profile by
  `DevTestLoginController`; it 404s in `test`/`prod`).
- **What it does:** two login buttons (`/oauth2/authorization/google`,
  `/oauth2/authorization/microsoft`) plus a panel that reads `/api/user/profile` and
  `/api/user/sessions` using the issued `jwt` cookie, and a logout button.

## Microsoft (live — credentials already provisioned)

An Entra app registration was created for local testing (audience: **any Microsoft account** =
the `common` wiring):

- **App registration:** `OAuth-PostgreSQL-Template-Dev`
- **Application (client) ID:** `22b80180-96eb-462b-a57e-1958b0c1672c`
- **Tenant:** DimoTools (`8681bb15-d8cd-47b0-bd76-4350a121aac5`)
- **Redirect URI registered:** `http://localhost:8080/login/oauth2/code/microsoft`
- **Client secret:** stored locally in `.env.microsoft.local` (gitignored — never committed).
  Secrets expire in 1 year; rotate with `az ad app credential reset --id <appId>`.

### Run it

```bash
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.11/libexec/openjdk.jdk/Contents/Home
# Microsoft creds (client-id + secret) — keep these out of the shell history / repo:
set -a; source .env.microsoft.local; set +a
# Local Postgres for the dev datasource (port 5432) must be reachable.
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Then open **http://localhost:8080/dev/test-login** and click **Login with Microsoft**. Sign in
with any Microsoft account, approve consent. You should land back on the harness showing your
`User` (email, `primaryProvider: MICROSOFT`) and one active session.

> If the browser briefly bounces to a blank `localhost:3000` after login, just reopen
> `http://localhost:8080/dev/test-login` — the `jwt` cookie is already set (cookies ignore port),
> so the panel will show you logged in.

## Google (creds pending)

Google login is wired but ships with a dummy client-id. To test it live, set real Google Cloud
OAuth creds before running:

```bash
export GOOGLE_CLIENT_ID=...        # from Google Cloud Console → Credentials → OAuth client
export GOOGLE_CLIENT_SECRET=...
```

and register `http://localhost:8080/login/oauth2/code/google` as an authorized redirect URI.

## Single-tenant override (production note)

The shipped Microsoft wiring uses the multi-tenant `common` endpoints (any Microsoft account). To
restrict to one organisation, point `spring.security.oauth2.client.provider.microsoft.*` at
`/<tenant-id>/` instead of `/common/` and set `issuer-uri`. See ADR-0009.

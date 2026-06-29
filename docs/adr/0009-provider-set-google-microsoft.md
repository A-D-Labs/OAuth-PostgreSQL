# Provider set rationalized to Google + Microsoft (audience `common`)

**Status:** accepted (2026-06-29) · v1 provider surface. Supersedes the inherited four-provider scaffolding.

The inherited template advertised **four** social providers — the `AuthProvider` enum had `GOOGLE`, `SPOTIFY`, `APPLE`, `SOUNDCLOUD`; `User` carried an id column and a unique constraint for each; `UserService` mapped all four — but only **Google** was actually wired into the login flow. The other three were half-implemented: a clone-er of this template would see `AuthProvider.APPLE`, assume Apple login worked, and discover at runtime that no client registration existed. For a deliberately-generic template (CONTEXT.md mission, 2026-06-09) the code contradicting its own domain model is the one real defect worth fixing, ahead of any new feature depth.

## Decision

- **Ship exactly two fully-wired providers: `GOOGLE` and `MICROSOFT`** (plus `LOCAL`). Remove the Spotify/Apple/SoundCloud scaffolding entirely — enum values, `User` id columns + unique constraints, `UserService` mapping branches, and the schema (migration `V7`). Every provider the template names now actually works.
- **Microsoft uses the multi-tenant `common` audience.** Any Microsoft account — personal (`@outlook`/`@hotmail`) or work/school — can sign in. This mirrors the existing Google wiring ("any Google account") and keeps the template generic. A downstream consumer who must lock login to one organisation switches the provider endpoints from `/common/` to `/<tenant-id>/` and sets `issuer-uri` — a documented one-place override, not a code change.
- **Microsoft is configured with static `common` endpoints, not `issuer-uri`.** `issuer-uri` triggers a blocking OIDC discovery call at context startup, which the headless test box (no outbound network) cannot make and which CI would fail on. Static `authorization-uri`/`token-uri`/`jwk-set-uri`/`user-info-uri` let the Spring context boot offline; the JWKS is fetched lazily on first real login.

## Trade-off accepted: `common` issuer validation

The `common` endpoint's ID-token `iss` claim is **tenant-templated** (`https://login.microsoftonline.com/<tenantid>/v2.0`), not the literal `common` URL. Because we configure static endpoints without `issuer-uri`, Spring's `OidcIdTokenValidator` does not enforce `iss`-equality (it still requires `iss`, `sub`, `aud`, `exp`). This is the correct behaviour for a multi-tenant default — pinning a single issuer would reject other tenants. Consumers who need strict `iss` validation should run single-tenant (set `issuer-uri` to their tenant) or add a custom `OAuth2TokenValidator` that accepts the templated issuer. This is documented rather than built, because the template ships generic and the validation choice belongs to the consuming app.

## Consequences

- The persisted surface matches the domain model: `users` has `google_id` + `microsoft_id` only; `primary_provider` CHECK is `('GOOGLE','MICROSOFT','LOCAL')`.
- Migration `V7` is **destructive** (drops three columns) — acceptable because the template carries no production data; `V1` is immutable so the change is a forward migration.
- Apple OIDC client-secret rotation (previously a deferred concern in #10) is now moot — Apple is removed.
- Adding a future provider is a known recipe: enum value + `User` id column + migration + `UserService` branch + a registration/provider block (built-in providers like Google need only client-id/secret; non-built-in ones like Microsoft also need explicit provider endpoints).

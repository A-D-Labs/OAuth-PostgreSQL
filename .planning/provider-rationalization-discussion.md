# Discussion — v1 scope lock & provider rationalization (2026-06-29, W27)

**Session:** cc-bridge `57356616` (discussion/grill only — Stage 1 of hl-dev-flow, no build).
**Status:** design tree fully resolved. **NOT committed** pending Diangelo's go-ahead.
**Outcome in one line:** the security base is *done* (arguably over-built for a template); the only real defect was a half-wired 4-provider surface, which we resolve by **dropping Spotify/Apple/SoundCloud and wiring Microsoft alongside Google**.

---

## 1. Where the codebase actually is (corrects the session brief)

The brief framed #48/#49/#50 as open work to ship. They are **already closed and merged** — dev HEAD is `655c18b`, not `60b2d01`. Verified during prep:

- **Shipped & solid:** MFA/TOTP + recovery codes (ADR-0005), per-user revocation epoch + `jti` (ADR-0006), refresh absolute-lifetime cap, per-session multi-device with `GET/DELETE /api/user/sessions` (#47/#48, ADR-0007), token-reuse detection with **full token-family revocation** (`TokenReuseException`, `findByPreviousToken`, `RefreshTokenReuseIT`), proxy-aware self-evicting rate-limiting with a profile-activated **Redis** backend (#49/#50, ADR-0008), GHA CI on real Postgres, Spring Boot 3.5.14 (34 CVEs cleared). **58 unit + 32 integration tests green.**
- **Only open issue:** #10 (deferred-work tracker) — and it is largely **stale**: reuse-detection, multi-device, distributed rate-limit, and CI are all now done.
- **Rollout note:** #48–50 were also promoted `dev → test` this session. The **`test → main` promotion remains gated on Diangelo** and was deliberately not attempted. Nothing on #48–50 is left to "ship" or "continue" except that gated promotion.

## 2. The honest assessment (the grill)

Against the **locked mission** (CONTEXT.md, 2026-06-09): *"a template, not a product; a faithful MySQL→Postgres conversion; deliberately generic."* Through that lens most of the robustness roadmap is well-executed **scope-creep**. So the recommendation was **not** "add more security" — it's complete — but: *is the surface coherent?*

The one incoherence: the domain model and code **half-implement four providers**. `AuthProvider` enum and `UserService.determineAuthProvider`/`setProviderId` handle GOOGLE/SPOTIFY/APPLE/SOUNDCLOUD, and `User` carries `googleId`/`spotifyId`/`appleId`/`soundcloudId` (with unique constraints in `V1`), **but only Google is wired** in `SecurityConfig`/`application.yaml`. In a template that's a footgun: a clone-er sees `AuthProvider.APPLE` and assumes Apple works.

## 3. Decisions locked (this session)

| # | Decision | Outcome |
|---|----------|---------|
| Scope | Add security depth, or stop? | **Stop.** Security base is complete/over-complete for a template. No further depth. ("this is so good now.") |
| Providers | Resolve the half-wired 4-provider surface | **Drop Spotify, Apple, SoundCloud entirely. Add Microsoft as a second standard provider alongside Google.** Ship Google + Microsoft as the two wired providers. |
| Microsoft audience | Which Microsoft accounts may log in? | **`common`** — any Microsoft account (personal + any organisation). Mirrors today's "any Google account" wiring; keeps the template generic; a downstream consumer locks to their own org by switching one registration value to single-tenant. No tenant ID hard-coded into the template. |
| #48–50 | Status | Already merged + on `test`. Not re-opened. `test→main` stays gated on Diangelo (separate decision). |

## 4. Design of the provider change (for the build session)

A clean swap, not additive. Touch points:

- **Enum** `AuthProvider`: remove `SPOTIFY`, `APPLE`, `SOUNDCLOUD`; add `MICROSOFT`. Keep `GOOGLE`, `LOCAL`.
- **`User` entity:** drop `spotifyId`, `appleId`, `soundcloudId`; add `microsoftId` (mirror `googleId`, unique).
- **`UserService`:** `determineAuthProvider` — replace the spotify/apple/soundcloud branches with a `microsoft` branch (match on `"microsoft"` in provider name). `setProviderId` switch — same swap.
- **Migration `V7`** (destructive — acceptable, template has no real data): drop `spotify_id`/`apple_id`/`soundcloud_id` columns and their `uk_users_spotify`/`uk_users_apple`/`uk_users_soundcloud` constraints; add `microsoft_id VARCHAR(255)` + `uk_users_microsoft UNIQUE (microsoft_id)`. (Cannot edit the already-applied `V1`.)
- **`SecurityConfig` / `application.yaml`:** add a Microsoft `oauth2Login` client registration (Entra ID OIDC, `common` issuer `https://login.microsoftonline.com/common/v2.0`), env-driven client-id/secret like Google. Document the single-tenant override.
- **Docs to update during build:** CONTEXT.md "AuthProvider" term (line ~16: `LOCAL`, `GOOGLE`, `MICROSOFT`); a short ADR-0009 ("Provider set rationalized to Google + Microsoft; audience `common`") — hard-to-reverse + surprising-without-context + a real trade-off, so it clears the ADR bar.
- **Tests:** adapt `OAuth2ProviderLoginIT` / any provider-mapping unit tests to Microsoft; keep all existing suites green.

## 5. Explicitly out of scope / frozen

No further MFA/revocation/session/rate-limit depth. No Apple secret rotation (Apple is being removed). No observability (Tier 3c). Error-handling polish (the 8 `catch (Exception)` sites in #10) stays a backlog item, **not** a v1 blocker. `test→main` promotion is a separate, Diangelo-gated decision.

---

*Resolved via cc-bridge grill, two questions, 2026-06-29. Build of §4 is a separate session and was not started here.*

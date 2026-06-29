# PRD — Provider rationalization: Google + Microsoft

**Sprint:** `sprint:2026-W27` · **feat slug:** `feat:provider-rationalization` · **Source:** `.planning/provider-rationalization-discussion.md` (cc-bridge grill, 2026-06-29)
**Status:** spec locked via grill. Advance to `/to-issues`.

## Problem Statement

A developer cloning this OAuth template is misled by its provider surface. The code advertises **four** social providers — the `AuthProvider` enum lists `GOOGLE`, `SPOTIFY`, `APPLE`, `SOUNDCLOUD`; `User` carries an id column for each; `UserService` maps all four — but only **Google** is actually wired into the login flow. The other three are half-implemented traps: a clone-er sees `AuthProvider.APPLE`, assumes Apple login works, and discovers only at runtime that no client registration exists. Separately, the template omits **Microsoft** login, one of the two most common enterprise/consumer identity providers, so anyone needing it has to add it from scratch.

## Solution

Rationalize the provider set to exactly **two fully-wired, standard providers: Google and Microsoft.** Remove Spotify, Apple, and SoundCloud entirely (enum, entity columns, mapping, schema). Wire Microsoft (Entra ID / Microsoft account) as a first-class provider mirroring how Google is wired today, using the multi-tenant **`common`** audience so any Microsoft account can sign in out of the box. The template becomes *honest* — every provider it names actually works — and *more useful* — it ships with the two providers most projects actually want.

## User Stories

1. As a developer cloning this template, I want every provider named in `AuthProvider` to actually work, so that I am not misled into assuming a half-wired provider is functional.
2. As a developer, I want Microsoft login wired alongside Google, so that I can offer the two mainstream identity providers without writing integration code.
3. As an end user with a Google account, I want to keep logging in with Google exactly as before, so that the change is non-regressive for existing flows.
4. As an end user with any Microsoft account (personal `@outlook`/`@hotmail` or a work/school account), I want to sign in with Microsoft, so that I can use my existing identity.
5. As a first-time Microsoft user, I want a `User` created on first login with my Microsoft identity recorded, so that subsequent logins recognise me as the same User.
6. As a returning Microsoft user, I want to be matched to my existing User by my Microsoft identity, so that I am not duplicated.
7. As a User who signed up with Google, I want my Primary Provider to remain Google, so that my account's main login method is unchanged.
8. As a developer, I want the `User` schema to carry only the provider id columns that are real (`google_id`, `microsoft_id`), so that the database reflects the actual provider set with no dead columns.
9. As a developer, I want a Flyway migration to perform the column swap, so that an existing database upgrades cleanly to the new provider set.
10. As a developer who needs to lock login to my own Microsoft organisation, I want documentation on switching the Microsoft audience from `common` to single-tenant, so that I can restrict access without guessing.
11. As a security reviewer, I want the Microsoft token validated against the documented `common` issuer, so that only genuine Microsoft-issued tokens are trusted.
12. As a developer, I want the existing OAuth integration test adapted to cover Google and Microsoft, so that both wired providers are exercised by CI.
13. As a developer, I want no reference to Spotify/Apple/SoundCloud left in code, config, or schema, so that the template carries no dead provider scaffolding.
14. As an operator, I want all existing test suites (58 unit + 32 integration) to stay green, so that the provider swap demonstrably introduces no regression elsewhere.
15. As a maintainer, I want the domain glossary (CONTEXT.md) and an ADR to record the rationalized provider set, so that the decision is discoverable to future readers.

## Implementation Decisions

**Modules built/modified** (small, vertical change — one deep concern: "the provider set"):

- **`AuthProvider` enum** — remove `SPOTIFY`, `APPLE`, `SOUNDCLOUD`; add `MICROSOFT`. Keep `GOOGLE`, `LOCAL`.
- **`User` entity** — drop `spotifyId`, `appleId`, `soundcloudId`; add `microsoftId` (unique, mirrors `googleId`).
- **`UserService`** — `determineAuthProvider` and `setProviderId`: replace the three removed branches with a single `MICROSOFT` branch (provider-name match on `"microsoft"`). Default-to-Google fallback preserved.
- **Migration `V7__provider_rationalization.sql`** — drop columns `spotify_id`, `apple_id`, `soundcloud_id` and their unique constraints (`uk_users_spotify`, `uk_users_apple`, `uk_users_soundcloud`); add `microsoft_id VARCHAR(255)` + `uk_users_microsoft UNIQUE (microsoft_id)`. (`V1` is already applied and immutable, hence a new migration. Destructive column drop is acceptable — template carries no production data.)
- **`SecurityConfig` + `application.yaml`** — add a Microsoft `oauth2Login` client registration: Entra ID OIDC, issuer `https://login.microsoftonline.com/common/v2.0`, env-driven client-id/secret in the same pattern as Google. No hard-coded tenant ID.
- **Docs** — update CONTEXT.md `AuthProvider` term to `LOCAL`, `GOOGLE`, `MICROSOFT`; add **ADR-0009** (provider set rationalized to Google + Microsoft; Microsoft audience `common`).

**Decisions locked in grill:** provider set = Google + Microsoft only; Microsoft audience = `common` (any Microsoft account), chosen to mirror the current "any Google account" wiring and keep the template generic — downstream consumers override to single-tenant via one registration value. Security depth elsewhere (MFA, revocation, sessions, rate-limit) is frozen; no additions.

## Testing Decisions

- **Good test = external behaviour only.** Assert on login outcome, User creation/matching, and persisted provider identity — not on private mapping internals.
- **Adapt `OAuth2ProviderLoginIT`** (the existing provider-login integration test) to cover **Google and Microsoft**: first-login creates a User with the right provider id; second login matches the same User; Primary Provider recorded correctly. Prior art: the existing Google path in that IT.
- **Adapt `UserService` provider-mapping unit tests** to the new enum (Microsoft maps correctly; removed providers no longer referenced).
- **Regression guard:** full existing suite (58 unit + 32 IT) must stay green on real Postgres in CI. No new MFA/session/rate-limit tests — those surfaces are unchanged.
- **Migration test:** schema-validation IT (`SchemaValidationIT`) must pass against the `V7` schema (microsoft_id present; three dropped columns absent).

## Out of Scope

- Any new security depth (MFA, revocation, multi-device, rate-limit) — explicitly frozen ("this is so good now").
- Apple OIDC client-secret rotation — moot; Apple is being removed.
- Observability / Tier 3c.
- Error-handling polish (the `catch (Exception)` sites in #10) — stays a backlog item, not a v1 blocker.
- `test → main` promotion — separate, Diangelo-gated decision, untouched here.
- Provider *linking* (one User holding both Google and Microsoft links) — not introduced; out of scope unless raised later.

## Further Notes

This change makes the template self-consistent against its own domain model (CONTEXT.md). It is deliberately small and vertical: enum → entity → migration → service → config → docs/tests, buildable as one or two tracer-bullet issues. The destructive `V7` migration is the only irreversible element and is safe for a data-less template.

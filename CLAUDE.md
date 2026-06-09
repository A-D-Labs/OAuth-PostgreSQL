# OAuth-PostgreSQL — Claude Code instructions

## Project at a glance

- **What:** TBD. Mirror copy of `Nootje88/OAuth` (Spring Boot Maven backend template), now to be paired with PostgreSQL and evolved independently. Mission to be locked in `/grill-with-docs`.
- **Origin:** mirror-pushed from `Nootje88/OAuth` on 2026-06-09. Full git history preserved. **No upstream fork relationship.**
- **Audience:** TBD (personal-dev for now; may be promoted to a Dimoit `product` if grill outcomes warrant).
- **Project map (canonical):** `~/.openclaw/workspace/projects/oauth-postgresql/README.md`.

## How work is driven

This repo is driven by **cc-bridge headless sessions**. A human is *not* watching live.

- Eyrie acts as orchestrator. The cc-bridge `/cc-bridge/<id>` panel surfaces three things:
  1. **Decisions** — your `questions/<ts>.json` files
  2. **Waves** — your `.planning/waves.json` file
  3. **Subagents** — auto-extracted from your Task/Agent calls
- When you need a human decision, you DO NOT prompt stdin. You write a question JSON to the bridge session dir's `questions/`, then call `mcp__openclaw__sessions_yield`. The orchestrator wakes you with the answer.
- See `.claude/skills/hl-dev-flow/SKILL.md` for the full protocol.

## Branch policy

- `feature/*` → push freely, agent self-merges feature → `dev` PRs.
- `dev` → push freely, agent merges its own PRs into dev.
- `test`, `main`, `master` → **denied by `.claude/settings.json`**. Diangelo gates promotions to these.

This repo's initial dev branch was branched off `main` (which holds the upstream template). Work happens on feature branches off `dev`.

## Inherited template (current main)

The mirrored repo is a Java + Spring Boot + Maven OAuth template:

- `pom.xml` — Maven build
- `src/main/java/…` — Spring Boot OAuth scaffold
- `azure/`, `azure-pipelines.yml` — Azure DevOps Pipelines (NOT GitHub Actions — predates the standard Dimoit `product` stack)
- `docker/` — Docker assets
- Other branches: `ITtests`, `Unit-testing-and-docker` — work-in-progress on integration tests and docker/unit-testing
- Tag: `Template`

**Do not assume any of this is the final stack.** Mission/stack/CI choices are TBD; the grill decides.

## Stack guidance (defer to grill outcomes)

No final stack locked yet. Pragmatic defaults if forced to pick:

- **Backend:** Java 21 + Spring Boot 3.x + Maven (inherited; reasonable to keep). JIB for containers if this becomes a Dimoit `product`.
- **DB:** PostgreSQL — implied by the project name. Flyway for migrations.
- **CI/CD:** TBD. Azure DevOps Pipelines is what the template ships with; if this becomes a Dimoit `product`, the standard is GitHub Actions → Azure CLI deploy, swap or keep.
- **Frontend:** none yet. Add only if grill decides this needs a UI.
- **Auth:** the whole point — OAuth flows. Grill should clarify which providers, which grant types, and what the consuming app looks like.

The hl-dev-flow `/grill-with-docs` stage will lock these. Don't pre-commit code.

## What to do right now

1. Read this file. Read `README.md` (the inherited template one). Read `HELP.md`.
2. Read the project map: `~/.openclaw/workspace/projects/oauth-postgresql/README.md` — mission is TBD there too; grill is the way to lock it.
3. Read the inherited code surface so you understand what the template already does: `pom.xml`, `src/main/java`, `src/main/resources`.
4. Invoke `/hl-dev-flow` and start at stage 1 (`/grill-with-docs`). The grill should lock:
   - **Mission** — what's this codebase actually for?
   - **Target stack** — keep Java/Spring or pivot? Keep Azure Pipelines or move to GHA?
   - **DB schema scope** — what does Postgres hold (users, refresh tokens, sessions, …)?
   - **Consuming app(s)** — what calls this OAuth backend?
   - **Branch/CI policy** — confirm the standard `dev`/`test`/`main` rollout applies (it does for `product`; not necessarily for `personal-dev`).
5. Write each decision as a `questions/<ts>.json` to the bridge session dir. Yield. Wait. Continue.

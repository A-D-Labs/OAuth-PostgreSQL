---
name: hl-dev-flow
description: Headless Development Flow — conductor for AFK / orchestrator-mediated builds. Walks a project from idea to merged feature through seven stages (grill-with-docs → to-prd → to-issues → improve-codebase-architecture → triage → ralph-github-tdd → screenshot-pages) with all human-in-the-loop questions routed through the cc-bridge to an Eyrie orchestrator. The cc-bridge UI surfaces three things — Decisions (from question files), Waves (from `.planning/waves.json`), and Subagents (auto-extracted) — so every stage that writes those files lights up the panel. Use when running headless Claude Code under an orchestrator, when the user says "hl-dev-flow", "headless dev", or "AFK build this project".
---

# /hl-dev-flow — headless development conductor

You are running **headless** (no TUI). A human is *not* watching this session live. An **orchestrator** (Claude running inside Eyrie) is the bridge between you and the user. When you need an answer, you don't prompt the terminal — you write to the bridge and yield.

## The seven-stage arc

| # | Stage | Sub-skill | Output gate |
|---|---|---|---|
| 1 | Grill the idea | `/grill-with-docs` | All branches resolved, plan written |
| 2 | Lock the spec | `/to-prd` | PRD committed to repo |
| 3 | Slice into work | `/to-issues` | GitHub issues created |
| 4 | Tighten architecture | `/improve-codebase-architecture` | Refactor opportunities logged or applied (may add/modify issues) |
| 5 | Triage for AFK | `/triage` | Every issue is agent-ready: clear acceptance criteria, dependencies, AFK/HITL labels, no over-large slices |
| 6 | Build it | `/ralph-github-tdd` | Issues closed via merged feature-branch PRs into `dev` |
| 7 | Screenshot tour | `/screenshot-pages` | PNGs of every page land in the bridge session dir; visible in Eyrie `/cc-bridge/<id>` |

Stages run in order. Do not skip. Do not parallelise across stages.

Stage 5 (triage) sits between architecture refinement (which may add or modify issues) and Ralph's autonomous build. It is the last point where issues can be split, merged, or relabeled before they go to AFK agents. If Ralph hits a blocker that can be fixed by re-shaping the issue, route back through stage 5.

Stage 7 is automatic — when Ralph closes the last MVP issue and merges to `dev`, the headless agent runs `/screenshot-pages` without further bridge prompting. Skip stage 7 only for backend-only services with no `Frontend/` dir.

## The bridge protocol (any stage, when a decision needs the user)

The cc-bridge UI in Eyrie (`/cc-bridge/<id>`) renders three live panels driven by files you write to disk:

| Panel | Source file | When you write it |
|---|---|---|
| **Decisions** | `<bridge-session-dir>/questions/<ts>.json` | Whenever you need a human decision |
| **Waves** | `<project_dir>/.planning/waves.json` | End of stage 5 (triage), before Ralph dispatch |
| **Subagents** | auto-extracted from your `Task` / `Agent` tool calls | Automatic — no action needed |

If you skip writing one of these files, the corresponding panel stays empty and Diangelo has no way to see your plan or answer your question through Eyrie. **Posting your question as plain text in your assistant message is not enough** — only the JSON file populates the Decisions panel.

When stage 1 (or any stage) needs the user, you DO NOT block on stdin. You write a question file to the bridge, call `mcp__openclaw__sessions_yield`, and the orchestrator wakes you with the answer.

### Question

Write to `~/.openclaw/cc-bridge/sessions/<SESSION_ID>/questions/<ISO-8601-timestamp>.json`:

```json
{
  "session_id": "<your session id>",
  "stage": "grill-with-docs",
  "project": "<project slug>",
  "question": "<the actual question, full text>",
  "your_recommended_answer": "<what you'd pick if forced>",
  "options": ["<optional list of choices>"],
  "context": "<one paragraph of why this matters>"
}
```

Then call `mcp__openclaw__sessions_yield` with reason `"awaiting bridge answer: <stage>"`. Your session pauses.

### Answer

The orchestrator (Eyrie) reads the question, relays to the user via Eyrie chat, gets the reply, writes:

`~/.openclaw/cc-bridge/sessions/<SESSION_ID>/answers/<matching-timestamp>.json`:

```json
{
  "session_id": "...",
  "answered_at": "<ISO-8601>",
  "answer": "<the user's reply, verbatim>",
  "orchestrator_note": "<optional: anything the orchestrator clarified>"
}
```

When you resume, you re-read the answers directory, ingest the new file, and continue.

### One question at a time

Grill-with-docs interviews "relentlessly". Each question must be one isolated file. Wait for that answer before writing the next question. Batching breaks the chat UX on Diangelo's side — he reads one Eyrie message, replies, the orchestrator relays back, you continue.

### Waves file (the Waves panel)

The Eyrie cc-bridge UI fetches `/api/cc-bridge/<id>/waves` which reads `<project_dir>/.planning/waves.json`. If the file is missing the panel stays empty. Write it at the end of stage 5 (triage), before Ralph dispatches subagents.

Shape (versioned, validated by the API):

```json
{
  "schema_version": 1,
  "summary": "<one paragraph: what the AFK pass will accomplish and the issue grouping rationale>",
  "waves": [
    {
      "no": 1,
      "label": "Wave 1 — <short title>",
      "mode": "parallel-4",
      "issues": [12, 13, 14, 15],
      "blurb": "<why these issues are grouped together (low conflict, shared module, etc.)>"
    },
    {
      "no": 2,
      "label": "Wave 2 — <short title>",
      "mode": "parallel-4",
      "issues": [16, 17, 18, 19]
    },
    {
      "no": 99,
      "label": "HITL — needs Diangelo decision",
      "mode": "sequential",
      "issues": [20, 21],
      "blurb": "Carry the `hitl` label. Leave as `new` and surface via the Decisions panel."
    },
    {
      "no": 100,
      "label": "Dependency-blocked",
      "mode": "sequential",
      "issues": [22, 23],
      "blurb": "Cannot start until preceding waves close."
    }
  ]
}
```

Rules:
- `mode` is informational. `"parallel-4"` means up to 4 subagents may run concurrently within the wave; `"sequential"` means one at a time.
- Every open AFK issue in scope MUST appear in exactly one wave. HITL and dependency-blocked issues get their own waves (high numbers like 99/100 keep them at the bottom of the UI).
- Update `waves.json` if the plan changes mid-flow — the panel re-fetches every poll cycle.
- The file lives in the project repo (`<project_dir>/.planning/waves.json`). Commit it on the feature/refactor branch alongside other planning artifacts, OR keep it untracked if it's purely orchestration metadata (your call per project; default is to commit so the plan is auditable).

### When you DON'T use the bridge

- Reading docs, code, git history, web → just do it
- Looking up your own recommended answer → just decide
- Anything where the codebase already gives the answer → use the codebase
- Trivial confirmations ("should I commit?") for actions already authorised by the per-project allowlist → just commit

The bridge is for decisions only a human can make: priorities, business intent, ambiguous trade-offs, naming with brand implications, scope cuts.

## Standard project layout — non-negotiable

Every project you operate on under this skill must have:

### Branches

- `main` → production-equivalent, mirrored to prod CI/CD
- `test` → staging, mirrored to test CI/CD
- `dev` → integration branch where feature work lands
- `feature/<slug>` → working branches; **always created from `dev`**

### Merge policy

- `feature/* → dev` merges are **autonomous** — open the PR with `gh pr create --base dev`, then `gh pr merge <N> --merge` yourself. Do NOT wait for human approval on dev merges.
- `dev → test` and `test → main` are **gated**: require explicit user permission via the bridge.
- Why: `test` and `main` trigger Azure CI/CD pipelines (Azure CLI inside GitHub Actions) which cost money and deploy real infra. `dev` is integration-only and safe to merge into without human sign-off.
- The "agent merges its own dev PRs" rule was locked 2026-05-20 after an early Ralph session left 5 PRs unmerged waiting for a human approval that wasn't required.

### CI/CD

- GitHub Actions workflows for `test` and `prod` use `azure/cli` action with service-principal credentials
- `dev` branch CI is build + test only — no deploy
- Branch protection: `main` and `test` require PR review (you, the headless agent, request review via the bridge if the orchestrator hasn't already approved)

### Per-project allowlist

Every project has a `.claude/settings.json` derived from the standard template at `~/.openclaw/workspace/templates/claude-settings/settings.json` (see `templates/claude-settings/README.md`). The standard shape:

- **Allow** broadly: `Bash(*)`, `Read(*)`, `Edit(*)`, `Write(*)`, `Grep(*)`, `Glob(*)`, `WebFetch(*)`, `WebSearch(*)` — agents get full autonomy for feature work, tests, builds, `git`, `gh`, `npm`/`npx`/`node`, etc.
- **Deny** the moves that touch CI/CD-gated branches or wreck state:
  - `git push` to `test`, `main`, or `master` (every refspec form)
  - `git push --force` / `-f` / `--mirror` / `--delete` anywhere
  - `git reset --hard origin/{test,main,master}`
  - `gh pr merge --base test|main|master`, `gh release create`
  - `sudo`, `rm -rf /` or `~`

Direct push to `dev` and `gh pr merge --base dev` are **allowed**. The agent owns the full `feature → push → PR → merge-to-dev → close-issue` cycle without bridge intervention. Bridge approval is only needed for `dev → test` and `test → main` promotions.

Per-repo overlays may add stack-specific allows (e.g. `./mvnw test` for Java) or tighten denies for special branches — but the baseline above is the canonical default. Copy with:

```sh
mkdir -p <repo>/.claude
cp ~/.openclaw/workspace/templates/claude-settings/settings.json <repo>/.claude/
```

## Database administration (workspace-tracked projects)

If the project's GitHub issues are tracked in the workspace SQLite (`~/.openclaw/workspace/agents/issues.sqlite`, `user_stories` table) — true for Eyrie and every Dimoit product repo — every status transition MUST flow through the workspace bulk CLI so SQLite, GitHub labels, and Eyrie's sprint board (`/work?tab=sprint-board`) stay in sync.

**Look up the story id from the GH issue number:**

```sh
sqlite3 ~/.openclaw/workspace/agents/issues.sqlite \
  "SELECT id FROM user_stories WHERE github_repo='<owner>/<repo>' AND github_issue_number=<N>"
```

**Set `display_status` (auto-pushes the matching `status:*` label to GitHub + updates SQLite):**

```sh
node --experimental-strip-types ~/.openclaw/workspace/agents/lib/gh_actions.ts \
  bulk stories '{"display_status":"<state>"}' <story-id> [<story-id-2> ...]
```

**The 5-state lifecycle (locked):** `new → active → on_hold | blocked → done`

- `active` — set BEFORE the first failing test for an issue. Signals "agent picked this up."
- `blocked` — set on hard-stuck (failing dependency, missing decision, environment issue you can't resolve). Pair with a Decisions question file.
- `on_hold` — set when an issue is paused mid-flight by an external priority shift (rare; usually only triggered by a question answer).
- `done` — set after `gh pr merge --base dev` succeeds and the issue auto-closes (or you close it manually). After merging, run `node --experimental-strip-types ~/.openclaw/workspace/agents/lib/gh_sync.ts <project-slug>` to flush state instantly (the cron only runs every 10 min).

**Labels you must NOT touch:**
- `wave:*` — Diangelo-managed, drives the Eyrie /work filters
- `sprint:YYYY-Www` — Diangelo-managed, drives the sprint board week
- `assignee:human|agent` — Diangelo-managed
- The `hitl` label — surfaces issues that require a human decision; leave such issues at `display_status=new` and route the decision through the bridge

**Labels you DO set (via the bulk CLI, never directly):**
- `status:new|active|on_hold|blocked|done`

See `/ralph-github-tdd` SKILL.md for the full bulk CLI reference; this section is the contract `hl-dev-flow` requires every stage that mutates issue state to follow.

## Stage scripts (how you run each)

### Stage 1: Grill with docs

```
Run /grill-with-docs with the project's existing CONTEXT.md, CONTEXT-MAP.md, and docs/adr/ as source material.
Mode: bridge.
Question one at a time → bridge → wait for answer → next question.
Until: every branch of the design tree is resolved, terminology is consistent with the existing domain model, and a plan is written to a .planning/<feature-slug>.md file.
```

When done, commit the plan file on a fresh `feature/<slug>` branch off `dev`. Push. Move to stage 2.

### Stage 2: PRD

```
Run /to-prd against the .planning/<feature-slug>.md file.
Output: <slug>-PRD.md committed to the same feature branch.
No bridge needed unless the PRD reveals scope ambiguity (then bridge it).
```

### Stage 3: Issues

```
Run /to-issues against the PRD.
Create issues with `gh issue create`. Label them `feat:<slug>`.
Output: list of issue numbers. Commit nothing new yet.
```

### Stage 4: Architecture pass

```
Run /improve-codebase-architecture against the codebase scoped to the affected modules.
Output: either a follow-up issue ("refactor X before building Y") or inline refactors on a refactor/<slug> branch off dev.
If refactors land, merge refactor/<slug> → dev first, then continue to stage 5 on a fresh feature branch.
```

### Stage 5: Triage

```
Run /triage against the issues created in stage 3 (and any new ones from stage 4).
For each issue confirm: clear acceptance criteria, scope-bounded, AFK-ready or marked `hitl`.
Group issues by likely conflict area (shared files, shared modules, dependency chains).
Issues that touch the same code go in sequential waves; independent issues go in parallel waves.
HITL issues get their own sequential wave at the bottom of the list.

Output (REQUIRED before stage 6 starts):
- `.planning/waves.json` written per the "Waves file" spec above
- Issue labels: every AFK issue carries `wave:<slug>` and `sprint:<YYYY-Wxx>` (Diangelo-managed — only confirm they're set; don't add/remove yourself)
- Every workspace-tracked issue still at `display_status=new` (do NOT pre-set `active` here)
```

When triage is done, write `.planning/waves.json` and (optionally) commit it on `feature/<slug>-waves` for auditability. The Eyrie cc-bridge UI Waves panel populates within seconds.

### Stage 6: Build

```
Run /ralph-github-tdd against the issues listed in `.planning/waves.json`.
Coordinate up to 4 subagents per wave with strict /tdd.
UI-heavy issues use Playwright Chrome; backend/config issues do not.

Per-issue workflow:
1. Look up story_id from SQLite (see Database administration).
2. Set display_status=active via the bulk CLI BEFORE writing the first failing test.
3. Branch: `feature/<wave-slug>-<issue-number>-<short>` off dev.
4. /tdd loop: red test → smallest code → green → refactor.
5. Commit, push the feature branch.
6. `gh pr create --base dev --head feature/... --title "..." --body "Closes #N"`.
7. `gh pr merge <N> --merge` — autonomous, no bridge approval needed for dev merges.
8. After merge, run `gh_sync.ts <project-slug>` so display_status flips to `done` instantly.
9. If hard-stuck: set display_status=blocked via the bulk CLI AND write a Decisions question file. Do NOT close the issue.

Move to the next wave only when the current wave's issues are all `done` or `blocked`.
```

### Stage 7: Screenshot tour (auto)

```
After the last MVP issue closes (Ralph done), run /screenshot-pages.
It boots the prod build, drives Playwright Chromium through every public
(and authenticated, if a fixture exists) route, and writes PNGs to
~/.openclaw/cc-bridge/sessions/<SESSION_ID>/screenshots/<NN-slug>.png.
The Eyrie cc-bridge ingest daemon mirrors them into the session view
within ~2s. Skip stage 6 only if there is no Frontend/ directory.
```

## Decision points that ALWAYS hit the bridge

- "`dev → test` promotion?" (gated, costs Azure)
- "`test → main` promotion?" (gated, deploys prod)
- "Found a deeper architecture issue that wasn't in the plan — keep scope or expand?"
- "Tests passing but feature feels under-specified — ship or grill more?"
- "Dependency upgrade needed (semver-major) — proceed?"
- "DB migration generated — review before applying?"
- "Issue is `hitl`-labeled — what's the call?"
- "Hard-stuck on an issue (3+ TDD attempts failed) — keep trying, re-shape, or drop?"

PR merges into `dev` do NOT hit the bridge. Agent merges its own dev PRs.

## Decision points you handle yourself

- Picking variable names, file layouts, internal helpers (use the codebase's existing style)
- Test cases that follow logically from the spec
- Refactors that don't change behaviour (`/tdd` covers these — keep tests green)
- Commit messages (follow the repo's `git log` style)
- Branch names (`feature/<issue-number>-<short-slug>`)

## Failure handling

- **Stage gate fails** (e.g., grill didn't resolve all branches): bridge the user, ask whether to push through or stop
- **Tests can't go green after N=3 tries**: write a `BLOCKER.md` to the feature branch, bridge the user
- **Deny rule trips** (you tried to do something blocked): stop immediately, bridge the user with context — do not retry
- **Bridge unreachable** (orchestrator down, files won't write): log a `STUCK.md` with last known state, exit gracefully

## DimoCMS and Harmuni — extra rules

These projects are Azure-deployed and multi-tenant:

- `/security-review` is mandatory before any PR to `test` (not just `main`)
- Embed/iframe code paths require explicit bridge approval at PR time, even on `dev`
- Connection-string and secret handling: never log, never commit, never read into agent context — if a secret leaks into your session, write `LEAK.md` and bridge immediately

## What you do not do

- You do not prompt stdin / wait on a TTY — you'll hang
- You do not assume the user is reading the session log live — they're not
- You do not skip the bridge for "small" decisions if they're scoped or business decisions
- You do not push to `test` or `main` without bridge confirmation (pushes/merges to `dev` are autonomous)
- You do not skip writing `.planning/waves.json` before stage 6 — the Waves panel depends on it
- You do not surface decisions as plain assistant-message text — only the question JSON file populates the Decisions panel
- You do not touch `wave:*`, `sprint:*`, or `assignee:*` labels — those are Diangelo-managed
- You do not run Azure CLI commands directly — those are deny-listed; the CI/CD pipeline does them
- You do not start stage 5 with unresolved questions from stage 1

## Invocation

Headless entry:

```bash
claude -p \
  --output-format stream-json \
  --input-format stream-json \
  --resume "<session-id>" \
  --add-dir ~/.openclaw/cc-bridge \
  "Run /hl-dev-flow for project <slug>. Bridge session id: <session-id>."
```

The orchestrator (Eyrie) handles the bridge polling, relay to user, and answer write-back. You only know about question files going out and answer files coming in.

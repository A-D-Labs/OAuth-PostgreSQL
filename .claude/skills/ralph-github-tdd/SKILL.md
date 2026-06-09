---
name: ralph-github-tdd
description: generate and run a ralph-coordinated github issue workflow in claude code using four subagents, strict /tdd, github cli issue discovery, and playwright chrome browser testing only for ui-heavy issues. use when the user wants to provoke or invoke a reusable prompt/process for github issues, ralph loops, subagents, claude code, antigravity, tdd workflows, or ui-heavy chrome testing decisions.
---

# Ralph GitHub TDD Workflow

Use this skill to produce or execute a reusable Claude Code prompt for processing GitHub issues through Ralph with four subagents and `/tdd`.

## Core behavior

When invoked, generate a ready-to-paste Claude Code prompt unless the user explicitly asks to modify the skill or package. The prompt must coordinate Ralph, four subagents, GitHub issues, `/tdd`, conditional Playwright Chrome UI testing, **and the Eyrie status-lifecycle integration described below.**

## Eyrie integration (locked 2026-05-20)

GitHub Issues are the source of truth for user stories in Diangelo's Eyrie/openclaw system. SQLite mirrors them via a 10-minute cron sync (`agents/lib/gh_sync.ts`), and the `/work`, `/today`, and `/stories/[id]` pages in Eyrie read straight from that mirror.

**Every status transition a Ralph subagent makes MUST be visible to Eyrie**, which means: change the GitHub issue's label/state, then either let the cron sweep catch up (≤10 min) or trigger an immediate sync via the workspace CLI.

### 5-state lifecycle (vocab is fixed)

```
new  →  active  →  on_hold  ⇄  blocked  →  done
                       │           │
                       └─────→ active ─────┘
```

| State | When | GitHub representation |
|---|---|---|
| `new` | freshly opened, not yet picked up | open issue, **no** `status:*` label |
| `active` | a subagent is working on it now | open issue, `status:active` label |
| `on_hold` | paused intentionally (waiting on something) | open issue, `status:on-hold` label |
| `blocked` | hard-stopped by a bug/dependency | open issue, `status:blocked` label |
| `done` | shipped + merged | closed issue, status labels removed |

Order of precedence when multiple status labels are present: `blocked` > `on-hold` > `active` > `new`. Closed always wins → `done`.

### How a subagent must change status

Two equivalent paths — pick one:

**Path A (preferred, immediate-sync):**

```bash
# Apply the label + close/reopen + re-sync that project, in one call.
node --experimental-strip-types \
  ~/.openclaw/workspace/agents/lib/gh_actions.ts \
  bulk stories '{"display_status":"<state>"}' <story-id-1> <story-id-2> ...
```

This pushes the right labels to GitHub *and* re-syncs SQLite so Eyrie sees the change in seconds. `<story-id>` is the SQLite `user_stories.id` (not the GH issue number). The CLI is idempotent.

**Path B (direct gh CLI, lazy sync via cron):**

```bash
# Active
gh issue edit <N> --repo <owner/repo> \
  --add-label "status:active" \
  --remove-label "status:on-hold,status:blocked"

# On hold
gh issue edit <N> --repo <owner/repo> \
  --add-label "status:on-hold" \
  --remove-label "status:active,status:blocked"

# Blocked
gh issue edit <N> --repo <owner/repo> \
  --add-label "status:blocked" \
  --remove-label "status:active,status:on-hold"

# Done
gh issue close <N> --repo <owner/repo> --reason completed
```

The cron sync (every 10 min) picks these up. For instant reflection, also run:

```bash
node --experimental-strip-types ~/.openclaw/workspace/agents/lib/gh_sync.ts <project-slug>
```

### Required status transitions per issue

For every issue a subagent processes:

1. **Pick up** → set `active` (path A or B above) **before** writing the first test.
2. **Block** → if work hits a hard external blocker (missing creds, broken dep upstream, design call needed), set `blocked` and stop. Don't move on without it.
3. **Pause** → if work has to wait on a teammate / a queued PR but can be picked up later, set `on_hold`.
4. **Unblock / unpause** → on resumption, set back to `active`.
5. **Finish** → after merge / commit, set `done` (i.e. close the issue with `--reason completed`).

Status transitions are NOT optional. Eyrie's `/today` sprint tile, `/work` sprint board, and nudges all key off `display_status`. An agent that closes a PR but never transitions the issue leaves Diangelo's dashboard stale.

### Wave / sprint metadata is not your concern

The `wave:*` and `sprint:*` labels are Diangelo-managed via the Eyrie UI. Don't add, remove, or rename them. Only touch `status:*` labels and `assignee:*` when explicitly applicable (see below).

### When to set the assignee dot

If a subagent owns an issue end-to-end (agent-only work), leave the existing `assignee:agent` label (sync defaults to that when nothing is set). If Diangelo's manual intervention is needed inside the issue scope, set `assignee:human` so the dot turns blue on the card:

```bash
gh issue edit <N> --repo <owner/repo> \
  --add-label "assignee:human" --remove-label "assignee:agent"
```

## Default prompt

```text
Use Ralph to coordinate 4 subagents that go through GitHub issues using /tdd.

Goal:
Process open GitHub issues one by one with strict TDD. Use Chrome UI testing only for UI-heavy issues. Every status transition flows back to Eyrie via GitHub labels.

Issue discovery:
- Use `gh issue list` to find open issues (filter by `--label wave:<slug>` or `--label sprint:<YYYY-Wxx>` when scoped).
- Use `gh issue view <issue-number>` to read the full issue before editing.
- Group issues by likely conflict area before assigning work.
- Assign up to 4 non-conflicting issues to the subagents.
- Each subagent works on only one issue at a time.

UI-heavy definition:
Treat an issue as UI-heavy only when it involves screens, layout, forms, navigation, modals, buttons, visual states, browser behavior, auth flows, responsive behavior, accessibility behavior, or end-user interaction.

Do not treat backend-only, config-only, docs-only, dependency-only, refactor-only, data-only, or non-visual issues as UI-heavy.

Status lifecycle (mandatory — Eyrie depends on this):
States: new → active → on_hold | blocked → done.
- Mark an issue `active` BEFORE writing the first test:
    gh issue edit <N> --repo <repo> --add-label "status:active" \
      --remove-label "status:on-hold,status:blocked"
- If hard-blocked, set `blocked` and stop on that issue:
    gh issue edit <N> --repo <repo> --add-label "status:blocked" \
      --remove-label "status:active,status:on-hold"
- If paused (queued / waiting), set `on_hold`:
    gh issue edit <N> --repo <repo> --add-label "status:on-hold" \
      --remove-label "status:active,status:blocked"
- On resume, set back to `active`.
- After merge, close: `gh issue close <N> --repo <repo> --reason completed`.

Prefer this immediate-sync path (writes labels + close-state AND re-syncs SQLite in one call):
    node --experimental-strip-types ~/.openclaw/workspace/agents/lib/gh_actions.ts \
      bulk stories '{"display_status":"<state>"}' <story-id-1> ...
Use SQLite `user_stories.id`, not the GH issue number. Look up via:
    sqlite3 ~/.openclaw/workspace/agents/issues.sqlite \
      "SELECT id FROM user_stories WHERE github_repo='<owner/repo>' AND github_issue_number=<N>"

Workflow for each issue:
1. Read the full issue with `gh issue view <issue-number>`.
2. Inspect relevant code and existing tests.
3. Decide whether the issue is UI-heavy: yes or no.
4. Set status `active` (see above) — do this BEFORE step 5.
5. Run `/tdd` with the issue context.
6. Write or update a failing test first.
7. Run the smallest relevant test command and confirm the failure.
8. Implement the smallest code change needed to pass.
9. Re-run the relevant tests.
10. If and only if the issue is UI-heavy:
    - Use Playwright MCP in Chrome.
    - Test the affected user flow in the browser.
    - Check visible behavior, console errors, network failures, and screenshots where useful.
11. Do not use Chrome UI testing for non-UI-heavy issues.
12. Do not mark an issue complete unless tests pass.
13. Commit the change. Then close the issue (`gh issue close <N> --reason completed`) — that flips it to `done` in Eyrie on the next sync.

Subagent rules:
- Work in parallel only when file conflicts are unlikely.
- If two issues touch the same files, routes, components, or feature area, queue them instead of working in parallel.
- Keep changes small and issue-scoped.
- Do not bundle unrelated fixes.
- Do not skip the failing-test step.
- If blocked, set `status:blocked` on the issue, leave it uncommitted unless safe, and move to the next non-conflicting issue.
- Do not touch `wave:*` or `sprint:*` labels — those are Diangelo-managed.

Completion report for each issue:
- Issue number and title
- Subagent name
- UI-heavy: yes/no
- Status transitions made (timestamps): new → active → done (or blocked / on_hold)
- Tests added or updated
- Test commands run
- Chrome/Playwright checks performed, only if UI-heavy
- Files changed
- Commit hash
- Remaining risks or follow-ups

Start by listing the open issues (optionally filtered by a sprint week label like `sprint:2026-W21`), grouping them by likely conflict area, then assign up to 4 non-conflicting issues to the subagents.
```

## Optional variants

If the user asks for a stricter version, add:

```text
Before committing, run lint, typecheck, and the full relevant test suite. Refuse to continue to the next issue if the current issue has failing tests unless the failure is unrelated and documented.
```

If the user wants a safer autonomous mode, add:

```text
Ask for approval before destructive commands, dependency upgrades, database migrations, deleting files, force-pushes, or broad refactors.
```

If the user wants issue comments, add:

```text
After a successful commit, draft a GitHub issue comment summarizing the fix, tests, and UI checks. Do not post it unless asked.
```

If the user wants the subagents scoped to a specific sprint week:

```text
Filter issue discovery to `gh issue list --label "sprint:<YYYY-Wxx>"`. Skip issues that don't carry that label.
```

## Output style

Return only the prompt by default. Keep it copy-paste ready. If the user asks for explanation, briefly explain how to invoke it, how to customize the variants, and how the status transitions feed into Eyrie.

---
name: playwright
description: Playwright Test best-practices for fast deterministic browser tests. Use when writing or debugging end-to-end / integration tests with @playwright/test. Pairs with /tdd as the test substrate for the red-green-refactor loop. Different role from openclaw-skills:browser-automation, which is exploratory only.
---

# Playwright

Playwright Test (`@playwright/test`) is the TDD substrate for browser-driven projects. This skill bakes in the rules that keep tests fast, deterministic, and maintainable.

If the user asks to write a test, this skill drives the *what* and *how*. Pair with `/tdd` for the *cycle*: red → green → refactor, vertical slices, one test → one impl.

## When to use this vs the OpenClaw browser tool

| Scenario | Tool |
|---|---|
| Automated tests in CI / pre-merge | **Playwright Test** |
| Red phase of TDD | **Playwright Test** |
| "Can you log in and check this banner?" | `openclaw-skills:browser-automation` |
| Manual debugging of a flaky Playwright test | `openclaw-skills:browser-automation` |
| Visual confirm a styling change | Either |

**Tests go in code, not chat.** If the goal is repeatable verification, write a Playwright test. If the goal is one-off exploration, use the browser-automation tool.

## Locator priority — top to bottom, stop at the first that fits

Tests should target what the **user** sees, not implementation internals. Use locators in this order:

1. `getByRole('button', { name: 'Save' })` — semantic + accessibility
2. `getByLabel('Email')` — form fields by their `<label>`
3. `getByPlaceholder('your@email.com')` — fallback when no label
4. `getByText('Welcome back')` — visible text content
5. `getByAltText(...)` / `getByTitle(...)` — images and tooltips
6. `getByTestId('foo')` — **last resort.** Only when no semantic anchor exists.

If you find yourself reaching for `page.locator('.css-class')` or `page.locator('div > div > span:nth-child(2)')`, stop and add an `aria-label` or `data-testid` to the markup instead. Tests should survive refactors of internal structure.

## Wait without sleeping

Never use `page.waitForTimeout(N)` in tests. It's the #1 source of flakiness. Use Playwright's auto-retrying assertions instead:

- `await expect(locator).toBeVisible()` — retries until visible or timeout
- `await expect(locator).toHaveText('Saved')` — retries until match
- `await expect.poll(() => fetchSomething())` — retry custom checks
- `await page.waitForLoadState('networkidle')` — full settle (use sparingly; expect-based waits scale better)
- `await page.waitForResponse('**/api/save')` — wait for a specific request

If your test needs a sleep, you're hiding a race condition. Find what you're really waiting for and assert on that.

## Network: real vs mocked

Two valid modes; pick deliberately:

**Real backend (integration test).** The Playwright `webServer` config boots the real backend. Slower, but exercises the full stack. Use for: auth flows, persistence checks, end-to-end critical paths.

**Mocked backend (component-style test).** `page.route('**/api/**', route => route.fulfill({...}))` stubs every API call. Fast, deterministic, no DB needed. Use for: UI logic, error states, edge cases that are hard to produce server-side.

**Anti-pattern:** mixing the two in one test (some real, some mocked). You lose the benefits of both. Pick one mode per test file.

## Fixtures and page objects

After three or more tests share setup, extract:

- **Auth fixture:** `test.extend({ authenticatedPage: ... })` — hands every test a logged-in page. Use `storageState` to skip the login flow per-test.
- **Page object:** `class LoginPage { goto() {} fill(email, password) {} submit() {} }` — encapsulates a screen. Tests read like a story.

Don't pre-build these on day one. Wait until the duplication is real, then extract.

## Run modes

| Command | When |
|---|---|
| `npx playwright test` | CI, full smoke |
| `npx playwright test --ui` | Develop a new test interactively |
| `npx playwright test --headed` | Watch a test run in a visible browser |
| `npx playwright test --debug` | Step through a test with the inspector |
| `npx playwright test path/to/spec.ts:42` | Run one test by line number |
| `npx playwright show-trace path/to/trace.zip` | Open a saved trace from a failure |
| `npx playwright codegen http://localhost:3939` | Record interactions and generate code |

## Pair with `/tdd`

Inner loop:

1. **Red.** Write the smallest failing Playwright test that describes the new behavior. Example: `await expect(page.getByRole('heading', { name: 'Welcome' })).toBeVisible();`. Run it. Watch it fail with the wrong reason — element not found.
2. **Green.** Write the minimal app code that makes that one test pass. Resist the urge to write the next test in your head and start sliding ahead. One test → one impl.
3. **Refactor.** Improve the test (better locator, fewer waits) AND improve the impl (extract, dedupe). Tests stay green.
4. Repeat.

**Don't write all tests first.** That's "horizontal slicing" — the anti-pattern Matt's `/tdd` skill calls out. You'll end up testing imagined behavior, not real behavior.

## Project-specific notes

### Eyrie (`~/.openclaw/eyrie`)
- Next.js, prod-built and served by launchd at `http://127.0.0.1:3939`
- `playwright.config.ts` uses `reuseExistingServer: true` so tests latch onto the running launchd instance
- DB is `eyrie-data/*.sqlite` files in workspace — tests that mutate state need cleanup (see `tests/e2e/fixtures/` once it exists)

### DimoCMS admin-ui (`~/.openclaw/DimoCMS/admin-ui`)
- Vite dev server on `http://localhost:5173` — note `localhost` not `127.0.0.1` (Vite binds to IPv6 loopback by default)
- API proxies to Spring Boot on `http://localhost:8080` (`/api`, `/auth`, `/oauth2`)
- **Two configs:**
  - `playwright.config.ts` — pure-frontend smoke tests, no backend needed
  - `playwright.integration.config.ts` — boots Vite AND Spring Boot together (long startup; needs PostgreSQL on :5432 + Java 21 via `.envrc`)
- Integration tests live in `tests/e2e/integration/`

## Anti-patterns to refuse politely

- `page.waitForTimeout(2000)` — see "Wait without sleeping" above
- Fixed-pixel assertions (`expect(box).toHaveCSS('width', '243px')`) — flaky on different DPRs
- Tests that depend on test order — every test must be independent
- Asserting on `console.log` output as a behavior check
- Snapshot/screenshot tests on full pages — too brittle. Snapshot only stable visual primitives
- One mega-test that does login + browse + edit + save + logout — split into one-behavior-per-test

## Debug a flaky test

In order:

1. Run with `--trace on` and open the trace: `npx playwright show-trace test-results/.../trace.zip`. The trace shows every action + screenshot per step.
2. If the trace doesn't reveal it, run `--debug` and step through interactively.
3. If still nothing, switch to `openclaw-skills:browser-automation` and reproduce manually in a real browser. Compare what the human can see to what Playwright "sees."

## When the test runner is hanging

Common causes:
- `webServer.url` doesn't return 200 within `timeout` — check the URL is reachable, raise the timeout
- `webServer.command` failed silently — set `stdout: 'pipe'` and tail the output
- Browser binary not installed — `npx playwright install chromium`
- Port already taken AND `reuseExistingServer: false` — set to `true` or kill the conflicting process

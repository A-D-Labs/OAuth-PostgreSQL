---
name: screenshot-pages
description: After the Ralph TDD loop finishes the MVP, take Playwright screenshots of every page of the built product and drop them in the cc-bridge session dir so they surface in the Eyrie cc-bridge UI. Used as the final step of /hl-dev-flow (after stage 5). Use when the user says "screenshot the pages", "screenshot tour", or the agent wraps up a Ralph TDD pass.
---

# /screenshot-pages — Playwright tour of the just-built product

When Ralph finishes building the MVP issues, this skill drives Playwright through every page of the product and saves PNGs to the active cc-bridge session so they appear in the Eyrie session detail view at `/cc-bridge/<id>`.

## Pre-flight

- The product has a `Frontend/` (React + Vite + Tailwind + Nginx-in-prod) per the standard project layout
- Playwright is already a dev-dep (template ships it). If not present: `npm i -D @playwright/test && npx playwright install --with-deps chromium`
- The bridge session dir is `~/.openclaw/cc-bridge/sessions/<SESSION_ID>/` and is exposed to the agent via `--add-dir` — write screenshots there

## What to capture

Two passes, both via Playwright Chromium in a fresh context, viewport `{1440, 900}`, dark-color-scheme preference set to match Eyrie's aesthetic:

1. **Public routes** (no auth) — enumerate from `Frontend/src/routes/`, `Frontend/src/App.tsx`, `pages/` or wherever the router lives. Examples: `/`, `/login`, `/signup`, `/about`, `/pricing`, `/terms`, `/privacy`.
2. **Authenticated routes** (logged-in user) — needs a sign-in flow. For Entra-protected products, hit a `test-user` route or use a Playwright fixture seeded with a fake JWT. If the project has a `e2e/auth.setup.ts`, reuse it.

Skip API routes, `404`, and pure redirects.

## How to drive it

Drop a script at `e2e/screenshot-tour.spec.ts` that uses `@playwright/test`. Reference shape:

```ts
import { test, expect } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { homedir } from 'node:os';

// Bridge session id passed as env or discovered from --add-dir
const SESSION_ID = process.env.CC_BRIDGE_SESSION_ID
  || (fs.readdirSync(path.join(homedir(), '.openclaw/cc-bridge/sessions/'))
       .sort().pop());
const OUT_DIR = path.join(
  homedir(), '.openclaw/cc-bridge/sessions/', SESSION_ID!, 'screenshots'
);
fs.mkdirSync(OUT_DIR, { recursive: true });

const PUBLIC_ROUTES: Array<{ slug: string; path: string; title?: string }> = [
  { slug: '01-home',     path: '/',         title: 'Home' },
  { slug: '02-pricing',  path: '/pricing',  title: 'Pricing' },
  { slug: '03-login',    path: '/login',    title: 'Login' },
  { slug: '04-signup',   path: '/signup',   title: 'Signup' },
  // … fill in for the product
];

for (const r of PUBLIC_ROUTES) {
  test(`screenshot ${r.slug}`, async ({ page }) => {
    await page.goto(`http://localhost:${process.env.PORT ?? '5173'}${r.path}`);
    await page.waitForLoadState('networkidle');
    await page.screenshot({
      path: path.join(OUT_DIR, `${r.slug}.png`),
      fullPage: true,
    });
    // Sidecar JSON so the ingest daemon can read the page title/url
    fs.writeFileSync(
      path.join(OUT_DIR, `${r.slug}.json`),
      JSON.stringify(
        { slug: r.slug, path: r.path, title: r.title ?? r.slug, captured_at: new Date().toISOString() },
        null, 2
      )
    );
  });
}
```

For authenticated routes, copy the same loop into a separate `screenshot-tour-auth.spec.ts` that uses `test.use({ storageState: '.auth/user.json' })` (or whatever fixture name the project uses).

## Runbook (what the agent actually does, end of Ralph stage 5)

```bash
# 1. Make sure the product builds and the dev server starts
cd Frontend
npm run build
PORT=5173 npm run preview &      # serves the prod bundle on :5173
DEV_PID=$!

# Wait for it
until curl -fs "http://localhost:5173/" > /dev/null; do sleep 1; done

# 2. Run the tour
CC_BRIDGE_SESSION_ID="$BRIDGE_SESSION_ID" \
  npx playwright test e2e/screenshot-tour.spec.ts \
    --project=chromium \
    --reporter=line

# 3. (optional) auth tour if a fixture exists
if [ -f e2e/auth.setup.ts ]; then
  CC_BRIDGE_SESSION_ID="$BRIDGE_SESSION_ID" \
    npx playwright test e2e/screenshot-tour-auth.spec.ts \
      --project=chromium --reporter=line || true
fi

# 4. Tear down
kill $DEV_PID || true
```

Screenshots land at `~/.openclaw/cc-bridge/sessions/<SESSION_ID>/screenshots/`. The Eyrie ingest daemon (cc-bridge-ingest) picks them up within ~2s and the Screenshots section of `/cc-bridge/<id>` renders the gallery.

## What to commit

- `e2e/screenshot-tour.spec.ts` (the file above, adapted)
- `e2e/screenshot-tour-auth.spec.ts` (if authenticated tour needed)
- `playwright.config.ts` baseURL set to `http://localhost:5173`

Do not commit the PNG outputs themselves — they go to the bridge session dir, not the repo. Add `e2e/screenshots/` to `.gitignore` if Playwright defaults write there.

## Failure handling

- **Dev server fails to start**: fall back to `npm run dev` instead of `npm run preview`
- **A route 404s**: continue with the next route; record the failure in `screenshots/<slug>.error.txt`
- **Auth fixture missing**: skip the auth tour, only do public
- **Playwright not installed**: install Chromium only (`npx playwright install chromium`), skip Firefox/WebKit
- **No bridge session id in env**: pick the most recently modified session dir as a best-effort fallback

## When NOT to run

- Pure backend-only services (no Frontend dir) — there's nothing to screenshot
- Pre-MVP work (PRD / issues / architecture stages) — screenshots are meaningful only after working UI exists
- Build failed — fix the build first

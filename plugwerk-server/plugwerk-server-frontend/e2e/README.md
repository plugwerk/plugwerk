# Plugwerk UI E2E tests (Playwright)

Browser-driven end-to-end tests that exercise the app from the outside like a
real user (issue #241). They run against the **production frontend bundle served
by the backend JAR** on `http://localhost:8080`, backed by a real PostgreSQL.

## Layout

```
e2e/
├── fixtures/
│   ├── config.ts         # base URL, admin creds, seeded namespace, storage paths
│   ├── global-setup.ts   # logs in as admin once, seeds a namespace, saves the session
│   └── api.ts            # REST helpers (admin token, create user)
├── pages/                # Page Object Model — no magic selector strings in specs
│   ├── LoginPage.ts
│   ├── CatalogPage.ts
│   └── TopNav.ts
└── tests/
    └── auth.spec.ts      # Phase 1 — authentication journeys
```

Selectors use accessible roles / labels / text first (the repo has no
`data-testid`s); a small, deliberate set of test-ids is added only where the
DOM is genuinely ambiguous.

## Run the full suite (recommended)

Brings up the stack via Docker Compose, waits for health, runs Playwright, tears
down:

```bash
./scripts/e2e-test.sh              # build image + run
SKIP_BUILD=1 ./scripts/e2e-test.sh # reuse an already-built image
```

## Run against an already-running stack

Start the stack yourself, then run Playwright directly:

```bash
docker compose -f docker-compose.yml -f docker-compose.e2e.yml up -d --build
cd plugwerk-server/plugwerk-server-frontend
npm run e2e            # headless
npm run e2e:ui         # interactive UI mode
npm run e2e:debug      # step-through debugger
npm run e2e:report     # open the last HTML report
```

Override the target with `PLUGWERK_E2E_BASE_URL`. The suite expects the admin
password to be `admin` (`PLUGWERK_AUTH_ADMIN_PASSWORD`, set by
`docker-compose.e2e.yml`).

## First-time setup

```bash
cd plugwerk-server/plugwerk-server-frontend
npm ci
npx playwright install chromium   # add --with-deps on a fresh CI runner
```

## Notes

- **Auth reuse:** `global-setup` saves the logged-in session (including the
  httpOnly `plugwerk_refresh` cookie) to `e2e/.auth/admin.json`; authenticated
  specs reuse it. Auth specs opt out with
  `test.use({ storageState: { cookies: [], origins: [] } })`.
- **Browsers:** Chromium only for now; Firefox/WebKit are a follow-up.
- **Flakes:** fix them or quarantine with `test.fixme()` — never merge a flaky test.

#!/usr/bin/env bash
# Plugwerk Playwright E2E runner (issue #241).
#
# Brings up the full stack (PostgreSQL + plugwerk-server serving the production
# SPA) via Docker Compose with the E2E credential overrides, waits for health,
# runs the Playwright suite against http://localhost:8080, then tears the stack
# down. Playwright's own global-setup logs in and seeds fixtures.
#
# Usage:
#   ./scripts/e2e-test.sh              # build image + run full suite
#   SKIP_BUILD=1 ./scripts/e2e-test.sh # reuse the already-built image
#
# Env:
#   BASE_URL   override the target (default http://localhost:8080)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FRONTEND_DIR="$ROOT/plugwerk-server/plugwerk-server-frontend"
BASE_URL="${BASE_URL:-http://localhost:8080}"
COMPOSE=(docker compose -f "$ROOT/docker-compose.yml" -f "$ROOT/docker-compose.e2e.yml")

cleanup() {
  echo "--- tearing down E2E stack ---"
  "${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "--- starting E2E stack ---"
if [[ "${SKIP_BUILD:-}" == "1" ]]; then
  "${COMPOSE[@]}" up -d
else
  "${COMPOSE[@]}" up -d --build
fi

echo "--- waiting for $BASE_URL/actuator/health to report UP ---"
for i in $(seq 1 60); do
  if curl -sf "$BASE_URL/actuator/health" | grep -q '"status":"UP"'; then
    echo "server is UP"
    break
  fi
  if [[ "$i" == "60" ]]; then
    echo "ERROR: server did not become healthy in time" >&2
    "${COMPOSE[@]}" logs plugwerk-server | tail -80 >&2
    exit 1
  fi
  sleep 2
done

echo "--- running Playwright suite ---"
cd "$FRONTEND_DIR"
PLUGWERK_E2E_BASE_URL="$BASE_URL" PLUGWERK_AUTH_ADMIN_PASSWORD="admin" npm run e2e

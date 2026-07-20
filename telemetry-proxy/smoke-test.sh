#!/usr/bin/env bash
#
# Smoke test for the Plugwerk telemetry reverse proxy.
#
# Usage:
#   ./smoke-test.sh [BASE_URL]
#
#   BASE_URL defaults to http://127.0.0.1:8787 (the `wrangler dev` default).
#   Examples:
#     ./smoke-test.sh                                  # local wrangler dev
#     ./smoke-test.sh https://plugwerk-telemetry-proxy.<account>.workers.dev   # deployed endpoint
#
# Checks:
#   1. A valid zero-PII payload  -> 204 No Content
#   2. A payload with an extra field -> 400 Bad Request (unknown field rejected)
#
# Note: the 204 check requires the proxy to reach PostHog (a reachable capture
# endpoint + a configured key). The 400 check is validator-only and offline.

set -euo pipefail

BASE_URL="${1:-http://127.0.0.1:8787}"
ENDPOINT="${BASE_URL%/}/v1/events"

# Fixed, well-known SYNTHETIC install id. When this smoke test runs against the
# live endpoint it forwards a real event to PostHog, so keep this id constant and
# exclude it from analytics (e.g. filter out distinct_id = this UUID) to keep the
# install-base metric clean. It is not a real installation.
UUID="3f2504e0-4f89-41d3-9a0c-0305e82c3301"
VALID="{\"installId\":\"${UUID}\",\"version\":\"1.1.0-SNAPSHOT\",\"installType\":\"docker-compose\",\"event\":\"server_start\"}"
EXTRA="{\"installId\":\"${UUID}\",\"version\":\"1.1.0-SNAPSHOT\",\"installType\":\"docker-compose\",\"event\":\"server_start\",\"hostname\":\"leaky\"}"

pass=0
fail=0

check() {
  local name="$1" expected="$2" actual="$3"
  if [[ "$actual" == "$expected" ]]; then
    echo "PASS: ${name} (HTTP ${actual})"
    pass=$((pass + 1))
  else
    echo "FAIL: ${name} (expected ${expected}, got ${actual})"
    fail=$((fail + 1))
  fi
}

post_status() {
  curl -s -o /dev/null -w "%{http_code}" -X POST "$ENDPOINT" \
    -H "Content-Type: application/json" --data "$1"
}

echo "Smoke-testing ${ENDPOINT}"
echo "----"

check "valid payload -> 204" "204" "$(post_status "$VALID")"
check "extra field -> 400" "400" "$(post_status "$EXTRA")"

echo "----"
echo "${pass} passed, ${fail} failed"
[[ "$fail" -eq 0 ]]

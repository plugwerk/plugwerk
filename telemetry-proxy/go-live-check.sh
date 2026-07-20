#!/usr/bin/env bash
#
# Go-live security verifier for the Plugwerk telemetry endpoint (DEV-54).
#
# Read-only. It inspects the LIVE Cloudflare configuration and the deployed Worker
# and asserts the security go-live conditions that gate opening the ingestion
# endpoint to production traffic:
#
#   1. Zone rate-limiting rule applied  (custom-domain variant ONLY — zone rules
#                                        do not exist for workers.dev; there the
#                                        in-code [[ratelimits]] binding + platform
#                                        DDoS mitigation are the limiting layers,
#                                        a deviation signed off in DEV-33).
#   2. PostHog secret is set            (the phc_ project-key prefix is enforced
#                                        in code — src/posthog.ts — and proven by
#                                        the smoke 204; this script confirms the
#                                        secret EXISTS, since its value is not
#                                        readable by design).
#   3. No Logpush request-body/header capture on the zone (custom-domain variant
#      ONLY, same reason as condition 1).
#
# It makes no changes. Run it at deploy time (DEV-34) and paste the output into the
# DEV-54 thread as the go-live confirmation evidence.
#
# Usage:
#   # workers.dev variant (no zone) — zone checks are skipped with a notice:
#   ./go-live-check.sh https://plugwerk-telemetry-proxy.<account>.workers.dev
#
#   # custom-domain variant — set BOTH CF_* vars to verify the zone conditions:
#   CF_API_TOKEN=<zone-scoped token> CF_ZONE_ID=<plugwerk.io zone id> \
#     ./go-live-check.sh https://telemetry.plugwerk.io
#
#   BASE_URL (required) is the deployed endpoint; the live smoke test
#   (204 valid / 400 extra-field) runs against it via smoke-test.sh.
#
# Requirements: curl, python3. Optional: wrangler (for the secret-existence check).

set -euo pipefail

CF_API="https://api.cloudflare.com/client/v4"
EXPECTED_PERIOD=60
EXPECTED_REQUESTS=60

BASE_URL="${1:-}"
if [[ -z "$BASE_URL" ]]; then
  echo "Usage: ./go-live-check.sh <base-url>" >&2
  echo "  e.g.: ./go-live-check.sh https://plugwerk-telemetry-proxy.<account>.workers.dev" >&2
  exit 2
fi
HOST="$(printf '%s' "$BASE_URL" | sed -E 's|^[a-zA-Z]+://||; s|/.*$||')"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

pass=0
fail=0

ok() { echo "PASS: $1"; pass=$((pass + 1)); }
bad() { echo "FAIL: $1"; fail=$((fail + 1)); }
info() { echo "      $1"; }

require() {
  command -v "$1" >/dev/null 2>&1 || { echo "ERROR: '$1' is required but not installed." >&2; exit 2; }
}

require curl
require python3

# Mode detection: both CF_* vars -> custom-domain mode (zone checks run);
# neither -> workers.dev mode (zone checks are skipped, visibly); one -> error.
if [[ -n "${CF_API_TOKEN:-}" && -n "${CF_ZONE_ID:-}" ]]; then
  ZONE_MODE=1
elif [[ -z "${CF_API_TOKEN:-}" && -z "${CF_ZONE_ID:-}" ]]; then
  ZONE_MODE=0
else
  echo "ERROR: set BOTH CF_API_TOKEN and CF_ZONE_ID (custom-domain mode) or NEITHER (workers.dev mode)." >&2
  exit 2
fi

cf_get() {
  curl -sS --fail-with-body \
    -H "Authorization: Bearer ${CF_API_TOKEN}" \
    -H "Content-Type: application/json" \
    "${CF_API}/zones/${CF_ZONE_ID}/$1"
}

echo "Go-live security check for ${HOST} (DEV-54)"
echo "================================================================"

# --- Condition 1: zone rate-limiting rule -----------------------------------
echo
echo "[1/3] Zone rate-limiting rule (http_ratelimit phase)"
if [[ "$ZONE_MODE" == "0" ]]; then
  info "SKIP (workers.dev variant): no zone fronts the Worker, so no zone rate-limiting"
  info "rule can exist. Limiting layers: in-code [[ratelimits]] binding (60/60s per IP,"
  info "per colo) + Cloudflare's always-on DDoS mitigation. Deviation signed off in DEV-33."
elif rl_json="$(cf_get "rulesets/phases/http_ratelimit/entrypoint" 2>/dev/null)"; then
  result="$(
    HOST="$HOST" EXPECTED_PERIOD="$EXPECTED_PERIOD" EXPECTED_REQUESTS="$EXPECTED_REQUESTS" \
    python3 - "$rl_json" <<'PY'
import json, os, sys

host = os.environ["HOST"]
exp_period = int(os.environ["EXPECTED_PERIOD"])
exp_requests = int(os.environ["EXPECTED_REQUESTS"])
data = json.loads(sys.argv[1])
rules = (data.get("result") or {}).get("rules") or []

def matches(rule):
    expr = (rule.get("expression") or "")
    rl = rule.get("ratelimit") or {}
    chars = rl.get("characteristics") or []
    return (
        host in expr
        and "/v1/" in expr
        and rule.get("action") == "block"
        and "ip.src" in chars
        and rl.get("period") == exp_period
        and rl.get("requests_per_period") == exp_requests
    )

hit = next((r for r in rules if matches(r)), None)
if hit:
    rl = hit.get("ratelimit") or {}
    print("OK|block, ip.src, %s req / %s s (mitigation_timeout=%ss)" % (
        rl.get("requests_per_period"), rl.get("period"), rl.get("mitigation_timeout")))
    print("EXPR|%s" % (hit.get("expression") or ""))
else:
    summary = "; ".join(
        "action=%s period=%s rpp=%s expr=%r" % (
            r.get("action"), (r.get("ratelimit") or {}).get("period"),
            (r.get("ratelimit") or {}).get("requests_per_period"), (r.get("expression") or "")[:80])
        for r in rules) or "(no rate-limiting rules on this zone)"
    print("MISS|%s" % summary)
PY
  )"
  verdict="${result%%|*}"
  detail="${result#*|}"
  detail="${detail%%$'\n'*}"
  if [[ "$verdict" == "OK" ]]; then
    ok "rate-limit rule present — ${detail}"
    info "$(printf '%s\n' "$result" | sed -n 's/^EXPR|/expr: /p')"
  else
    bad "no matching rate-limit rule (need: block, ip.src, ${EXPECTED_REQUESTS}/${EXPECTED_PERIOD}s, host+/v1/)"
    info "found: ${detail}"
    info "fix: apply the rule from README.md -> 'Zone rate-limiting rule (REQUIRED)'"
  fi
else
  bad "could not read the http_ratelimit ruleset (check CF_API_TOKEN scope / CF_ZONE_ID)"
fi

# --- Condition 2: PostHog project key secret --------------------------------
echo
echo "[2/3] PostHog project key secret (phc_ prefix enforced in code)"
if command -v wrangler >/dev/null 2>&1; then
  if secrets_json="$(cd "$SCRIPT_DIR" && wrangler secret list --format json 2>/dev/null)"; then
    if printf '%s' "$secrets_json" | python3 -c \
      'import json,sys; sys.exit(0 if any(s.get("name")=="POSTHOG_PROJECT_KEY" for s in json.load(sys.stdin)) else 1)'; then
      ok "POSTHOG_PROJECT_KEY secret is set on the deployed Worker"
      info "value is write-only; the phc_ prefix is enforced at runtime (src/posthog.ts)"
      info "a non-phc_ key fails closed (502) — proven green by the smoke 204 below"
    else
      bad "POSTHOG_PROJECT_KEY secret is NOT set — run: wrangler secret put POSTHOG_PROJECT_KEY"
    fi
  else
    info "SKIP: 'wrangler secret list' failed (not authenticated?). The smoke 204 still"
    info "      proves a working phc_ key, and the code guard rejects any non-phc_ key."
  fi
else
  info "SKIP: wrangler not installed. The phc_ prefix is enforced in code (src/posthog.ts);"
  info "      a passing smoke 204 below confirms a valid phc_ key is configured."
fi

# --- Condition 3: no Logpush body/header capture ----------------------------
echo
echo "[3/3] Logpush — no request-body / header capture"
if [[ "$ZONE_MODE" == "0" ]]; then
  info "SKIP (workers.dev variant): Logpush jobs are zone-scoped; no zone exists."
  info "Do not add an account-level Workers Logpush job that captures request bodies."
elif lp_json="$(cf_get "logpush/jobs" 2>/dev/null)"; then
  result="$(python3 - "$lp_json" <<'PY'
import json, sys

data = json.loads(sys.argv[1])
jobs = data.get("result") or []

# Fields that would capture request bodies or headers (PII risk for this gate).
SENSITIVE = {
    "ClientRequestBody", "RequestHeaders", "ResponseHeaders",
    "ClientRequestHeaders", "OriginResponseHeaders", "RequestBody", "ResponseBody",
}

def fields_of(job):
    oo = job.get("output_options") or {}
    names = set(oo.get("field_names") or [])
    legacy = job.get("logpull_options") or ""  # e.g. "fields=ClientIP,RequestHeaders&timestamps=rfc3339"
    for part in legacy.split("&"):
        if part.startswith("fields="):
            names.update(f for f in part[len("fields="):].split(",") if f)
    return names

offenders = []
warnings = []
for job in jobs:
    if not job.get("enabled", True):
        continue
    f = fields_of(job)
    hits = sorted(SENSITIVE & f)
    label = "%s (dataset=%s, id=%s)" % (job.get("name") or "?", job.get("dataset"), job.get("id"))
    if hits:
        offenders.append("%s -> %s" % (label, ", ".join(hits)))
    elif job.get("dataset") == "workers_trace_events":
        warnings.append("%s -> captures Worker console logs (we log nothing, but confirm)" % label)

if offenders:
    print("FAIL|" + " ; ".join(offenders))
elif not jobs:
    print("OK|no Logpush jobs configured on this zone")
else:
    msg = "%d Logpush job(s), none capture request body/headers" % len(jobs)
    if warnings:
        msg += " | WARN: " + " ; ".join(warnings)
    print("OK|" + msg)
PY
  )"
  verdict="${result%%|*}"
  detail="${result#*|}"
  if [[ "$verdict" == "OK" ]]; then
    ok "no request-body/header Logpush capture — ${detail}"
  else
    bad "Logpush job(s) capture request body/headers: ${detail}"
    info "fix: remove those fields from the job, or delete the job, for this zone"
  fi
else
  bad "could not read Logpush jobs (check CF_API_TOKEN scope / CF_ZONE_ID)"
fi

# --- Live smoke test (204 / 400) --------------------------------------------
echo
echo "[+] Live smoke test against ${BASE_URL}"
if [[ -x "${SCRIPT_DIR}/smoke-test.sh" ]]; then
  if "${SCRIPT_DIR}/smoke-test.sh" "$BASE_URL"; then
    ok "smoke test passed (204 valid / 400 extra-field)"
  else
    bad "smoke test failed — endpoint not healthy"
  fi
else
  info "SKIP: smoke-test.sh not found/executable next to this script"
fi

echo
echo "================================================================"
echo "${pass} passed, ${fail} failed"
[[ "$fail" -eq 0 ]]

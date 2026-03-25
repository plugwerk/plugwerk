#!/usr/bin/env bash
# Plugwerk E2E Smoke Test
# Runs against a live Docker Compose stack and verifies the core upload/download flow.
#
# Usage:
#   ./scripts/smoke-test.sh              # builds image + starts stack
#   SKIP_BUILD=1 ./scripts/smoke-test.sh # skips docker compose build (faster for local iteration)
set -euo pipefail

BASE_URL="${PLUGWERK_BASE_URL:-http://localhost:8080}"
USERNAME="${PLUGWERK_USERNAME:-test}"
PASSWORD="${PLUGWERK_PASSWORD:-test}"
NAMESPACE="smoke-$(date +%s)"
PLUGIN_ID="smoke-plugin"
PLUGIN_VERSION="1.0.0"
WORKDIR="$(mktemp -d)"
COMPOSE_FILE="$(cd "$(dirname "$0")/.." && pwd)/docker-compose.yml"

cleanup() {
  echo "--- Cleanup ---"
  rm -rf "$WORKDIR"
  docker compose -f "$COMPOSE_FILE" down -v --remove-orphans 2>/dev/null || true
}
trap cleanup EXIT

# --------------------------------------------------------------------------- #
# 1. Build + start stack                                                        #
# --------------------------------------------------------------------------- #
echo "--- Starting Docker Compose stack ---"
if [[ "${SKIP_BUILD:-0}" != "1" ]]; then
  docker compose -f "$COMPOSE_FILE" build --progress=plain
fi
docker compose -f "$COMPOSE_FILE" up -d

echo "--- Waiting for server health ---"
for i in $(seq 1 30); do
  if curl -sf "$BASE_URL/actuator/health" | grep -q '"status":"UP"'; then
    echo "Server is up (attempt $i)"
    break
  fi
  echo "  waiting... ($i/30)"
  sleep 5
  if [[ $i -eq 30 ]]; then
    echo "ERROR: Server did not become healthy in time"
    docker compose -f "$COMPOSE_FILE" logs
    exit 1
  fi
done

# --------------------------------------------------------------------------- #
# 2. Login                                                                      #
# --------------------------------------------------------------------------- #
echo "--- Login ---"
TOKEN=$(curl -sf -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")
[[ -z "$TOKEN" ]] && { echo "ERROR: Login failed"; exit 1; }
echo "Login OK"

AUTH_HEADER="Authorization: Bearer $TOKEN"

# --------------------------------------------------------------------------- #
# 3. Create namespace                                                            #
# --------------------------------------------------------------------------- #
echo "--- Creating namespace: $NAMESPACE ---"
curl -sf -X POST "$BASE_URL/api/v1/namespaces" \
  -H "$AUTH_HEADER" \
  -H "Content-Type: application/json" \
  -d "{\"slug\":\"$NAMESPACE\",\"ownerOrg\":\"Smoke Test\"}" > /dev/null
echo "Namespace created"

# --------------------------------------------------------------------------- #
# 4. Build minimal test plugin JAR                                              #
# --------------------------------------------------------------------------- #
echo "--- Building minimal test plugin JAR ---"
DESCRIPTOR="$WORKDIR/plugwerk.yml"
cat > "$DESCRIPTOR" <<YAML
plugwerk:
  id: $PLUGIN_ID
  version: $PLUGIN_VERSION
  name: Smoke Test Plugin
  description: Minimal plugin for E2E smoke testing
YAML

JAR_FILE="$WORKDIR/$PLUGIN_ID-$PLUGIN_VERSION.jar"
(cd "$WORKDIR" && jar cf "$JAR_FILE" plugwerk.yml)

EXPECTED_SHA=$(sha256sum "$JAR_FILE" | awk '{print $1}')
echo "JAR built: $JAR_FILE (SHA-256: $EXPECTED_SHA)"

# --------------------------------------------------------------------------- #
# 5. Upload plugin release                                                      #
# --------------------------------------------------------------------------- #
echo "--- Uploading plugin release ---"
UPLOAD_RESPONSE=$(curl -sf -X POST "$BASE_URL/api/v1/namespaces/$NAMESPACE/releases" \
  -H "$AUTH_HEADER" \
  -F "artifact=@$JAR_FILE;type=application/java-archive")
echo "Upload response: $UPLOAD_RESPONSE"
RELEASE_ID=$(echo "$UPLOAD_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
[[ -z "$RELEASE_ID" ]] && { echo "ERROR: Upload failed — no release ID"; exit 1; }
echo "Upload OK (release ID: $RELEASE_ID)"

# --------------------------------------------------------------------------- #
# 6. Query catalog                                                              #
# --------------------------------------------------------------------------- #
echo "--- Querying catalog ---"
CATALOG=$(curl -sf "$BASE_URL/api/v1/namespaces/$NAMESPACE/plugins" \
  -H "$AUTH_HEADER")
FOUND=$(echo "$CATALOG" | python3 -c "
import sys, json
d = json.load(sys.stdin)
plugins = d.get('content', d) if isinstance(d, dict) else d
print(next((p['pluginId'] for p in plugins if p['pluginId'] == '$PLUGIN_ID'), ''))
")
[[ "$FOUND" != "$PLUGIN_ID" ]] && { echo "ERROR: Plugin not found in catalog"; exit 1; }
echo "Catalog OK — plugin found"

# --------------------------------------------------------------------------- #
# 7. Download artifact + verify SHA-256                                         #
# --------------------------------------------------------------------------- #
echo "--- Downloading artifact ---"
DOWNLOAD_FILE="$WORKDIR/downloaded.jar"
curl -sf "$BASE_URL/api/v1/namespaces/$NAMESPACE/plugins/$PLUGIN_ID/releases/$PLUGIN_VERSION/download" \
  -H "$AUTH_HEADER" \
  -o "$DOWNLOAD_FILE"

ACTUAL_SHA=$(sha256sum "$DOWNLOAD_FILE" | awk '{print $1}')
if [[ "$ACTUAL_SHA" != "$EXPECTED_SHA" ]]; then
  echo "ERROR: SHA-256 mismatch!"
  echo "  expected: $EXPECTED_SHA"
  echo "  actual:   $ACTUAL_SHA"
  exit 1
fi
echo "Download OK — SHA-256 verified"

# --------------------------------------------------------------------------- #
echo ""
echo "✓ All smoke tests passed"

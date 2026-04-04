#!/usr/bin/env bash
# Checks that all TypeScript/TSX source files contain the AGPL license header.
# Usage: ./scripts/license-check.sh        (check only)
#        ./scripts/license-check.sh --fix   (add missing headers)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
FRONTEND_DIR="$(dirname "$SCRIPT_DIR")"
PROJECT_ROOT="$(cd "$FRONTEND_DIR/../.." && pwd)"
HEADER_FILE="$PROJECT_ROOT/license-header.txt"
SRC_DIR="$FRONTEND_DIR/src"

EXPECTED="Copyright (c) 2025-present devtank42 GmbH"

if [[ ! -f "$HEADER_FILE" ]]; then
  echo "Error: license-header.txt not found at $HEADER_FILE" >&2
  exit 1
fi

FIX=false
if [[ "${1:-}" == "--fix" ]]; then
  FIX=true
fi

FAILED=0
FIXED=0

while IFS= read -r -d '' file; do
  # Skip generated files
  if [[ "$file" == *"/api/generated/"* || "$file" == *"vite-env.d.ts" ]]; then
    continue
  fi

  if ! head -5 "$file" | grep -q "$EXPECTED"; then
    if $FIX; then
      # Prepend header + blank line
      TMPFILE=$(mktemp)
      cat "$HEADER_FILE" "$file" > "$TMPFILE"
      mv "$TMPFILE" "$file"
      echo "✓ Added header: ${file#"$FRONTEND_DIR"/}"
      FIXED=$((FIXED + 1))
    else
      echo "✗ Missing header: ${file#"$FRONTEND_DIR"/}"
      FAILED=$((FAILED + 1))
    fi
  fi
done < <(find "$SRC_DIR" -type f \( -name '*.ts' -o -name '*.tsx' \) -print0)

if $FIX; then
  echo ""
  echo "$FIXED file(s) updated."
else
  if [[ $FAILED -gt 0 ]]; then
    echo ""
    echo "License check failed: $FAILED file(s) missing header."
    echo "Run 'npm run license:add' to fix."
    exit 1
  else
    echo "All source files have the license header."
  fi
fi

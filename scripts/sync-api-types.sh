#!/usr/bin/env bash
# Fetches the latest OpenAPI spec from the running backend and regenerates
# frontend types + TanStack Query hooks from it.
#
# Usage:
#   ./scripts/sync-api-types.sh
#
# Requirements:
#   - Backend must be running on localhost:8080 (docker compose up, or ./gradlew bootRun)
#   - Node/npm must be available

set -euo pipefail

BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
SPEC_PATH="frontend/src/generated/openapi-spec.json"

echo "==> Fetching OpenAPI spec from $BACKEND_URL/v3/api-docs ..."
curl -sf "$BACKEND_URL/v3/api-docs" -o "$SPEC_PATH"
echo "    Saved to $SPEC_PATH"

echo "==> Regenerating types + hooks ..."
(cd frontend && npm run generate:api)

echo ""
echo "Done. Review changes in frontend/src/generated/, then adjust any FE code that depends on updated types."

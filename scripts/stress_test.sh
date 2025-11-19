#!/bin/zsh

# Simple script to repeatedly invoke the query endpoint.
# Usage: ./stress_test.sh [url] [query]
#
# url   – base URL of the query endpoint (default: http://localhost:8080/api/query)
# query – SQL query to execute (default: SELECT 1)

set -euo pipefail

URL=${1:-http://localhost:8080/api/query}
QUERY=${2:-SELECT 1}
DELAY=${DELAY:-1}

echo "Calling $URL with query: $QUERY every ${DELAY}s"

while true; do
  # Use --data-urlencode to ensure the query parameter is properly escaped
  curl -s -G --data-urlencode "q=${QUERY}" "${URL}" || true
  sleep "$DELAY"
done

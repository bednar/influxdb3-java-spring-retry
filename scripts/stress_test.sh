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
  # print timestamp (ISO-like) before request (macOS-compatible)
  printf '%s ' "$(date '+%Y-%m-%dT%H:%M:%S%z')"

  # perform request: body + newline + status code
  response=$(curl -sS -G --data-urlencode "q=${QUERY}" -w '\n%{http_code}' "${URL}" || true)
  curl_exit=$?

  if [ "$curl_exit" -ne 0 ]; then
    printf 'CURL_ERROR (exit %s)\n' "$curl_exit"
  else
    http_code=$(printf '%s' "$response" | tail -n1)
    body=$(printf '%s' "$response" | sed '$d')

    # If body is exactly the status (no newline), treat as no response
    if [ "$http_code" = "$body" ]; then
      body=""
    fi

    if [[ -z "$http_code" || "$http_code" = *[^0-9]* ]]; then
      printf 'NO_RESPONSE\n'
    elif [ "$http_code" -ge 400 ]; then
      printf 'HTTP_ERROR %s: %s\n' "$http_code" "$body"
    else
      printf '%s\n' "$body"
    fi
  fi

  sleep "$DELAY"
done

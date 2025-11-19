#!/bin/zsh

# Simple script to repeatedly invoke the query endpoint.
# Usage: ./stress_test.sh [url] [query] [concurrency]
#
# url         – base URL of the query endpoint (default: http://localhost:8080/api/query)
# query       – SQL query to execute (default: SELECT 1)
# concurrency – number of parallel requests to run (default: 1; can also use $CONCURRENCY)

set -euo pipefail

URL=${1:-http://localhost:8080/api/query}
QUERY=${2:-SELECT 1}
DELAY=${DELAY:-1}
CONCURRENCY=${3:-${CONCURRENCY:-1}}

WORKER_PIDS=()

echo "Calling $URL with query: $QUERY every ${DELAY}s with concurrency ${CONCURRENCY}"

worker() {
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
}

cleanup() {
  set +e
  if [ ${#WORKER_PIDS[@]} -gt 0 ]; then
    kill -TERM "${WORKER_PIDS[@]}" 2>/dev/null || true
    # wait briefly for graceful shutdown
    for _ in {1..20}; do
      alive=()
      for pid in "${WORKER_PIDS[@]}"; do
        if kill -0 "$pid" 2>/dev/null; then
          alive+=("$pid")
        fi
      done
      WORKER_PIDS=("${alive[@]}")
      if [ ${#WORKER_PIDS[@]} -eq 0 ]; then
        break
      fi
      sleep 0.1
    done
    if [ ${#WORKER_PIDS[@]} -gt 0 ]; then
      kill -KILL "${WORKER_PIDS[@]}" 2>/dev/null || true
    fi
  fi
}

# Ensure background workers are terminated on Ctrl-C or kill
trap 'cleanup; exit 0' INT TERM

# Start the requested number of workers
i=1
while [ "$i" -le "$CONCURRENCY" ]; do
  worker &
  WORKER_PIDS+=("$!")
  i=$((i+1))
done

# Wait for workers (until interrupted)
wait

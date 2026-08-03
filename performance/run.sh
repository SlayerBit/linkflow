#!/usr/bin/env bash
# Run a LinkFlow k6 scenario and write HTML + JSON reports under performance/reports/.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SCENARIO="${1:-}"
shift || true

usage() {
  cat <<EOF
Usage: ./performance/run.sh <scenario> [k6 args...]

Scenarios:
  smoke               Critical-path sanity (1 VU)
  login               Login against seeded users
  registration        Register new accounts (writes DB)
  redirect            GET /r/{code} hot path
  url-creation        Authenticated URL create (writes DB)
  analytics           Analytics reads for owned URLs
  authenticated-mix   Mixed authenticated + redirect
  stress              Ramping VUs (raise rate limits)
  soak                Long steady load (default 30m)

Examples:
  ./performance/scripts/seed.sh
  ./performance/run.sh smoke
  VUS=20 DURATION=2m ./performance/run.sh redirect
  BASE_URL=http://localhost:8080 ./performance/run.sh login

Env knobs: BASE_URL, VUS, DURATION, RAMP_VUS, RAMP_UP, SUSTAIN, RAMP_DOWN,
           SOAK_VUS, SOAK_DURATION, SEED_FILE, PERF_EMAIL, PERF_PASSWORD,
           INSECURE_SKIP_TLS_VERIFY, THRESHOLD_* (see performance/README.md)

Reports: performance/reports/<scenario>-<timestamp>.{html,json}
EOF
}

if [[ -z "$SCENARIO" || "$SCENARIO" == "-h" || "$SCENARIO" == "--help" ]]; then
  usage
  exit 0
fi

SCRIPT="$ROOT/performance/scenarios/${SCENARIO}.js"
if [[ ! -f "$SCRIPT" ]]; then
  echo "Unknown scenario: ${SCENARIO}" >&2
  usage >&2
  exit 1
fi

if ! command -v k6 >/dev/null 2>&1; then
  echo "k6 is not installed. See https://k6.io/docs/get-started/installation/" >&2
  exit 1
fi

mkdir -p "$ROOT/performance/reports"
export BASE_URL="${BASE_URL:-https://localhost}"
export SEED_FILE="${SEED_FILE:-$ROOT/performance/data/seed.json}"
export INSECURE_SKIP_TLS_VERIFY="${INSECURE_SKIP_TLS_VERIFY:-true}"

# Scenarios that need seed data
case "$SCENARIO" in
  registration) ;;
  *)
    if [[ ! -f "$SEED_FILE" && -z "${PERF_EMAIL:-}" ]]; then
      echo "Missing seed file at ${SEED_FILE}." >&2
      echo "Run: ./performance/scripts/seed.sh" >&2
      echo "Or set PERF_EMAIL / PERF_PASSWORD for a pre-verified account." >&2
      exit 1
    fi
    ;;
esac

if [[ "${FLUSH_RATE_LIMITS:-false}" == "true" ]]; then
  echo "→ Flushing Redis rate_limit:* keys (FLUSH_RATE_LIMITS=true)"
  docker compose -f "$ROOT/docker-compose.yml" exec -T redis \
    redis-cli -a "${REDIS_PASSWORD:-linkflow-local-redis}" --no-auth-warning \
    EVAL "local k=redis.call('keys', KEYS[1]); for i=1,#k do redis.call('del', k[i]) end; return #k" 1 'rate_limit:*' \
    || true
fi

echo "→ k6 run ${SCENARIO}  BASE_URL=${BASE_URL}"
cd "$ROOT"
exec k6 run "$SCRIPT" "$@"

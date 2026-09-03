#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# LinkFlow — Health Check Script
# ---------------------------------------------------------------------------
# Checks readiness of all LinkFlow containers on this node.
#
# Usage: ./scripts/health-check.sh [--edge]
#
# Without flags: checks app (8081), web (8082), gateway (8080)
# With --edge:   checks nginx health endpoint on port 80
#
# Exit codes:
#   0 — all endpoints healthy
#   1 — at least one endpoint unhealthy
# ---------------------------------------------------------------------------
set -euo pipefail

FAILED=0

check_endpoint() {
    local name="$1"
    local url="$2"
    local response

    if response=$(wget -qO- --timeout=5 "$url" 2>/dev/null); then
        if echo "$response" | grep -q '"status":"UP"'; then
            echo "✓ $name: healthy"
            return 0
        else
            echo "✗ $name: unhealthy (response: $response)"
            return 1
        fi
    else
        echo "✗ $name: unreachable"
        return 1
    fi
}

check_nginx() {
    local response
    if response=$(wget -qO- --timeout=5 "http://127.0.0.1/nginx-health" 2>/dev/null); then
        if echo "$response" | grep -q "ok"; then
            echo "✓ nginx: healthy"
            return 0
        fi
    fi
    echo "✗ nginx: unhealthy"
    return 1
}

if [ "${1:-}" = "--edge" ]; then
    echo "=== LinkFlow Edge Health Check ==="
    check_nginx || FAILED=1
else
    echo "=== LinkFlow App Node Health Check ==="
    check_endpoint "linkflow-app"     "http://127.0.0.1:8081/actuator/health/readiness" || FAILED=1
    check_endpoint "linkflow-web"     "http://127.0.0.1:8082/actuator/health/readiness" || FAILED=1
    check_endpoint "linkflow-gateway" "http://127.0.0.1:8080/actuator/health/readiness" || FAILED=1
fi

echo ""
if [ "$FAILED" -eq 0 ]; then
    echo "Result: ALL HEALTHY"
else
    echo "Result: UNHEALTHY"
fi

exit "$FAILED"

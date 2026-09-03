#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# LinkFlow — Rollback Script
# ---------------------------------------------------------------------------
# Reverts an application node to its previous image tag.
#
# Usage: ./scripts/rollback.sh [<image-tag>]
#
# Without arguments: reads the previous tag from /opt/linkflow/.rollback-tag
# With argument:     uses the specified tag
#
# Exit codes:
#   0 — rollback succeeded, all containers healthy
#   1 — rollback failed or no rollback tag available
# ---------------------------------------------------------------------------
set -euo pipefail

DEPLOY_DIR="/opt/linkflow"
COMPOSE_FILE="docker-compose.ec2-app.yml"
MAX_WAIT=180
POLL_INTERVAL=10

cd "$DEPLOY_DIR"

# ── Determine rollback tag ───────────────────────────────────────────────
if [ -n "${1:-}" ]; then
    ROLLBACK_TAG="$1"
elif [ -f .rollback-tag ]; then
    ROLLBACK_TAG=$(cat .rollback-tag)
else
    echo "ERROR: No rollback tag specified and .rollback-tag not found."
    echo "This may be the first deployment — there is nothing to roll back to."
    exit 1
fi

echo "=== LinkFlow Rollback: reverting to tag=$ROLLBACK_TAG ==="
echo "  Time: $(date -u +%Y-%m-%dT%H:%M:%SZ)"

# ── Source .env for REGISTRY and AWS_REGION ───────────────────────────────
set -a
# shellcheck source=/dev/null
source .env
set +a

# ── ECR login ────────────────────────────────────────────────────────────
ECR_URL="${REGISTRY%/}"
if [ -n "$ECR_URL" ]; then
    echo "Logging into ECR: $ECR_URL"
    aws ecr get-login-password --region "${AWS_REGION}" | \
        docker login --username AWS --password-stdin "$ECR_URL" 2>&1
fi

# ── Pull and restart with rollback tag ───────────────────────────────────
export IMAGE_TAG="$ROLLBACK_TAG"

echo "Pulling images with rollback tag: $ROLLBACK_TAG"
docker compose -f "$COMPOSE_FILE" pull 2>&1

echo "Restarting containers..."
docker compose -f "$COMPOSE_FILE" up -d --remove-orphans 2>&1

# ── Wait for health ──────────────────────────────────────────────────────
echo "Waiting for containers to become healthy (timeout: ${MAX_WAIT}s)..."
SERVICES="linkflow-app linkflow-web linkflow-gateway"
ELAPSED=0

while [ "$ELAPSED" -lt "$MAX_WAIT" ]; do
    ALL_HEALTHY=true

    for SVC in $SERVICES; do
        CONTAINER_ID=$(docker compose -f "$COMPOSE_FILE" ps -q "$SVC" 2>/dev/null || true)
        if [ -z "$CONTAINER_ID" ]; then
            ALL_HEALTHY=false
            break
        fi
        HEALTH=$(docker inspect --format='{{.State.Health.Status}}' "$CONTAINER_ID" 2>/dev/null || echo "unknown")
        if [ "$HEALTH" != "healthy" ]; then
            ALL_HEALTHY=false
            break
        fi
    done

    if [ "$ALL_HEALTHY" = true ]; then
        echo ""
        echo "=== Rollback successful ==="
        echo "$ROLLBACK_TAG" > .current-tag
        echo "  Restored current tag: $ROLLBACK_TAG"
        docker compose -f "$COMPOSE_FILE" ps 2>&1
        exit 0
    fi

    printf "  [%3ds] waiting...\r" "$ELAPSED"
    sleep "$POLL_INTERVAL"
    ELAPSED=$((ELAPSED + POLL_INTERVAL))
done

echo ""
echo "ERROR: Rollback health check timeout after ${MAX_WAIT}s"
docker compose -f "$COMPOSE_FILE" ps 2>&1
docker compose -f "$COMPOSE_FILE" logs --tail=30 2>&1 || true
exit 1

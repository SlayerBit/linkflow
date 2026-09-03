#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# LinkFlow — Application Node Deployment Script
# ---------------------------------------------------------------------------
# Executed on EC2 #2/#3/#4 via AWS SSM Run Command during CI/CD.
#
# Usage: ./scripts/deploy.sh <image-tag>
#
# What it does:
#   1. Saves the current IMAGE_TAG for rollback
#   2. Pulls the latest repository (compose files, scripts, configs)
#   3. Logs into ECR using the EC2 instance profile
#   4. Pulls the new container images
#   5. Restarts containers with graceful shutdown
#   6. Waits for all containers to pass their readiness health checks
#   7. Records the new IMAGE_TAG as current
#
# Exit codes:
#   0 — all containers healthy
#   1 — health check timeout or deployment error
# ---------------------------------------------------------------------------
set -euo pipefail

IMAGE_TAG="${1:?Usage: deploy.sh <image-tag>}"
DEPLOY_DIR="/opt/linkflow"
COMPOSE_FILE="docker-compose.ec2-app.yml"
MAX_WAIT=180
POLL_INTERVAL=10

cd "$DEPLOY_DIR"

echo "=== LinkFlow Deploy: tag=$IMAGE_TAG ==="
echo "  Time: $(date -u +%Y-%m-%dT%H:%M:%SZ)"

# ── Sync repository to match origin/main exactly ─────────────────────────
# fetch + reset is deterministic: it always produces the exact state of origin/main regardless
# of local modifications. git pull would fail on local drift or diverged history.
echo "Syncing repository to origin/main..."
git fetch origin 2>&1
git checkout main 2>&1
git reset --hard origin/main 2>&1

# ── Source .env for REGISTRY and AWS_REGION ───────────────────────────────
if [ ! -f .env ]; then
    echo "ERROR: .env file not found at $DEPLOY_DIR/.env"
    exit 1
fi
set -a
# shellcheck source=/dev/null
source .env
set +a

# ── Save current tag for rollback ────────────────────────────────────────
if [ -f .current-tag ]; then
    cp .current-tag .rollback-tag
    echo "  Saved rollback tag: $(cat .rollback-tag)"
fi

# ── ECR login (instance profile provides credentials) ────────────────────
ECR_URL="${REGISTRY%/}"
if [ -n "$ECR_URL" ]; then
    echo "Logging into ECR: $ECR_URL"
    aws ecr get-login-password --region "${AWS_REGION}" | \
        docker login --username AWS --password-stdin "$ECR_URL" 2>&1
else
    echo "REGISTRY is empty, skipping ECR login (local build mode)"
fi

# ── Override IMAGE_TAG (takes precedence over .env value) ────────────────
export IMAGE_TAG

# ── Pull new images ──────────────────────────────────────────────────────
echo "Pulling images with tag: $IMAGE_TAG"
docker compose -f "$COMPOSE_FILE" pull 2>&1

# ── Restart containers (respects stop_grace_period for graceful drain) ───
echo "Starting containers..."
docker compose -f "$COMPOSE_FILE" up -d --remove-orphans 2>&1

# ── Wait for all containers to become healthy ────────────────────────────
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
        echo "=== All containers healthy ==="
        echo "$IMAGE_TAG" > .current-tag
        echo "  Recorded current tag: $IMAGE_TAG"

        # Print container status
        docker compose -f "$COMPOSE_FILE" ps 2>&1
        exit 0
    fi

    printf "  [%3ds] waiting...\r" "$ELAPSED"
    sleep "$POLL_INTERVAL"
    ELAPSED=$((ELAPSED + POLL_INTERVAL))
done

# ── Health check timeout ─────────────────────────────────────────────────
echo ""
echo "ERROR: Health check timeout after ${MAX_WAIT}s"
echo ""
echo "Container status:"
docker compose -f "$COMPOSE_FILE" ps 2>&1
echo ""
echo "Recent logs:"
docker compose -f "$COMPOSE_FILE" logs --tail=30 2>&1 || true
exit 1

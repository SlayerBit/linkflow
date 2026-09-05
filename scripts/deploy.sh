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

# ── Sync repository to match origin/main if reachable ───────────────────
# Attempt to fetch latest compose files, scripts, and configurations from GitHub.
# Uses strict connect/low-speed timeouts and retries so transient network blips
# do not hang or abort the deployment.
#
# CRITICAL INVARIANT: The requested IMAGE_TAG ($1) is immutable and always deployed
# to this node. If GitHub sync fails, the script continues using existing local
# compose configuration with the exact requested IMAGE_TAG. It NEVER falls back to 'latest'.
if [ "${LINKFLOW_DEPLOY_SYNCED:-0}" != "1" ]; then
    echo "Syncing repository to origin/main..."
    sync_success=false
    for attempt in 1 2 3; do
        echo "  Attempt $attempt: fetching origin/main..."
        if git -c http.connectTimeout=10 -c http.lowSpeedLimit=1000 -c http.lowSpeedTime=15 fetch origin main 2>&1; then
            sync_success=true
            break
        fi
        echo "  Attempt $attempt failed. Waiting before retry..."
        sleep 3
    done

    if [ "$sync_success" = true ]; then
        git checkout main 2>&1
        git reset --hard origin/main 2>&1
        echo "  Repository successfully synced to $(git rev-parse --short HEAD)"
        # Re-execute the script so bash re-opens from byte 0 in case deploy.sh itself
        # was updated by the git reset.
        export LINKFLOW_DEPLOY_SYNCED=1
        exec bash "$0" "$@"
    else
        if [ -f "$COMPOSE_FILE" ]; then
            echo "WARNING: Could not connect to GitHub to sync repository after 3 attempts."
            echo "Proceeding with existing local configuration ($COMPOSE_FILE) for IMAGE_TAG=$IMAGE_TAG..."
        else
            echo "ERROR: Failed to sync repository and $COMPOSE_FILE does not exist locally."
            exit 1
        fi
    fi
fi

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
# INVARIANT: The requested IMAGE_TAG takes precedence and is exported to docker compose.
export IMAGE_TAG
echo "Deploying target image tag: $IMAGE_TAG"

# ── Pull new images ──────────────────────────────────────────────────────
echo "Pulling images with tag: $IMAGE_TAG"
docker compose -f "$COMPOSE_FILE" pull 2>&1

# ── Restart containers (respects stop_grace_period for graceful drain) ───
echo "Starting containers with tag: $IMAGE_TAG..."
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

#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# LinkFlow — EC2 Bootstrap Script (Amazon Linux 2023)
# ---------------------------------------------------------------------------
# First-time setup for any EC2 instance (edge or application node).
# Run as root on a fresh Amazon Linux 2023 instance.
#
# Usage: sudo ./scripts/ec2-bootstrap.sh
#
# What it does:
#   1. Installs Docker CE
#   2. Installs Docker Compose v2 plugin
#   3. Verifies AWS CLI (pre-installed on Amazon Linux 2023)
#   4. Verifies SSM Agent (pre-installed on Amazon Linux 2023)
#   5. Clones the LinkFlow repository to /opt/linkflow
#   6. Creates the .env template
#
# After running this script:
#   1. Edit /opt/linkflow/.env with actual values (see .env.ec2.example)
#   2. For the edge (EC2 #1): obtain TLS certificates with certbot
#   3. Start the stack with the appropriate compose file
# ---------------------------------------------------------------------------
set -euo pipefail

REPO_URL="https://github.com/SlayerBit/linkflow.git"
DEPLOY_DIR="/opt/linkflow"
COMPOSE_VERSION="v2.29.2"

echo "=== LinkFlow EC2 Bootstrap ==="
echo "  OS: $(cat /etc/os-release | grep PRETTY_NAME | cut -d= -f2)"
echo "  Time: $(date -u +%Y-%m-%dT%H:%M:%SZ)"

# ── Docker ────────────────────────────────────────────────────────────────
echo ""
echo "--- Installing Docker ---"
dnf install -y docker
systemctl enable docker
systemctl start docker
echo "Docker version: $(docker --version)"

# ── Docker Compose v2 plugin ─────────────────────────────────────────────
echo ""
echo "--- Installing Docker Compose ${COMPOSE_VERSION} ---"
DOCKER_CLI_PLUGINS="/usr/local/lib/docker/cli-plugins"
mkdir -p "$DOCKER_CLI_PLUGINS"
curl -fsSL "https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-x86_64" \
    -o "${DOCKER_CLI_PLUGINS}/docker-compose"
chmod +x "${DOCKER_CLI_PLUGINS}/docker-compose"
echo "Docker Compose version: $(docker compose version)"

# ── AWS CLI (pre-installed on Amazon Linux 2023) ─────────────────────────
echo ""
echo "--- Verifying AWS CLI ---"
if command -v aws &>/dev/null; then
    echo "AWS CLI version: $(aws --version)"
else
    echo "ERROR: AWS CLI not found. Install it manually."
    exit 1
fi

# ── SSM Agent (pre-installed on Amazon Linux 2023) ───────────────────────
echo ""
echo "--- Verifying SSM Agent ---"
if systemctl is-active --quiet amazon-ssm-agent; then
    echo "SSM Agent: running"
else
    echo "Starting SSM Agent..."
    systemctl enable amazon-ssm-agent
    systemctl start amazon-ssm-agent
    echo "SSM Agent: started"
fi

# ── Git ──────────────────────────────────────────────────────────────────
echo ""
echo "--- Verifying Git ---"
if ! command -v git &>/dev/null; then
    echo "Installing git..."
    dnf install -y git
fi
echo "Git version: $(git --version)"

# ── Certbot (edge node only) ─────────────────────────────────────────────
echo ""
echo "--- Installing Certbot (for edge TLS) ---"
dnf install -y certbot || echo "WARN: certbot installation failed (optional, edge only)"

# ── Clone repository ─────────────────────────────────────────────────────
echo ""
echo "--- Setting up deploy directory ---"
if [ -d "$DEPLOY_DIR/.git" ]; then
    echo "Repository already exists at $DEPLOY_DIR, syncing to origin/main..."
    cd "$DEPLOY_DIR"
    git fetch origin
    git checkout main
    git reset --hard origin/main
else
    echo "Cloning repository to $DEPLOY_DIR..."
    mkdir -p "$DEPLOY_DIR"
    git clone "$REPO_URL" "$DEPLOY_DIR"
fi

# ── Create .env from template ────────────────────────────────────────────
cd "$DEPLOY_DIR"
if [ ! -f .env ]; then
    cp .env.ec2.example .env
    echo "Created .env from template. EDIT IT with actual values before starting."
else
    echo ".env already exists, not overwriting."
fi

# ── Make scripts executable ──────────────────────────────────────────────
chmod +x scripts/*.sh 2>/dev/null || true
chmod +x scripts/aws/*.sh 2>/dev/null || true

# ── Summary ──────────────────────────────────────────────────────────────
echo ""
echo "=== Bootstrap Complete ==="
echo ""
echo "Next steps:"
echo "  1. Edit $DEPLOY_DIR/.env with actual values"
echo "     - Edge (EC2 #1): Set Section A values (Redis, Grafana, app node IPs)"
echo "     - App nodes (EC2 #2-#4): Set Section B values (DB, Redis host, JWT, SMTP)"
echo ""
echo "  2. For edge node (EC2 #1):"
echo "     a. Obtain TLS certificate (domain must match DOMAIN_NAME in .env):"
echo "        certbot certonly --standalone -d yourdomain.com"
echo "     b. Start edge stack:"
echo "        cd $DEPLOY_DIR && docker compose -f docker-compose.ec2-edge.yml up -d"
echo "     c. Set up certificate auto-renewal:"
echo "        echo '0 0,12 * * * root certbot renew --quiet --deploy-hook \"docker compose -f $DEPLOY_DIR/docker-compose.ec2-edge.yml exec nginx nginx -s reload\"' >> /etc/crontab"
echo ""
echo "  3. For app nodes (EC2 #2-#4):"
echo "     a. Set REGISTRY and AWS_REGION in .env"
echo "     b. First deploy: ./scripts/deploy.sh latest"
echo "        Or local build: docker compose -f docker-compose.ec2-app.yml up --build -d"

#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# LinkFlow — Create ECR Repositories
# ---------------------------------------------------------------------------
# Creates the 3 ECR repositories for LinkFlow container images with lifecycle
# policies that retain the 20 most recent tagged images and expire untagged
# images after 7 days.
#
# Usage: ./scripts/aws/create-ecr-repos.sh [--region REGION]
#
# Prerequisites: AWS CLI configured with permissions to create ECR resources.
# ---------------------------------------------------------------------------
set -euo pipefail

REGION="${1:-${AWS_REGION:-ap-south-1}}"
REPOS=("linkflow-app" "linkflow-gateway" "linkflow-web")

# ECR lifecycle policy: keep the 20 most recent tagged images, expire
# untagged images after 7 days. This prevents unbounded growth from CI/CD
# pushes while keeping enough history for rollbacks.
LIFECYCLE_POLICY='{
  "rules": [
    {
      "rulePriority": 1,
      "description": "Expire untagged images after 7 days",
      "selection": {
        "tagStatus": "untagged",
        "countType": "sinceImagePushed",
        "countUnit": "days",
        "countNumber": 7
      },
      "action": {
        "type": "expire"
      }
    },
    {
      "rulePriority": 2,
      "description": "Keep only the 20 most recent tagged images",
      "selection": {
        "tagStatus": "tagged",
        "tagPrefixList": ["latest", "sha-"],
        "countType": "imageCountMoreThan",
        "countNumber": 20
      },
      "action": {
        "type": "expire"
      }
    }
  ]
}'

echo "=== Creating ECR Repositories ==="
echo "  Region: $REGION"
echo ""

for REPO in "${REPOS[@]}"; do
    echo "--- $REPO ---"

    # Create repository (ignore error if it already exists)
    if aws ecr create-repository \
        --repository-name "$REPO" \
        --region "$REGION" \
        --image-scanning-configuration scanOnPush=true \
        --image-tag-mutability MUTABLE \
        --query 'repository.repositoryUri' \
        --output text 2>/dev/null; then
        echo "  Created: $REPO"
    else
        echo "  Already exists: $REPO"
    fi

    # Apply lifecycle policy
    aws ecr put-lifecycle-policy \
        --repository-name "$REPO" \
        --region "$REGION" \
        --lifecycle-policy-text "$LIFECYCLE_POLICY" \
        --query 'lifecyclePolicyText' \
        --output text >/dev/null 2>&1
    echo "  Lifecycle policy applied"
    echo ""
done

# Print registry URL
ACCOUNT_ID=$(aws sts get-caller-identity --query 'Account' --output text)
echo "=== ECR Setup Complete ==="
echo ""
echo "Registry URL: ${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"
echo ""
echo "Set this in GitHub Secrets:"
echo "  ECR_REGISTRY = ${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"
echo ""
echo "Set this in .env on app nodes (trailing slash!):"
echo "  REGISTRY=${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com/"

#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# LinkFlow — GitHub OIDC Federation Setup
# ---------------------------------------------------------------------------
# Configures AWS IAM for keyless GitHub Actions authentication.
#
# Creates:
#   1. OIDC identity provider for token.actions.githubusercontent.com
#   2. IAM role with trust policy scoped to the LinkFlow repository
#   3. Permissions policy for ECR push + SSM send-command
#
# Usage: ./scripts/aws/github-oidc-setup.sh [--region REGION] [--repo OWNER/REPO]
#
# Prerequisites: AWS CLI configured with IAM admin permissions.
#
# After running, set the output role ARN as a GitHub Secret:
#   AWS_ROLE_ARN = arn:aws:iam::<account>:role/linkflow-github-actions
# ---------------------------------------------------------------------------
set -euo pipefail

REGION="${AWS_REGION:-ap-south-1}"
REPO="${GITHUB_REPO:-SlayerBit/linkflow}"
ROLE_NAME="linkflow-github-actions"
POLICY_NAME="linkflow-github-actions-policy"
INSTANCE_ROLE_NAME="linkflow-ec2-instance"
INSTANCE_PROFILE_NAME="linkflow-ec2-instance"

# Parse arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        --region) REGION="$2"; shift 2 ;;
        --repo) REPO="$2"; shift 2 ;;
        *) echo "Unknown argument: $1"; exit 1 ;;
    esac
done

ACCOUNT_ID=$(aws sts get-caller-identity --query 'Account' --output text)
OIDC_PROVIDER_URL="token.actions.githubusercontent.com"
OIDC_PROVIDER_ARN="arn:aws:iam::${ACCOUNT_ID}:oidc-provider/${OIDC_PROVIDER_URL}"

echo "=== GitHub OIDC Federation Setup ==="
echo "  Account:  $ACCOUNT_ID"
echo "  Region:   $REGION"
echo "  Repo:     $REPO"
echo ""

# ── Step 1: Create OIDC Provider ─────────────────────────────────────────
echo "--- Step 1: OIDC Provider ---"
if aws iam get-open-id-connect-provider --open-id-connect-provider-arn "$OIDC_PROVIDER_ARN" &>/dev/null; then
    echo "  OIDC provider already exists"
else
    # AWS manages the GitHub certificate chain since July 2023. The thumbprint
    # parameter is required by the API but is not validated for GitHub.
    aws iam create-open-id-connect-provider \
        --url "https://${OIDC_PROVIDER_URL}" \
        --thumbprint-list "ffffffffffffffffffffffffffffffffffffffff" \
        --client-id-list "sts.amazonaws.com" \
        --query 'OpenIDConnectProviderArn' --output text
    echo "  Created OIDC provider"
fi
echo ""

# ── Step 2: Create GitHub Actions Role ───────────────────────────────────
echo "--- Step 2: GitHub Actions IAM Role ---"

# Trust policy: only the main branch of the specified repository can assume this role.
TRUST_POLICY=$(cat <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "${OIDC_PROVIDER_ARN}"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "${OIDC_PROVIDER_URL}:aud": "sts.amazonaws.com"
        },
        "StringLike": {
          "${OIDC_PROVIDER_URL}:sub": [
            "repo:${REPO}:ref:refs/heads/main",
            "repo:${REPO}:environment:production"
          ]
        }
      }
    }
  ]
}
EOF
)

if aws iam get-role --role-name "$ROLE_NAME" &>/dev/null; then
    echo "  Role $ROLE_NAME already exists, updating trust policy..."
    aws iam update-assume-role-policy \
        --role-name "$ROLE_NAME" \
        --policy-document "$TRUST_POLICY"
else
    aws iam create-role \
        --role-name "$ROLE_NAME" \
        --assume-role-policy-document "$TRUST_POLICY" \
        --description "GitHub Actions OIDC role for LinkFlow CI/CD" \
        --query 'Role.Arn' --output text
    echo "  Created role: $ROLE_NAME"
fi

# Attach permissions policy
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
POLICY_ARN="arn:aws:iam::${ACCOUNT_ID}:policy/${POLICY_NAME}"

if aws iam get-policy --policy-arn "$POLICY_ARN" &>/dev/null; then
    echo "  Updating existing policy..."
    # Create a new version and set as default
    aws iam create-policy-version \
        --policy-arn "$POLICY_ARN" \
        --policy-document "file://${SCRIPT_DIR}/iam-github-actions-policy.json" \
        --set-as-default \
        --query 'PolicyVersion.VersionId' --output text >/dev/null
else
    aws iam create-policy \
        --policy-name "$POLICY_NAME" \
        --policy-document "file://${SCRIPT_DIR}/iam-github-actions-policy.json" \
        --description "ECR push + SSM send-command for LinkFlow deployments" \
        --query 'Policy.Arn' --output text >/dev/null
    echo "  Created policy: $POLICY_NAME"
fi

aws iam attach-role-policy \
    --role-name "$ROLE_NAME" \
    --policy-arn "$POLICY_ARN" 2>/dev/null || true
echo "  Policy attached"
echo ""

# ── Step 3: Create EC2 Instance Role ─────────────────────────────────────
echo "--- Step 3: EC2 Instance Role ---"

EC2_POLICY_ARN="arn:aws:iam::${ACCOUNT_ID}:policy/${INSTANCE_ROLE_NAME}-policy"

if aws iam get-role --role-name "$INSTANCE_ROLE_NAME" &>/dev/null; then
    echo "  Instance role already exists"
else
    aws iam create-role \
        --role-name "$INSTANCE_ROLE_NAME" \
        --assume-role-policy-document "file://${SCRIPT_DIR}/ssm-trust-policy.json" \
        --description "EC2 instance role for LinkFlow (SSM + ECR pull)" \
        --query 'Role.Arn' --output text >/dev/null
    echo "  Created role: $INSTANCE_ROLE_NAME"
fi

# Attach SSM managed policy
aws iam attach-role-policy \
    --role-name "$INSTANCE_ROLE_NAME" \
    --policy-arn "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore" 2>/dev/null || true
echo "  Attached AmazonSSMManagedInstanceCore"

# Create and attach ECR pull + CloudWatch policy
if aws iam get-policy --policy-arn "$EC2_POLICY_ARN" &>/dev/null; then
    aws iam create-policy-version \
        --policy-arn "$EC2_POLICY_ARN" \
        --policy-document "file://${SCRIPT_DIR}/iam-ec2-instance-policy.json" \
        --set-as-default \
        --query 'PolicyVersion.VersionId' --output text >/dev/null 2>&1 || true
else
    aws iam create-policy \
        --policy-name "${INSTANCE_ROLE_NAME}-policy" \
        --policy-document "file://${SCRIPT_DIR}/iam-ec2-instance-policy.json" \
        --description "ECR pull + CloudWatch Logs for LinkFlow EC2 instances" \
        --query 'Policy.Arn' --output text >/dev/null
fi
aws iam attach-role-policy \
    --role-name "$INSTANCE_ROLE_NAME" \
    --policy-arn "$EC2_POLICY_ARN" 2>/dev/null || true
echo "  Attached ECR pull + CloudWatch policy"

# Create instance profile (if not exists)
if aws iam get-instance-profile --instance-profile-name "$INSTANCE_PROFILE_NAME" &>/dev/null; then
    echo "  Instance profile already exists"
else
    aws iam create-instance-profile \
        --instance-profile-name "$INSTANCE_PROFILE_NAME" >/dev/null
    aws iam add-role-to-instance-profile \
        --instance-profile-name "$INSTANCE_PROFILE_NAME" \
        --role-name "$INSTANCE_ROLE_NAME"
    echo "  Created instance profile: $INSTANCE_PROFILE_NAME"
fi
echo ""

# ── Summary ──────────────────────────────────────────────────────────────
GITHUB_ROLE_ARN="arn:aws:iam::${ACCOUNT_ID}:role/${ROLE_NAME}"

echo "=== Setup Complete ==="
echo ""
echo "GitHub Secrets to configure:"
echo "  AWS_ROLE_ARN  = ${GITHUB_ROLE_ARN}"
echo "  AWS_REGION    = ${REGION}"
echo "  ECR_REGISTRY  = ${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"
echo ""
echo "EC2 Instance Profile to attach to all 4 instances:"
echo "  ${INSTANCE_PROFILE_NAME}"
echo ""
echo "Remaining GitHub Secrets (set to EC2 instance IDs):"
echo "  EC2_EDGE_INSTANCE_ID  = i-0xxxxxxxxxxxx"
echo "  EC2_APP1_INSTANCE_ID  = i-0xxxxxxxxxxxx"
echo "  EC2_APP2_INSTANCE_ID  = i-0xxxxxxxxxxxx"
echo "  EC2_APP3_INSTANCE_ID  = i-0xxxxxxxxxxxx"

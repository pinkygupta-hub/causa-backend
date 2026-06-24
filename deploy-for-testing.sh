#!/bin/bash

# Deploy RCA changes to OpenShift for testing
# This script builds and deploys the rca-llm-integration branch

set -e

echo "======================================"
echo "Deploy RCA for Testing"
echo "======================================"
echo ""

# Configuration
IMAGE_REPO="quay.io/rh-ee-shesaxen/causa-backend"
IMAGE_TAG="rca-testing-$(date +%Y%m%d_%H%M%S)"
KUSTOMIZE_DIR="/tmp/causa-deploy-pinky"
NAMESPACE="pinky"

# Check prerequisites
echo "Checking prerequisites..."

# Check if logged into OpenShift
oc whoami &>/dev/null || { echo "Error: Not logged into OpenShift"; exit 1; }
echo "✅ OpenShift: $(oc whoami)"

# Check if logged into Quay
docker login quay.io &>/dev/null || { echo "Warning: Not logged into quay.io"; }
echo "✅ Docker ready"

# Check current branch
CURRENT_BRANCH=$(git branch --show-current)
echo "Current branch: $CURRENT_BRANCH"

if [ "$CURRENT_BRANCH" != "rca-llm-integration" ]; then
    read -p "Not on rca-llm-integration branch. Continue anyway? (y/N): " confirm
    if [ "$confirm" != "y" ] && [ "$confirm" != "Y" ]; then
        echo "Aborted. Please switch to rca-llm-integration branch."
        exit 1
    fi
fi
echo ""

# Step 1: Build the application
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Step 1: Building Application"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

mvn clean package -DskipTests

echo ""
echo "✅ Application built successfully"
echo ""

# Step 2: Build Docker image
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Step 2: Building Docker Image"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Image: $IMAGE_REPO:$IMAGE_TAG"
echo ""

docker build -t "$IMAGE_REPO:$IMAGE_TAG" .

echo ""
echo "✅ Docker image built successfully"
echo ""

# Step 3: Push image
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Step 3: Pushing Image to Registry"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

docker push "$IMAGE_REPO:$IMAGE_TAG"

echo ""
echo "✅ Image pushed successfully"
echo ""

# Step 4: Update kustomization
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Step 4: Updating Kustomization"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

if [ ! -d "$KUSTOMIZE_DIR" ]; then
    echo "Error: Kustomization directory not found: $KUSTOMIZE_DIR"
    echo "Please create it first or update the KUSTOMIZE_DIR variable"
    exit 1
fi

# Backup original kustomization
cp "$KUSTOMIZE_DIR/kustomization.yaml" "$KUSTOMIZE_DIR/kustomization.yaml.backup"

# Update image tag
sed -i.tmp "s/newTag:.*/newTag: $IMAGE_TAG/" "$KUSTOMIZE_DIR/kustomization.yaml"
rm -f "$KUSTOMIZE_DIR/kustomization.yaml.tmp"

echo "Updated kustomization.yaml with tag: $IMAGE_TAG"
echo ""
echo "✅ Kustomization updated"
echo ""

# Step 5: Deploy to OpenShift
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Step 5: Deploying to OpenShift"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

oc apply -k "$KUSTOMIZE_DIR"

echo ""
echo "Waiting for rollout to complete..."
oc rollout status deployment/causa-backend -n $NAMESPACE --timeout=5m

echo ""
echo "✅ Deployment complete"
echo ""

# Step 6: Verify deployment
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Step 6: Verifying Deployment"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Get pod status
echo "Pods:"
oc get pods -n $NAMESPACE -l app.kubernetes.io/name=causa-backend

echo ""

# Get route
ROUTE=$(oc get route -n $NAMESPACE causa-backend -o jsonpath='{.spec.host}')
echo "Route: https://$ROUTE"

echo ""

# Test health endpoint
echo "Testing health endpoint..."
HEALTH=$(curl -k -s "https://$ROUTE/q/health/live" | jq -r '.status' 2>/dev/null)
if [ "$HEALTH" == "UP" ]; then
    echo "✅ Health check: UP"
else
    echo "⚠️  Health check: $HEALTH"
fi

echo ""

# Check recent logs
echo "Recent logs (last 20 lines):"
oc logs -n $NAMESPACE -l app.kubernetes.io/name=causa-backend --tail=20

echo ""
echo "======================================"
echo "Deployment Summary"
echo "======================================"
echo ""
echo "Image: $IMAGE_REPO:$IMAGE_TAG"
echo "Namespace: $NAMESPACE"
echo "Route: https://$ROUTE"
echo ""
echo "Next steps:"
echo "1. Run test scenarios: ./test-rca-scenarios.sh"
echo "2. View logs: oc logs -n $NAMESPACE -l app.kubernetes.io/name=causa-backend -f"
echo "3. Test manually: curl -k https://$ROUTE/q/health/live"
echo ""
echo "To rollback:"
echo "  cp $KUSTOMIZE_DIR/kustomization.yaml.backup $KUSTOMIZE_DIR/kustomization.yaml"
echo "  oc apply -k $KUSTOMIZE_DIR"
echo ""

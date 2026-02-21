#!/bin/bash

# Test script for GitHub workflow integration
# This script registers a GitHub workflow and triggers its execution


# ── Resolve project root (works from any directory) ──────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"
# ─────────────────────────────────────────────────────────────────────────────

set -e

BASE_URL="http://localhost:8080"
WORKFLOW_FILE="data/github/github_test-calc-deployment.yaml"

# Check if GITHUB_TOKEN is set
if [ -z "$GITHUB_TOKEN" ]; then
    echo "⚠️  ERROR: GITHUB_TOKEN environment variable is not set"
    echo "Please set it with: export GITHUB_TOKEN='your_github_token'"
    exit 1
fi

echo "🚀 Starting GitHub Workflow Test"
echo "================================="
echo ""

# Step 1: Register the workflow
echo "📝 Step 1: Registering GitHub workflow from $WORKFLOW_FILE"
REGISTER_RESPONSE=$(curl -s -X POST \
  -H "Content-Type: text/yaml" \
  --data-binary @"$WORKFLOW_FILE" \
  "$BASE_URL/api/workflows")

WORKFLOW_ID=$(echo "$REGISTER_RESPONSE" | jq -r '.workflowId')

if [ "$WORKFLOW_ID" = "null" ] || [ -z "$WORKFLOW_ID" ]; then
    echo "❌ Failed to register workflow"
    echo "Response: $REGISTER_RESPONSE"
    exit 1
fi

echo "✅ Workflow registered successfully with ID: $WORKFLOW_ID"
echo ""

# Step 2: Trigger the workflow
echo "▶️  Step 2: Triggering workflow execution"
TRIGGER_RESPONSE=$(curl -s -X POST "$BASE_URL/api/workflows/$WORKFLOW_ID/run")
RUN_ID=$(echo "$TRIGGER_RESPONSE" | jq -r '.id')

if [ "$RUN_ID" = "null" ] || [ -z "$RUN_ID" ]; then
    echo "❌ Failed to trigger workflow"
    echo "Response: $TRIGGER_RESPONSE"
    exit 1
fi

echo "✅ Workflow triggered successfully with Run ID: $RUN_ID"
echo ""

# Step 3: Monitor execution
echo "⏳ Step 3: Monitoring workflow execution"
echo "Press Ctrl+C to stop monitoring (workflow will continue running)"
echo ""

SECONDS_ELAPSED=0
MAX_WAIT=600  # 10 minutes max

while [ $SECONDS_ELAPSED -lt $MAX_WAIT ]; do
    STATUS_RESPONSE=$(curl -s "$BASE_URL/api/runs/$RUN_ID")
    STATUS=$(echo "$STATUS_RESPONSE" | jq -r '.status')
    
    echo "[$SECONDS_ELAPSED s] Status: $STATUS"
    
    if [ "$STATUS" = "COMPLETED" ]; then
        echo ""
        echo "✅ Workflow completed successfully!"
        echo ""
        echo "Full response:"
        echo "$STATUS_RESPONSE" | jq '.'
        exit 0
    elif [ "$STATUS" = "FAILED" ]; then
        echo ""
        echo "❌ Workflow failed!"
        echo ""
        echo "Full response:"
        echo "$STATUS_RESPONSE" | jq '.'
        exit 1
    fi
    
    sleep 5
    SECONDS_ELAPSED=$((SECONDS_ELAPSED + 5))
done

echo ""
echo "⏱️  Timeout reached (${MAX_WAIT}s). Workflow may still be running."
echo "Check status manually: curl $BASE_URL/api/runs/$RUN_ID | jq '.'"

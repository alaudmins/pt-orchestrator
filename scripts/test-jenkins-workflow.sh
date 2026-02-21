#!/bin/bash

# Test script for Jenkins workflow integration
# This script registers a Jenkins workflow and triggers its execution


# ── Resolve project root (works from any directory) ──────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"
# ─────────────────────────────────────────────────────────────────────────────

set -e

BASE_URL="http://localhost:8080"
WORKFLOW_FILE="data/jenkins/jenkins_test-failure.yaml"

# Check if JENKINS_TOKEN is set
if [ -z "$JENKINS_TOKEN" ]; then
    echo "⚠️  ERROR: JENKINS_TOKEN environment variable is not set"
    echo "Please set it with: export JENKINS_TOKEN='your_jenkins_token'"
    exit 1
fi

# Set defaults for optional env vars
export JENKINS_URL="${JENKINS_URL:-http://localhost:9090}"
export JENKINS_USER="${JENKINS_USER:-pt-orch}"

echo "🚀 Starting Jenkins Workflow Test"
echo "=================================="
echo "Jenkins URL: $JENKINS_URL"
echo "Jenkins User: $JENKINS_USER"
echo ""

# Step 1: Register the workflow
echo "📝 Step 1: Registering Jenkins workflow from $WORKFLOW_FILE"
REGISTER_RESPONSE=$(curl -s -X POST \
  -H "Content-Type: text/yaml" \
  --data-binary @"$WORKFLOW_FILE" \
  "$BASE_URL/api/workflows")

# Extract workflow ID using jq
WORKFLOW_ID=$(echo "$REGISTER_RESPONSE" | jq -r '.workflowId' 2>/dev/null)

if [ "$WORKFLOW_ID" = "null" ] || [ -z "$WORKFLOW_ID" ]; then
    echo "❌ Failed to register workflow"
    echo "Response (first 500 chars):"
    echo "$REGISTER_RESPONSE" | head -c 500
    exit 1
fi

echo "✅ Workflow registered successfully with ID: $WORKFLOW_ID"
echo ""

# Step 2: Trigger the workflow
echo "▶️  Step 2: Triggering workflow execution"
TRIGGER_RESPONSE=$(curl -s -X POST "$BASE_URL/api/workflows/$WORKFLOW_ID/run")
RUN_ID=$(echo "$TRIGGER_RESPONSE" | jq -r '.runId')

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
MAX_WAIT=300  # 5 minutes max (Jenkins jobs are typically faster)

while [ $SECONDS_ELAPSED -lt $MAX_WAIT ]; do
    STATUS_RESPONSE=$(curl -s "$BASE_URL/api/runs/$RUN_ID")
    STATUS=$(echo "$STATUS_RESPONSE" | jq -r '.status')
    
    echo "[$SECONDS_ELAPSED s] Status: $STATUS"
    
    if [ "$STATUS" = "COMPLETED" ] || [ "$STATUS" = "SUCCESS" ]; then
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
echo "⏱️  Timeout reached (${MAX_WAIT}s). Workflow may still be running."\n"Check status manually: curl $BASE_URL/api/runs/$RUN_ID | jq '.'"

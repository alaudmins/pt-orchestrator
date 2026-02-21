#!/usr/bin/env bash
# =============================================================================
# test-github-parallel-workflow.sh
#
# Tests parallel execution with GitHub Actions:
#   - Registers github_test-parallel.yaml (PARALLEL stage, 2 steps)
#   - Step 1 triggers parallel-job-a.yml  (15s sleep)
#   - Step 2 triggers parallel-job-b.yml  (15s sleep)
#
# If parallel, total time ≈ 60-90s (GitHub runner startup + work).
# If sequential, total time ≈ 130s+.
#
# Usage:
#   GITHUB_TOKEN=<token> bash test-github-parallel-workflow.sh
# =============================================================================


# ── Resolve project root (works from any directory) ──────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"
# ─────────────────────────────────────────────────────────────────────────────

ORCHESTRATOR_URL="${ORCHESTRATOR_URL:-http://localhost:8080}"
WORKFLOW_FILE="data/github/github_test-parallel.yaml"
POLL_INTERVAL=10
MAX_WAIT=600

echo "🚀 GitHub Parallel Workflow Test"
echo "================================="
echo "Orchestrator : ${ORCHESTRATOR_URL}"
echo "Workflow     : ${WORKFLOW_FILE}"
echo "Token        : resolved from secrets store (POST /api/secrets)"
echo ""

# Step 1 — Register
echo "📝 Step 1: Registering GitHub parallel workflow"
REGISTER_RESPONSE=$(GITHUB_TOKEN="${GITHUB_TOKEN}" curl -s -X POST \
    -H "Content-Type: text/yaml" \
    --data-binary "@${WORKFLOW_FILE}" \
    "${ORCHESTRATOR_URL}/api/workflows")

WORKFLOW_ID=$(echo "${REGISTER_RESPONSE}" | python3 -c "import sys,json; print(json.load(sys.stdin).get('workflowId',''))" 2>/dev/null)

if [ -z "${WORKFLOW_ID}" ]; then
    echo "❌ Failed to register workflow"
    echo "${REGISTER_RESPONSE}"
    exit 1
fi
echo "✅ Registered: ${WORKFLOW_ID}"
echo ""

# Step 2 — Trigger
echo "▶️  Step 2: Triggering run"
TRIGGER_RESPONSE=$(curl -s -X POST "${ORCHESTRATOR_URL}/api/workflows/${WORKFLOW_ID}/run")
RUN_ID=$(echo "${TRIGGER_RESPONSE}" | python3 -c "import sys,json; print(json.load(sys.stdin).get('runId',''))" 2>/dev/null)

if [ -z "${RUN_ID}" ]; then
    echo "❌ Failed to trigger workflow"
    echo "${TRIGGER_RESPONSE}"
    exit 1
fi
echo "✅ Run ID: ${RUN_ID}"
echo ""

# Step 3 — Monitor
echo "⏳ Step 3: Monitoring (max ${MAX_WAIT}s)"
echo "   Parallel: ~60-90s (GitHub runner start + 15s work × 1)"
echo "   Sequential: ~130s+ (two runs back-to-back)"
echo ""

START_TIME=$(date +%s)

while true; do
    STATUS_RESPONSE=$(curl -s "${ORCHESTRATOR_URL}/api/runs/${RUN_ID}")
    STATUS=$(echo "${STATUS_RESPONSE}" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',''))" 2>/dev/null)
    ELAPSED=$(( $(date +%s) - START_TIME ))

    echo "[${ELAPSED}s] Status: ${STATUS}"

    if [ "${STATUS}" = "SUCCESS" ]; then
        echo ""
        echo "🎉 GitHub parallel workflow completed in ${ELAPSED}s!"
        if [ "${ELAPSED}" -lt 120 ]; then
            echo "✅ PARALLEL CONFIRMED — completed in ${ELAPSED}s (< 120s sequential threshold)"
        else
            echo "⚠️  Completed in ${ELAPSED}s — may not have run in parallel"
        fi
        echo ""
        echo "${STATUS_RESPONSE}" | python3 -m json.tool
        exit 0
    elif [ "${STATUS}" = "FAILED" ]; then
        echo ""
        echo "❌ Workflow FAILED after ${ELAPSED}s"
        echo ""
        echo "${STATUS_RESPONSE}" | python3 -m json.tool
        exit 1
    fi

    if [ "${ELAPSED}" -ge "${MAX_WAIT}" ]; then
        echo "⏰ Timeout after ${MAX_WAIT}s"
        exit 1
    fi

    sleep "${POLL_INTERVAL}"
done

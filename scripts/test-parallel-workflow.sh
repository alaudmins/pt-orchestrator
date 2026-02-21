#!/usr/bin/env bash
# =============================================================================
# test-parallel-workflow.sh
#
# Tests the parallel execution by running jenkins_test-parallel.yaml.
# Both parallel steps sleep 15s — if they run in parallel, total time ≈ 20-25s.
# If sequential, total time would be ≈ 40s+.
#
# Prerequisites:
#   - Orchestrator running on localhost:8080
#   - Jenkins running with parallel-job-a and parallel-job-b
#     (run: bash setup-jenkins-parallel-jobs.sh first)
#
# Usage:
#   JENKINS_TOKEN=<token> JENKINS_URL=http://localhost:9090 JENKINS_USER=pt-orch \
#   bash test-parallel-workflow.sh
# =============================================================================


# ── Resolve project root (works from any directory) ──────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"
# ─────────────────────────────────────────────────────────────────────────────

ORCHESTRATOR_URL="${ORCHESTRATOR_URL:-http://localhost:8080}"
JENKINS_URL="${JENKINS_URL:-http://localhost:9090}"
JENKINS_USER="${JENKINS_USER:-pt-orch}"
JENKINS_TOKEN="${JENKINS_TOKEN:?Please set JENKINS_TOKEN}"
WORKFLOW_FILE="data/jenkins/jenkins_test-parallel.yaml"
POLL_INTERVAL=5
MAX_WAIT=300

echo "🚀 Parallel Workflow Test"
echo "========================="
echo "Orchestrator : ${ORCHESTRATOR_URL}"
echo "Jenkins URL  : ${JENKINS_URL}"
echo ""

# Step 1 — Register workflow
echo "📝 Step 1: Registering parallel workflow from ${WORKFLOW_FILE}"
REGISTER_RESPONSE=$(curl -s -X POST \
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
echo "▶️  Step 2: Triggering workflow run"
TRIGGER_RESPONSE=$(curl -s -X POST \
    "${ORCHESTRATOR_URL}/api/workflows/${WORKFLOW_ID}/run")

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
echo "   Expecting completion in ~25s if parallel ✅"
echo "   (Would take ~40s+ if accidentally sequential)"
echo ""

START_TIME=$(date +%s)
ELAPSED=0

while [ "${ELAPSED}" -lt "${MAX_WAIT}" ]; do
    STATUS_RESPONSE=$(curl -s "${ORCHESTRATOR_URL}/api/runs/${RUN_ID}")
    STATUS=$(echo "${STATUS_RESPONSE}" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',''))" 2>/dev/null)
    ELAPSED=$(( $(date +%s) - START_TIME ))

    echo "[${ELAPSED}s] Status: ${STATUS}"

    if [ "${STATUS}" = "SUCCESS" ]; then
        echo ""
        echo "🎉 Parallel workflow completed in ${ELAPSED}s!"
        if [ "${ELAPSED}" -lt 35 ]; then
            echo "✅ PARALLEL CONFIRMED — completed in ${ELAPSED}s (< 35s threshold)"
        else
            echo "⚠️  Completed in ${ELAPSED}s — may not have run in parallel (threshold: 35s)"
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

    sleep "${POLL_INTERVAL}"
done

echo "⏰ Timeout after ${MAX_WAIT}s"
exit 1

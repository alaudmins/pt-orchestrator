#!/usr/bin/env bash
# =============================================================================
# test-e2e-secrets-flow.sh
#
# End-to-end test for the pt-orchestrator secrets store + all workflows.
#
# What this covers:
#   1. Stores credentials in secrets store (POST /api/secrets)
#   2. Jenkins  SEQUENTIAL — calculator-pipeline (MULTIPLY then ADD)
#   3. Jenkins  PARALLEL   — parallel-job-a + parallel-job-b simultaneously
#   4. GitHub   SEQUENTIAL — calc-deployment   (build-deploy.yml, dev env)
#   5. GitHub   SEQUENTIAL — staging-deployment (build-deploy.yml, staging)
#   6. GitHub   SEQUENTIAL — prod-deployment    (build-deploy.yml, prod)
#   7. GitHub   PARALLEL   — parallel-job-a.yml + parallel-job-b.yml simultaneously
#   8. Final pass/fail summary
#
# Usage:
#   GITHUB_TOKEN="ghp_xxx" \
#   JENKINS_TOKEN="your-jenkins-api-token" \
#   bash test-e2e-secrets-flow.sh
#
# Optional overrides:
#   ORCHESTRATOR_URL   (default: http://localhost:8080)
#   JENKINS_URL        (default: http://localhost:9090)
#   JENKINS_USER       (default: pt-orch)
#   MAX_WAIT_FAST      (default: 120s  — Jenkins jobs, parallel steps)
#   MAX_WAIT_SLOW      (default: 600s  — GitHub build-deploy.yml with Maven)
# =============================================================================


# ── Resolve project root (works from any directory) ──────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"
# ─────────────────────────────────────────────────────────────────────────────

set -euo pipefail

# ── Configuration ─────────────────────────────────────────────────────────────
ORCHESTRATOR_URL="${ORCHESTRATOR_URL:-http://localhost:8080}"
JENKINS_URL="${JENKINS_URL:-http://localhost:9090}"
JENKINS_USER="${JENKINS_USER:-pt-orch}"
MAX_WAIT_FAST="${MAX_WAIT_FAST:-120}"   # Jenkins + parallel steps
MAX_WAIT_SLOW="${MAX_WAIT_SLOW:-600}"   # GitHub build-deploy.yml (Maven build)
POLL_INTERVAL=10

GITHUB_TOKEN="${GITHUB_TOKEN:?Please export GITHUB_TOKEN=<your-github-pat>}"
JENKINS_TOKEN="${JENKINS_TOKEN:?Please export JENKINS_TOKEN=<your-jenkins-api-token>}"

PASS=0
FAIL=0

# ── Helpers ───────────────────────────────────────────────────────────────────
ok()      { echo "  ✅ $*"; }
fail()    { echo "  ❌ $*"; }
info()    { echo "  ℹ️  $*"; }
section() { echo ""; echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"; echo "  $*"; echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"; }

_json_field() { python3 -c "import sys,json; print(json.load(sys.stdin).get('$1',''))" 2>/dev/null; }

store_secret() {
    curl -sf -X POST "${ORCHESTRATOR_URL}/api/secrets" \
        -H "Content-Type: application/json" \
        -d "{\"name\":\"$1\",\"value\":\"$2\",\"description\":\"$3\"}"
}

register_workflow() {
    curl -sf -X POST -H "Content-Type: text/yaml" \
        --data-binary "@$1" "${ORCHESTRATOR_URL}/api/workflows"
}

trigger_run() {
    curl -sf -X POST "${ORCHESTRATOR_URL}/api/workflows/$1/run"
}

get_status() {
    curl -sf "${ORCHESTRATOR_URL}/api/runs/$1" | _json_field status
}

list_secrets() {
    curl -sf "${ORCHESTRATOR_URL}/api/secrets"
}

# run_workflow <label> <yaml_file> <max_wait_seconds>
# Registers, triggers, and polls until SUCCESS / FAILED / timeout.
run_workflow() {
    local label="$1" file="$2" max_wait="$3"
    local workflow_id run_id status elapsed start_time

    echo "  📝 Registering from ${file}"
    local reg
    reg=$(register_workflow "${file}")
    workflow_id=$(echo "${reg}" | _json_field workflowId)
    if [ -z "${workflow_id}" ]; then
        fail "Failed to register ${label} — response: ${reg}"
        FAIL=$(( FAIL + 1 ))
        return 1
    fi
    ok "Registered → ${workflow_id}"

    echo "  ▶️  Triggering run"
    local trig
    trig=$(trigger_run "${workflow_id}")
    run_id=$(echo "${trig}" | _json_field runId)
    if [ -z "${run_id}" ]; then
        fail "Failed to trigger ${label} — response: ${trig}"
        FAIL=$(( FAIL + 1 ))
        return 1
    fi
    ok "Run ID → ${run_id}"

    echo "  ⏳ Polling (max ${max_wait}s) …"
    start_time=$(date +%s)
    while true; do
        status=$(get_status "${run_id}")
        elapsed=$(( $(date +%s) - start_time ))
        echo "  [${elapsed}s] Status: ${status}"

        case "${status}" in
            SUCCESS)
                ok "${label} ✅  completed in ${elapsed}s"
                PASS=$(( PASS + 1 ))
                return 0
                ;;
            FAILED)
                fail "${label} ❌  FAILED after ${elapsed}s"
                FAIL=$(( FAIL + 1 ))
                return 1
                ;;
        esac

        if [ "${elapsed}" -ge "${max_wait}" ]; then
            fail "${label} ⏰  timed out after ${max_wait}s (status: ${status})"
            FAIL=$(( FAIL + 1 ))
            return 1
        fi
        sleep "${POLL_INTERVAL}"
    done
}

# ══════════════════════════════════════════════════════════════════════════════
echo ""
echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║     pt-orchestrator — End-to-End Secrets + Workflow Test      ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo "  Orchestrator : ${ORCHESTRATOR_URL}"
echo "  Jenkins      : ${JENKINS_URL}"
echo "  Fast timeout : ${MAX_WAIT_FAST}s   (Jenkins / parallel)"
echo "  Slow timeout : ${MAX_WAIT_SLOW}s  (GitHub build-deploy.yml)"

# ── 0. Health check ───────────────────────────────────────────────────────────
section "0 / Health Check"
if curl -sf "${ORCHESTRATOR_URL}/api/workflows" > /dev/null; then
    ok "Orchestrator is up at ${ORCHESTRATOR_URL}"
else
    fail "Orchestrator unreachable. Is the container running?"
    exit 1
fi

# ── 1. Store secrets ──────────────────────────────────────────────────────────
section "1 / Store Secrets  (POST /api/secrets)"

echo "  Storing → secret:github-token"
store_secret "github-token"  "${GITHUB_TOKEN}"  "GitHub PAT for demo-calc-app workflows" | \
    python3 -c "import sys,json; d=json.load(sys.stdin); print('  → ' + d.get('message','?') + '  |  ' + d.get('hint',''))"

echo "  Storing → secret:jenkins-token"
store_secret "jenkins-token" "${JENKINS_TOKEN}" "Jenkins API token for pt-orch user" | \
    python3 -c "import sys,json; d=json.load(sys.stdin); print('  → ' + d.get('message','?') + '  |  ' + d.get('hint',''))"

echo ""
echo "  Secrets in store (values NEVER returned):"
list_secrets | python3 -c "
import sys, json
for s in json.load(sys.stdin):
    print(f\"  • {s['name']:<20} {s.get('description','')}\")
"

# ── 2. Jenkins SEQUENTIAL ─────────────────────────────────────────────────────
section "2 / Jenkins SEQUENTIAL  (calculator-pipeline)"
info "2 steps: MULTIPLY 15×7  then  ADD 100+50  (sequential)"
info "Token   : secret:jenkins-token"
run_workflow "Jenkins sequential" \
    "data/jenkins/jenkins_test-calculator.yaml" \
    "${MAX_WAIT_FAST}" || true

# ── 3. Jenkins PARALLEL ───────────────────────────────────────────────────────
section "3 / Jenkins PARALLEL  (parallel-job-a + parallel-job-b)"
info "2 steps running simultaneously, each sleeping 15s  (~30s total)"
info "Token   : secret:jenkins-token"
run_workflow "Jenkins parallel" \
    "data/jenkins/jenkins_test-parallel.yaml" \
    "${MAX_WAIT_FAST}" || true

# ── 4. GitHub SEQUENTIAL — calc-deployment ────────────────────────────────────
section "4 / GitHub SEQUENTIAL  (calc-deployment, env=dev)"
info "Triggers build-deploy.yml → Maven build + test + deploy  (allow ~5 min)"
info "Token   : secret:github-token"
run_workflow "GitHub calc-deployment" \
    "data/github/github_test-calc-deployment.yaml" \
    "${MAX_WAIT_SLOW}" || true

# ── 5. GitHub SEQUENTIAL — staging-deployment ─────────────────────────────────
section "5 / GitHub SEQUENTIAL  (staging-deployment, env=staging v2.5.3)"
info "Triggers build-deploy.yml with staging parameters"
info "Token   : secret:github-token"
run_workflow "GitHub staging-deployment" \
    "data/github/github_test-staging-deployment.yaml" \
    "${MAX_WAIT_SLOW}" || true

# ── 6. GitHub SEQUENTIAL — prod-deployment ────────────────────────────────────
section "6 / GitHub SEQUENTIAL  (prod-deployment, env=prod v3.0.0-rc1)"
info "Triggers build-deploy.yml with prod parameters (tests disabled)"
info "Token   : secret:github-token"
run_workflow "GitHub prod-deployment" \
    "data/github/github_test-prod-deployment.yaml" \
    "${MAX_WAIT_SLOW}" || true

# ── 7. GitHub PARALLEL ────────────────────────────────────────────────────────
section "7 / GitHub PARALLEL  (parallel-job-a.yml + parallel-job-b.yml)"
info "2 lightweight workflows (15s sleep each) running simultaneously (~35–90s)"
info "Token   : secret:github-token"
run_workflow "GitHub parallel" \
    "data/github/github_test-parallel.yaml" \
    "${MAX_WAIT_SLOW}" || true

# ── Summary ───────────────────────────────────────────────────────────────────
TOTAL=$(( PASS + FAIL ))
section "Summary"
echo "  Passed : ${PASS} / ${TOTAL}"
echo "  Failed : ${FAIL} / ${TOTAL}"
echo ""
if [ "${FAIL}" -eq 0 ]; then
    echo "  🎉 All ${TOTAL} tests passed!"
    echo "     Secrets were stored once and resolved at runtime — no env vars in container."
    exit 0
else
    echo "  ⚠️  ${FAIL} test(s) failed. Review output above."
    exit 1
fi

#!/usr/bin/env bash
# =============================================================================
# setup-jenkins-parallel-jobs.sh
#
# Creates (or updates) two Jenkins pipeline jobs for testing parallel execution:
#   - parallel-job-a
#   - parallel-job-b
#
# Also updates the existing calculator-pipeline with SLEEP_DURATION support.
#
# Usage:
#   JENKINS_URL=http://localhost:9090 \
#   JENKINS_USER=pt-orch \
#   JENKINS_TOKEN=<token> \
#   bash setup-jenkins-parallel-jobs.sh
# =============================================================================


# ── Resolve project root (works from any directory) ──────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"
# ─────────────────────────────────────────────────────────────────────────────

JENKINS_URL="${JENKINS_URL:-http://localhost:9090}"
JENKINS_USER="${JENKINS_USER:-pt-orch}"
JENKINS_TOKEN="${JENKINS_TOKEN:?Please set JENKINS_TOKEN}"
AUTH="${JENKINS_USER}:${JENKINS_TOKEN}"

echo "🔧 Jenkins Job Setup"
echo "  URL  : ${JENKINS_URL}"
echo "  User : ${JENKINS_USER}"
echo ""

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

get_crumb() {
    curl -s -u "${AUTH}" \
        "${JENKINS_URL}/crumbIssuer/api/json" \
        | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['crumbRequestField'] + ':' + d['crumb'])" 2>/dev/null
}

job_exists() {
    local name="$1"
    local code
    code=$(curl -s -o /dev/null -w "%{http_code}" -u "${AUTH}" "${JENKINS_URL}/job/${name}/api/json")
    [ "$code" = "200" ]
}

create_or_update_job() {
    local name="$1"
    local script="$2"
    local crumb
    crumb=$(get_crumb)

    # Escape the groovy script for embedding in XML
    local escaped_script
    escaped_script=$(python3 -c "
import sys, html
script = open('${script}').read()
print(html.escape(script))
")

    local xml="<?xml version='1.1' encoding='UTF-8'?>
<flow-definition plugin='workflow-job'>
  <description>Parallel test job: ${name}</description>
  <keepDependencies>false</keepDependencies>
  <properties/>
  <definition class='org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition' plugin='workflow-cps'>
    <script>${escaped_script}</script>
    <sandbox>true</sandbox>
  </definition>
  <triggers/>
  <disabled>false</disabled>
</flow-definition>"

    if job_exists "${name}"; then
        echo "♻️  Updating existing job: ${name}"
        local http_code
        http_code=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
            -u "${AUTH}" \
            -H "${crumb}" \
            -H "Content-Type: application/xml" \
            --data "${xml}" \
            "${JENKINS_URL}/job/${name}/config.xml")
        [ "$http_code" = "200" ] && echo "  ✅ Updated (HTTP ${http_code})" || echo "  ⚠️  HTTP ${http_code}"
    else
        echo "➕ Creating new job: ${name}"
        local http_code
        http_code=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
            -u "${AUTH}" \
            -H "${crumb}" \
            -H "Content-Type: application/xml" \
            --data "${xml}" \
            "${JENKINS_URL}/createItem?name=${name}")
        [ "$http_code" = "200" ] && echo "  ✅ Created (HTTP ${http_code})" || echo "  ⚠️  HTTP ${http_code}"
    fi
}

# ---------------------------------------------------------------------------
# Create / update parallel-job-a
# ---------------------------------------------------------------------------
echo "--- parallel-job-a ---"
create_or_update_job "parallel-job-a" "jenkins-parallel-job.groovy"
echo ""

# ---------------------------------------------------------------------------
# Create / update parallel-job-b
# ---------------------------------------------------------------------------
echo "--- parallel-job-b ---"
create_or_update_job "parallel-job-b" "jenkins-parallel-job.groovy"
echo ""

# ---------------------------------------------------------------------------
# Update calculator-pipeline with SLEEP_DURATION support
# ---------------------------------------------------------------------------
echo "--- calculator-pipeline (update) ---"
create_or_update_job "calculator-pipeline" "jenkins-calculator-pipeline.groovy"
echo ""

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo "✅ Done! Jobs available at:"
echo "   ${JENKINS_URL}/job/parallel-job-a"
echo "   ${JENKINS_URL}/job/parallel-job-b"
echo "   ${JENKINS_URL}/job/calculator-pipeline"
echo ""
echo "Run the parallel workflow test:"
echo "  JENKINS_TOKEN=${JENKINS_TOKEN} JENKINS_URL=http://localhost:9090 JENKINS_USER=${JENKINS_USER} bash test-parallel-workflow.sh"

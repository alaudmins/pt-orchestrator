# Environment Variables Setup Guide

## Quick Start

### Option 1: Use the Setup Script (Recommended)

```bash
# Source the setup script to set all variables
source setup-env.sh
```

### Option 2: Manual Export (Copy-Paste)

Copy and paste these commands in your terminal:

```bash
# GitHub Integration
export GITHUB_TOKEN="your_github_personal_access_token_here"

# Jenkins Integration
export JENKINS_URL="http://host.docker.internal:9090"
export JENKINS_USER="pt-orch"
export JENKINS_TOKEN="your_jenkins_api_token_here"
```

### Option 3: One-Line Command

```bash
export GITHUB_TOKEN="your_github_personal_access_token_here" && export JENKINS_URL="http://host.docker.internal:9090" && export JENKINS_USER="pt-orch" && export JENKINS_TOKEN="your_jenkins_api_token_here"
```

---

## Running Tests

After setting environment variables, run the test scripts:

```bash
# Test GitHub integration
./test-github-workflow.sh

# Test Jenkins integration
./test-jenkins-workflow.sh
```

---

## Environment Variables Reference

| Variable | Value | Purpose |
|----------|-------|---------|
| `GITHUB_TOKEN` | `github_pat_11B4GZ...` | GitHub Personal Access Token for triggering workflows |
| `JENKINS_URL` | `http://host.docker.internal:9090` | Jenkins server URL (accessible from Docker) |
| `JENKINS_USER` | `pt-orch` | Jenkins username |
| `JENKINS_TOKEN` | `1116dba6516c82d...` | Jenkins API token |

---

## Verify Environment Variables

Check if variables are set correctly:

```bash
echo "GitHub Token: ${GITHUB_TOKEN:0:20}..."
echo "Jenkins URL: $JENKINS_URL"
echo "Jenkins User: $JENKINS_USER"
echo "Jenkins Token: ${JENKINS_TOKEN:0:10}..."
```

---

## Troubleshooting

### Variables Not Persisting

Environment variables are session-specific. If you close your terminal, you'll need to set them again.

**Solutions:**
1. Add to your shell profile (`.zshrc` or `.bashrc`):
   ```bash
   echo 'source /path/to/pt-orchestrator/setup-env.sh' >> ~/.zshrc
   ```

2. Or just run `source setup-env.sh` each time you start a new terminal session

### Test Script Says "Variable Not Set"

Make sure you're using `export` (not just assignment):
- ✅ Correct: `export GITHUB_TOKEN="..."`
- ❌ Wrong: `GITHUB_TOKEN="..."`

Verify with: `env | grep -E "GITHUB|JENKINS"`

---

## For CI/CD or Production

For non-local environments, set these as environment variables in your CI/CD system or container orchestration platform:

- **Docker Compose**: Set in `.env` file
- **Kubernetes**: Use Secrets
- **GitHub Actions**: Set as repository secrets
- **Jenkins**: Use credentials plugin

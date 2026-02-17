# Environment Variable Support Testing Guide

This document explains how to test the GitHub and Jenkins workflow integrations using environment variables for credentials.

## Overview

The pt-orchestrator now supports environment variable substitution in workflow YAML files using the syntax:
- `${VAR_NAME}` - Required variable (fails if not set)
- `${VAR_NAME:default}` - Optional variable with default value

##Prerequisites

1. **Running orchestrator**:
   ```bash
   docker-compose up --build
   ```

2. **jq installed** (for test scripts):
   ```bash
   # macOS
   brew install jq
   
   # Ubuntu/Debian
   sudo apt-get install jq
   ```

## Testing GitHub Workflow Integration

### Step 1: Create GitHub Personal Access Token

1. Go to GitHub Settings → Developer settings → Personal access tokens → Tokens (classic)
2. Generate new token with scopes:
   - `repo` (Full control of private repositories)
   - `workflow` (Update GitHub Action workflows)

### Step 2: Set Environment Variable

```bash
export GITHUB_TOKEN="your_github_personal_access_token"
```

### Step 3: Run Test Script

```bash
./test-github-workflow.sh
```

The script will:
1. Register the GitHub workflow from `data/github/github_test-calc-deployment.yaml`
2. Trigger the workflow execution
3. Monitor the execution status
4. Display the final result

### Manual Testing

You can also test manually:

```bash
# Register workflow
curl -X POST -H "Content-Type: text/yaml" \
  --data-binary @data/github/github_test-calc-deployment.yaml \
  http://localhost:8080/api/workflows

# Trigger execution
curl -X POST http://localhost:8080/api/workflows/github_test-calc-deployment/run

# Check status (replace RUN_ID with actual ID from previous response)
curl http://localhost:8080/api/runs/RUN_ID
```

## Testing Jenkins Workflow Integration

### Step 1: Get Jenkins API Token

1. Log in to your Jenkins instance
2. Click your username → Configure
3. Under "API Token", click "Add new Token"
4. Copy the generated token

### Step 2: Set Environment Variables

```bash
export JENKINS_TOKEN="your_jenkins_api_token"
export JENKINS_URL="http://localhost:9090"  # Optional, defaults to http://localhost:9090
export JENKINS_USER="pt-orch"               # Optional, defaults to pt-orch
```

### Step 3: Run Test Script

```bash
./test-jenkins-workflow.sh
```

The script will:
1. Register the Jenkins workflow from `data/jenkins/jenkins_test-calculator.yaml`
2. Trigger the workflow execution with parameters
3. Monitor the execution status
4. Display the final result

### Manual Testing

You can also test manually:

```bash
# Register workflow
curl -X POST -H "Content-Type: text/yaml" \
  --data-binary @data/jenkins/jenkins_test-calculator.yaml \
  http://localhost:8080/api/workflows

# Trigger execution
curl -X POST http://localhost:8080/api/workflows/jenkins_test-calculator/run

# Check status (replace RUN_ID with actual ID from previous response)
curl http://localhost:8080/api/runs/RUN_ID
```

## Docker Compose with Environment Variables

When running with Docker Compose, environment variables are automatically passed from the host:

```bash
# Set environment variables
export GITHUB_TOKEN="your_github_token"
export JENKINS_TOKEN="your_jenkins_token"

# Start services (environment variables will be passed to orchestrator-app)
docker-compose up --build
```

## Workflow YAML Examples

### GitHub Workflow with Environment Variable

```yaml
id: my-github-workflow
version: "1.0"
name: My GitHub Workflow
stages:
  - id: deploy
    executionMode: SEQUENTIAL
    steps:
      - id: trigger-action
        type: GITHUB_WORKFLOW
        config:
          repo: owner/repo-name
          workflow: deploy.yml
          branch: main
          token: "${GITHUB_TOKEN}"  # ← Environment variable
          inputs:
            environment: "production"
```

### Jenkins Workflow with Environment Variables

```yaml
id: my-jenkins-workflow
version: "1.0"
name: My Jenkins Workflow
stages:
  - id: build
    executionMode: SEQUENTIAL
    steps:
      - id: run-build
        type: JENKINS_JOB
        config:
          jenkinsUrl: "${JENKINS_URL:http://localhost:9090}"  # ← With default
          jobName: "my-build-job"
          username: "${JENKINS_USER:admin}"                   # ← With default
          token: "${JENKINS_TOKEN}"                           # ← Required
          parameters:
            BUILD_TYPE: "release"
            VERSION: "1.0.0"
```

## Troubleshooting

### "Environment variable 'X' is required but not set"

Make sure you've exported the environment variable before running the orchestrator or test scripts:
```bash
export GITHUB_TOKEN="your_token"
```

### GitHub workflow triggers but status shows "RUNNING" indefinitely

- Check that your GitHub Personal Access Token has the correct permissions
- Verify the workflow exists in your repository
- Check GitHub Actions logs for errors

### Jenkins workflow fails with authentication error

- Verify your Jenkins API token is correct
- Ensure the Jenkins user has permission to trigger the job
- Check that `JENKINS_URL` is accessible from the orchestrator

### "jq: command not found"

Install jq:
```bash
# macOS
brew install jq

# Ubuntu/Debian
sudo apt-get install jq
```

## Architecture

The environment variable resolution happens at execution time:

1. **Workflow registration**: YAML files are stored as-is with `${VAR_NAME}` placeholders
2. **Workflow execution**: When a workflow runs, `EnvVarResolver` resolves all placeholders in the config
3. **Executor receives resolved config**: Executors receive configuration with actual values substituted

This approach keeps credentials out of the database and allows different environments to use different credentials.

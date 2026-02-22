# PT Orchestrator

A lightweight, self-contained **workflow orchestration engine** built on Spring Boot. It triggers and monitors end-to-end test pipelines across **Jenkins** and **GitHub Actions** — with built-in AES-256-GCM encrypted secrets, sequential and parallel stage execution, and a fully non-blocking REST API.

---

## Table of Contents

1. [Features](#features)
2. [Architecture](#architecture)
3. [Prerequisites](#prerequisites)
4. [Setup — Mac / Linux](#setup--mac--linux)
5. [Setup — Windows (no Docker)](#setup--windows-no-docker)
6. [Database Configuration](#database-configuration)
7. [Environment Variables Reference](#environment-variables-reference)
8. [Secrets Management](#secrets-management)
9. [Workflow YAML Reference](#workflow-yaml-reference)
10. [REST API Reference](#rest-api-reference)
11. [Non-Blocking Execution Model](#non-blocking-execution-model)
12. [End-to-End Usage Walkthrough](#end-to-end-usage-walkthrough)
13. [Testing Guide](#testing-guide)
14. [Scripts Reference](#scripts-reference)
15. [Postman Collection](#postman-collection)
16. [Project Structure](#project-structure)
17. [Documentation](#documentation)

---

## Features

| Feature | Detail |
|---------|--------|
| **YAML Workflow Definitions** | Declare multi-stage pipelines in YAML; stored and versioned in DB |
| **Non-Blocking Trigger** | `POST /run` returns immediately with a `runId`; execution runs in the background |
| **Sequential Stages** | Steps within a stage execute one-by-one; each step waits for completion before next |
| **Parallel Stages** | Steps within a stage execute concurrently via a dedicated thread pool |
| **Jenkins Integration** | Triggers builds, polls queue → build number → completion; supports nested folder paths |
| **GitHub Actions Integration** | Dispatches workflow runs and polls for completion |
| **Built-in Secrets Store** | AES-256-GCM encrypted; referenced in YAML as `secret:<name>`; values never returned by API |
| **Live Status Polling** | `GET /api/runs/{runId}` returns real-time stage and step status from DB |
| **H2 (default)** | Zero-config file-based database for dev/local use |
| **PostgreSQL (production)** | Switch with a single env var/profile change |
| **Auto `.env` Loading** | `spring-dotenv` loads `.env` automatically from IntelliJ, terminal, or `java -jar` |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        HTTP Request Thread                       │
│  POST /api/workflows/{id}/run                                    │
│    → save WorkflowRun (status: PENDING)                          │
│    → submit to wf-engine-* thread pool (non-blocking)           │
│    → return { runId } immediately  ◄─── returns in < 1 sec     │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│              Background Thread  (wf-engine-N)                    │
│                                                                   │
│  WorkflowRun status → RUNNING                                    │
│                                                                   │
│  For each Stage (in definition order):                           │
│    StageRun status → RUNNING                                     │
│                                                                   │
│    SEQUENTIAL mode:                                              │
│      for each Step:                                              │
│        trigger → poll every 10s → wait for SUCCESS/FAILED        │
│        StepRun status updated in DB after every poll             │
│                                                                   │
│    PARALLEL mode:                                                │
│      all Steps triggered simultaneously                          │
│      each Step polled on its own CompletableFuture              │
│      StageRun waits for ALL futures before proceeding           │
│                                                                   │
│  WorkflowRun status → SUCCESS / FAILED                          │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│               GET /api/runs/{runId}  (any time)                  │
│    → reads fresh state from DB                                   │
│    → returns WorkflowRun + StageRuns + StepRuns with live status│
└─────────────────────────────────────────────────────────────────┘

Executor types:
  JenkinsExecutor   → POST /job/F1/job/F2/job/JobName/buildWithParameters
                      → poll /queue/item/{id}/api/json
                      → poll /job/…/{buildNumber}/api/json
  GithubExecutor    → POST /repos/{owner}/{repo}/actions/workflows/{wf}/dispatches
                      → poll /repos/{owner}/{repo}/actions/runs

Value resolution (config fields):
  "secret:<name>"           → SecretService → AES-256-GCM decrypt from DB
  "${ENV_VAR:defaultValue}" → System.getenv("ENV_VAR") with fallback

Thread Pool (wf-engine-*):
  core=5, max=20, queue=100   (configurable in application.properties)
```

### Key Components

| Class | Role |
|---|---|
| `WorkflowController` | REST endpoints for workflows, runs, secrets |
| `WorkflowService` | Business logic: register, trigger, list, delete |
| `WorkflowEngine` | Background executor; orchestrates stages and steps |
| `JenkinsExecutor` | Triggers and polls Jenkins jobs (supports nested folders) |
| `GithubExecutor` | Triggers and polls GitHub Actions workflows |
| `EncryptionService` | AES-256-GCM encrypt/decrypt for the secrets store |
| `SecretService` | CRUD for the secrets store |
| `EnvVarResolver` | Resolves `secret:*` and `${VAR:default}` in step configs |
| `AsyncConfig` | Configures the named `workflowExecutor` thread pool |
| `WorkflowParser` | Parses YAML workflow definitions |

---

## Prerequisites

| Requirement | Mac / Linux | Windows |
|---|---|---|
| Java | JDK 17+ | JDK 17+ |
| Build tool | Maven 3.8+ (or use `./mvnw`) | Maven 3.8+ (or use `mvnw.cmd`) |
| Docker (optional) | Docker Desktop | Not required |
| Git | Any | Git for Windows or WSL |

---

## Setup — Mac / Linux

### Option A: With Docker (H2 — recommended for dev)

```bash
# 1. Clone and enter the project
git clone https://github.com/alaudmins/pt-orchestrator.git
cd pt-orchestrator

# 2. Set up environment
cp .env.example .env
# Edit .env — set SECRETS_ENCRYPTION_KEY at minimum:
#   openssl rand -base64 32   ← paste output as SECRETS_ENCRYPTION_KEY

# 3. Start with embedded H2 (no external DB needed)
docker compose -f docker-compose.h2.yml --env-file .env up -d

# 4. Verify
curl http://localhost:8080/api/workflows
```

### Option B: Without Docker (direct Maven / IntelliJ)

```bash
# 1. Clone
git clone https://github.com/alaudmins/pt-orchestrator.git
cd pt-orchestrator

# 2. Set up environment
cp .env.example .env
# Edit .env — set at least SECRETS_ENCRYPTION_KEY

# 3a. From terminal (spring-dotenv auto-loads .env — no export needed)
./mvnw spring-boot:run

# --- OR ---

# 3b. From IntelliJ IDEA
#   - Open the project
#   - Run OrchestratorApplication as Spring Boot
#   - .env is loaded automatically by spring-dotenv — no run config changes needed

# 4. Verify
curl http://localhost:8080/api/workflows
```

> **Note:** The H2 profile is the default. The database file is stored at `./data/orchestratordb.mv.db` and persists across restarts.

---

## Setup — Windows (no Docker)

> These steps work on a Windows VDI/PC with no Docker required.

### 1. Install Prerequisites

- **JDK 17+**: Download from [Adoptium](https://adoptium.net/) or [Oracle](https://www.oracle.com/java/technologies/downloads/)
- **Maven 3.8+**: Download from [maven.apache.org](https://maven.apache.org/download.cgi), extract, add `bin/` to `PATH`
  - *Or* use the included Maven Wrapper (`mvnw.cmd`) — **no Maven install needed**

Verify:
```cmd
java -version
mvn -version      (or: mvnw.cmd -version)
```

### 2. Clone the project

```cmd
git clone https://github.com/alaudmins/pt-orchestrator.git
cd pt-orchestrator
```

### 3. Create your `.env` file

Copy `.env.example` to `.env` using Notepad or any editor:
```cmd
copy .env.example .env
notepad .env
```

Set the following at minimum:

```
SECRETS_ENCRYPTION_KEY=<generate with: openssl rand -base64 32>
JENKINS_URL=http://your-jenkins-server:port
JENKINS_USER=your-jenkins-username
```

### 4. Set environment variables (Windows Command Prompt)

Windows doesn't auto-source `.env`. Set the variables for the current session:

```cmd
for /f "usebackq tokens=1,* delims==" %A in (`findstr /v "^#" .env`) do set %A=%B
```

Or set them permanently via **System Properties → Environment Variables**.

> **Tip:** If you use IntelliJ on Windows, `spring-dotenv` will load `.env` automatically. No manual `set` commands needed when running from IDEA.

### 5. Build and run

```cmd
:: Using Maven Wrapper (no Maven install needed)
mvnw.cmd spring-boot:run

:: Or if Maven is installed system-wide
mvn spring-boot:run
```

The app starts on **http://localhost:8080**

### 6. Verify

```cmd
curl http://localhost:8080/api/workflows
```

---

## Database Configuration

### Default: H2 (File-based, no installation)

The app runs with H2 by default. No configuration needed.

- **Data file**: `./data/orchestratordb.mv.db` (persists across restarts)
- **Profile**: `h2` (set automatically)
- **Console**: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:file:./data/orchestratordb`)

Key properties (`application-h2.properties`):
```properties
spring.datasource.url=jdbc:h2:file:./data/orchestratordb;AUTO_SERVER=TRUE;MODE=PostgreSQL
spring.h2.console.enabled=true
spring.jpa.hibernate.ddl-auto=update
```

### Production: PostgreSQL

Switch to PostgreSQL without changing any code — just set the Spring profile and DB connection:

**In `.env`:**
```env
SPRING_PROFILES_ACTIVE=postgres
DATABASE_URL=jdbc:postgresql://your-db-host:5432/orchestrator
DATABASE_USER=your_db_user
DATABASE_PASSWORD=your_db_password
```

**Or as JVM args:**
```bash
mvn spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Dspring.profiles.active=postgres \
    -Dspring.datasource.url=jdbc:postgresql://localhost:5432/orchestrator \
    -Dspring.datasource.username=postgres \
    -Dspring.datasource.password=postgres"
```

**With Docker Compose (PostgreSQL):**
```bash
docker compose -f docker-compose.yml --env-file .env up -d
```

> See [docs/H2_POSTGRES_CONFIG.md](docs/H2_POSTGRES_CONFIG.md) and [docs/EXTERNAL_DATABASE.md](docs/EXTERNAL_DATABASE.md) for full details.

---

## Environment Variables Reference

| Variable | Required | Default | Description |
|---|---|---|---|
| `SECRETS_ENCRYPTION_KEY` | ✅ Yes | *(ephemeral)* | Base64-encoded 32-byte AES key. Generate: `openssl rand -base64 32`. Without this, a new random key is generated on each restart, making stored secrets unreadable. |
| `SPRING_PROFILES_ACTIVE` | No | `h2` | Set to `postgres` for PostgreSQL |
| `DATABASE_URL` | If postgres | — | JDBC URL for PostgreSQL |
| `DATABASE_USER` | If postgres | — | DB username |
| `DATABASE_PASSWORD` | If postgres | — | DB password |
| `JENKINS_URL` | No | `http://localhost:9090` | Jenkins base URL |
| `JENKINS_USER` | No | `pt-orch` | Jenkins username for API auth |
| `SERVER_PORT` | No | `8080` | App port |

Generate a strong encryption key:
```bash
# Mac/Linux
openssl rand -base64 32

# Windows (PowerShell)
[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }))
```

---

## Secrets Management

Secrets are stored AES-256-GCM encrypted in the database. Values can **never** be read back via the API — only names and metadata are exposed.

### Store a secret

```bash
curl -X POST http://localhost:8080/api/secrets \
  -H "Content-Type: application/json" \
  -d '{
    "name": "jenkins-token",
    "value": "your_actual_token_here",
    "description": "Jenkins API token for pt-orch user"
  }'
```

### Reference in workflow YAML

```yaml
token: "secret:jenkins-token"   # resolved at step execution time
```

### List stored secrets (names only, never values)

```bash
curl http://localhost:8080/api/secrets
```

### Delete a secret

```bash
curl -X DELETE http://localhost:8080/api/secrets/jenkins-token
```

---

## Workflow YAML Reference

### Structure

```yaml
id: <unique-workflow-id>       # used in API calls: POST /api/workflows/{id}/run
version: "1.0"
name: Human readable name

stages:
  - id: <stage-id>
    executionMode: SEQUENTIAL  # or PARALLEL
    steps:
      - id: <step-id>
        type: JENKINS_JOB      # or GITHUB_WORKFLOW
        config:
          # ... executor-specific config ...
```

### Jenkins Job Step

```yaml
- id: run-load-test
  type: JENKINS_JOB
  config:
    jenkinsUrl: "${JENKINS_URL:http://localhost:9090}"    # env var with fallback
    jobName: "TeamA/PerformanceTests/load-test"          # supports nested folders!
    username: "${JENKINS_USER:pt-orch}"
    token: "secret:jenkins-token"                        # from secrets store
    parameters:                                          # optional build parameters
      ENVIRONMENT: "staging"
      DURATION: "300"
      THREADS: "50"
```

> **Nested Jenkins Folders**: Use slash-separated paths. `"FolderA/SubFolder/JobName"` is automatically converted to `/job/FolderA/job/SubFolder/job/JobName` in the Jenkins REST API URL.

### GitHub Actions Step

```yaml
- id: deploy-app
  type: GITHUB_WORKFLOW
  config:
    repo: your-org/your-repo
    workflow: deploy.yml
    branch: main
    token: "secret:github-token"
    inputs:                                              # optional workflow_dispatch inputs
      environment: "staging"
      version: "1.2.3"
```

### Full Example: Multi-Stage with Mixed Executors

```yaml
id: github_test-full-pipeline
version: "1.0"
name: Full Performance Test Pipeline

stages:
  # Stage 1: Deploy the application (sequential — one step)
  - id: deploy
    executionMode: SEQUENTIAL
    steps:
      - id: deploy-to-staging
        type: GITHUB_WORKFLOW
        config:
          repo: my-org/my-app
          workflow: deploy.yml
          branch: main
          token: "secret:github-token"
          inputs:
            environment: staging

  # Stage 2: Run load tests in parallel (all steps start simultaneously)
  - id: load-tests
    executionMode: PARALLEL
    steps:
      - id: api-load-test
        type: JENKINS_JOB
        config:
          jenkinsUrl: "${JENKINS_URL}"
          jobName: "PerformanceTeam/LoadTests/api-test"
          username: "${JENKINS_USER}"
          token: "secret:jenkins-token"
          parameters:
            DURATION: "600"
            THREADS: "100"
      - id: ui-load-test
        type: JENKINS_JOB
        config:
          jenkinsUrl: "${JENKINS_URL}"
          jobName: "PerformanceTeam/LoadTests/ui-test"
          username: "${JENKINS_USER}"
          token: "secret:jenkins-token"
          parameters:
            DURATION: "600"
            THREADS: "50"
```

---

## REST API Reference

Base URL: `http://localhost:8080`

### Secrets

| Method | Endpoint | Body | Description |
|---|---|---|---|
| `POST` | `/api/secrets` | `{"name":"…","value":"…","description":"…"}` | Store or update a secret |
| `GET` | `/api/secrets` | — | List all secret names + metadata (no values) |
| `DELETE` | `/api/secrets/{name}` | — | Delete a secret |

### Workflows

| Method | Endpoint | Body | Description |
|---|---|---|---|
| `POST` | `/api/workflows` | YAML text (`Content-Type: text/yaml`) | Register or update a workflow definition |
| `GET` | `/api/workflows` | — | List all registered workflows |
| `DELETE` | `/api/workflows/{workflowId}` | — | Delete a workflow and all its run history |

**Register Response:**
```json
{
  "workflowId": "jenkins_test-calculator",
  "name": "Test Jenkins Calculator Pipeline",
  "version": "1.0",
  "message": "Workflow registered successfully. Use 'workflowId' to trigger a run."
}
```
> ⚠️ Use the `workflowId` field (the YAML `id` value) for subsequent calls — not a UUID.

### Runs

| Method | Endpoint | Body | Description |
|---|---|---|---|
| `POST` | `/api/workflows/{workflowId}/run` | — | Trigger a workflow run (returns immediately) |
| `GET` | `/api/runs/{runId}` | — | Get live run status with stages and steps |
| `GET` | `/api/runs` | — | List all runs (summary) |

**Trigger Run Response:**
```json
{
  "runId": "550e8400-e29b-41d4-a716-446655440000",
  "workflowId": "jenkins_test-calculator",
  "status": "PENDING",
  "startTime": "2026-02-22T14:30:00Z",
  "endTime": null
}
```

**Get Run Status Response (live, poll this repeatedly):**
```json
{
  "runId": "550e8400-e29b-41d4-a716-446655440000",
  "workflowId": "jenkins_test-calculator",
  "workflowName": "Test Jenkins Calculator Pipeline",
  "status": "RUNNING",
  "startTime": "2026-02-22T14:30:00Z",
  "endTime": null,
  "stages": [
    {
      "stageDefId": "trigger-jenkins-job",
      "status": "RUNNING",
      "startTime": "2026-02-22T14:30:01Z",
      "endTime": null,
      "steps": [
        {
          "stepDefId": "run-calculator-pipeline",
          "executorType": "JENKINS_JOB",
          "status": "RUNNING",
          "startTime": "2026-02-22T14:30:02Z",
          "endTime": null,
          "attemptCount": 1,
          "logs": null
        }
      ]
    }
  ]
}
```

**Status values:** `PENDING` → `RUNNING` → `SUCCESS` / `FAILED`

---

## Non-Blocking Execution Model

The trigger-run call (`POST /api/workflows/{id}/run`) is fully non-blocking:

```
HTTP Thread                          wf-engine-N thread (background)
    │                                        │
    ├── save WorkflowRun (PENDING)           │
    ├── submit to thread pool ──────────────►│
    ├── return { runId } immediately         ├── update status: RUNNING
    │   (< 1 second)                         ├── Stage 1 starts
    │                                        │     ├── Step A: trigger Jenkins
    │                                        │     ├── Step A: poll every 10s
    │                                        │     ├── Step A: SUCCESS
    │                                        │     └── Step B: (SEQUENTIAL: starts next)
    │                                        ├── Stage 2 starts (PARALLEL)
    │                                        │     ├── Step X + Step Y: both start
    │                                        │     ├── Both poll concurrently
    │                                        │     └── Wait for all → stage SUCCESS
    │                                        └── WorkflowRun: SUCCESS

GET /api/runs/{runId}  ←── can be called at any time to see live status
```

### Thread Pool Configuration

In `application.properties`:
```properties
orchestrator.executor.core-pool-size=5    # concurrent workflows always ready
orchestrator.executor.max-pool-size=20    # burst ceiling
orchestrator.executor.queue-capacity=100  # pending trigger backlog
```

---

## End-to-End Usage Walkthrough

### Step 1: Start the app

```bash
# Mac/Linux
./mvnw spring-boot:run

# Windows
mvnw.cmd spring-boot:run
```

### Step 2: Store your credentials as secrets

```bash
# Jenkins token
curl -X POST http://localhost:8080/api/secrets \
  -H "Content-Type: application/json" \
  -d '{"name":"jenkins-token","value":"YOUR_JENKINS_API_TOKEN","description":"Jenkins API token"}'

# GitHub token (if using GitHub workflows)
curl -X POST http://localhost:8080/api/secrets \
  -H "Content-Type: application/json" \
  -d '{"name":"github-token","value":"ghp_YOUR_TOKEN","description":"GitHub PAT"}'

# Verify
curl http://localhost:8080/api/secrets
```

### Step 3: Register a workflow

```bash
curl -X POST http://localhost:8080/api/workflows \
  -H "Content-Type: text/yaml" \
  --data-binary @data/jenkins/jenkins_test-calculator.yaml

# Response includes workflowId — save it
# e.g. "workflowId": "jenkins_test-calculator"
```

### Step 4: Trigger a run

```bash
curl -X POST http://localhost:8080/api/workflows/jenkins_test-calculator/run

# Returns immediately (< 1 sec):
# { "runId": "550e8400-...", "status": "PENDING" }
```

### Step 5: Monitor the run

```bash
# Replace with your actual runId
curl http://localhost:8080/api/runs/550e8400-e29b-41d4-a716-446655440000

# Poll every 10-15 seconds until status = SUCCESS or FAILED
# The response shows live stage and step statuses
```

### Step 6: List all runs

```bash
curl http://localhost:8080/api/runs
```

---

## Testing Guide

### Verifying Non-Blocking Behaviour

```bash
# 1. Trigger a run and time the response
time curl -X POST http://localhost:8080/api/workflows/jenkins_test-calculator/run
# Expected: completes in < 1 second ✅

# 2. Immediately poll — should already show RUNNING
curl http://localhost:8080/api/runs/<runId>
# Expected: status=RUNNING, stages populated ✅
```

### Verifying Parallel Execution

```bash
# Register and trigger the parallel workflow
curl -X POST http://localhost:8080/api/workflows \
  -H "Content-Type: text/yaml" \
  --data-binary @data/jenkins/jenkins_test-parallel.yaml

curl -X POST http://localhost:8080/api/workflows/jenkins_test-parallel/run

# Poll immediately — both steps should be RUNNING at the same time
curl http://localhost:8080/api/runs/<runId>
# Expected: stages[0].steps shows job-a AND job-b both with status=RUNNING ✅
```

### Using the Postman Collection

Import `pt-orchestrator.postman_collection.json` from the project root.

The collection has 4 folders:

| Folder | Purpose |
|---|---|
| 🔐 Secrets | Store, list, delete secrets |
| 📋 Workflows | Register, list, delete workflow definitions |
| ▶️ Runs | Trigger and monitor workflow runs |
| 🧪 Non-Blocking Architecture Test | **8-step ordered sequence** with automated assertions to prove non-blocking and parallel behaviour |

> In the **🧪** folder, **run steps 1-8 in order**. Variables (`workflowId`, `runId`) are auto-captured between steps. Edit steps 2 and 6 to replace `YOUR_FOLDER/YOUR_JOB_NAME` with your real Jenkins job path.

### Using the Test Scripts (Mac/Linux)

```bash
# End-to-end test (all workflow types, secrets flow)
bash scripts/test-e2e-secrets-flow.sh

# Jenkins sequential
bash scripts/test-jenkins-workflow.sh

# Jenkins parallel
bash scripts/test-parallel-workflow.sh

# GitHub Actions
bash scripts/test-github-workflow.sh
```

---

## Scripts Reference

All scripts are in `scripts/` and resolve the project root automatically — run them from any directory:

| Script | Purpose |
|---|---|
| `scripts/start-app.sh` | Start the orchestrator (H2 or postgres profile) |
| `scripts/stop-all.sh` | Stop all services |
| `scripts/setup-env.sh` | Source this to export all env vars from `.env` |
| `scripts/test-e2e-secrets-flow.sh` | Full end-to-end test (secrets + 7 workflows) |
| `scripts/test-jenkins-workflow.sh` | Jenkins sequential workflow test |
| `scripts/test-parallel-workflow.sh` | Jenkins parallel execution test |
| `scripts/test-github-workflow.sh` | GitHub Actions sequential test |
| `scripts/test-github-parallel-workflow.sh` | GitHub Actions parallel execution test |
| `scripts/setup-jenkins-parallel-jobs.sh` | One-time: provision Jenkins parallel test jobs |

---

## Postman Collection

File: **`pt-orchestrator.postman_collection.json`** (project root)

**To import:**
1. Open Postman → **Import** → drag the file in
2. Set collection variable `baseUrl` = `http://localhost:8080`
3. Use the **🧪 Non-Blocking Architecture Test** folder for complete validation

The collection includes pre-written test assertions, console logging, and auto-variable capture between requests.

---

## Project Structure

```
pt-orchestrator/
├── src/main/java/com/cvs/orchestrator/
│   ├── OrchestratorApplication.java      # @EnableAsync + @EnableScheduling
│   ├── config/
│   │   └── AsyncConfig.java              # wf-engine-* thread pool (core=5, max=20)
│   ├── controller/
│   │   ├── WorkflowController.java       # /api/workflows, /api/runs
│   │   └── SecretController.java         # /api/secrets
│   ├── service/
│   │   ├── WorkflowService.java          # register, trigger, list, delete
│   │   ├── SecretService.java            # CRUD for secrets store
│   │   └── EncryptionService.java        # AES-256-GCM encrypt/decrypt
│   ├── engine/
│   │   ├── WorkflowEngine.java           # @Async execution; stage/step orchestration
│   │   └── StepStatusReader.java         # REQUIRES_NEW tx for fresh DB reads
│   ├── executors/
│   │   ├── JenkinsExecutor.java          # trigger + poll Jenkins (folder-aware)
│   │   ├── GithubExecutor.java           # trigger + poll GitHub Actions
│   │   ├── ExecutorRegistry.java         # maps executor type string → implementation
│   │   ├── StepExecutionContext.java
│   │   └── StepExecutionResult.java
│   ├── model/
│   │   ├── definition/                   # WorkflowDefinition, Stage, Step entities
│   │   └── runtime/                      # WorkflowRun, StageRun, StepRun entities
│   └── util/
│       ├── EnvVarResolver.java           # resolves secret:* and ${VAR:default}
│       └── WorkflowParser.java           # YAML → WorkflowDefinition
├── data/
│   ├── jenkins/*.yaml                    # Sample Jenkins workflow definitions
│   └── github/*.yaml                     # Sample GitHub workflow definitions
├── scripts/                              # Shell scripts for Mac/Linux
├── docs/                                 # Extended documentation
├── docker-compose.yml                    # App + PostgreSQL
├── docker-compose.h2.yml                 # App with H2 (no external DB)
├── docker-compose.db.yml                 # PostgreSQL only
├── Dockerfile
├── pom.xml
├── .env.example                          # Template — copy to .env and fill in
└── pt-orchestrator.postman_collection.json
```

---

## Documentation

| Document | Description |
|---|---|
| [docs/ENV_SETUP_GUIDE.md](docs/ENV_SETUP_GUIDE.md) | Detailed environment variables reference |
| [docs/H2_POSTGRES_CONFIG.md](docs/H2_POSTGRES_CONFIG.md) | Switching between H2 and PostgreSQL |
| [docs/EXTERNAL_DATABASE.md](docs/EXTERNAL_DATABASE.md) | Connecting to an external / remote PostgreSQL |
| [docs/INDEPENDENT_SETUP.md](docs/INDEPENDENT_SETUP.md) | Full independent setup reference |
| [docs/TESTING_GUIDE.md](docs/TESTING_GUIDE.md) | End-to-end testing guide |

---

## License

MIT

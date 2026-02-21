# Performance Test Orchestrator

A Spring Boot workflow orchestration engine for automating Jenkins and GitHub Actions execution — with built-in secrets store, sequential and parallel stage support.

## Features

- **YAML-based Workflow Definitions** — define complex multi-stage workflows declaratively
- **Jenkins Integration** — trigger and monitor Jenkins pipelines
- **GitHub Actions Integration** — trigger and monitor GitHub workflows
- **Parallel Execution** — run steps concurrently within a stage (`executionMode: PARALLEL`)
- **Built-in Secrets Store** — AES-256-GCM encrypted secrets; no tokens in env vars or YAMLs
- **Durable State** — all execution state persisted to H2 (dev) or PostgreSQL (prod)
- **REST API** — full API for workflow and run management

## Quick Start

```bash
# 1. Copy and fill in your env file
cp .env.example .env   # edit .env with your keys

# 2. Start the orchestrator
docker compose -f docker-compose.h2.yml --env-file .env up -d

# 3. Store credentials in the secrets store (one-time)
curl -X POST http://localhost:8080/api/secrets \
  -H "Content-Type: application/json" \
  -d '{"name":"github-token","value":"ghp_xxx","description":"GitHub PAT"}'

# 4. Run a workflow
source .env && bash scripts/test-e2e-secrets-flow.sh
```

## API Reference

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/workflows` | Register a workflow (YAML body) |
| `GET`  | `/api/workflows` | List all workflows |
| `DELETE` | `/api/workflows/{id}` | Delete a workflow |
| `POST` | `/api/workflows/{id}/run` | Trigger a run |
| `GET`  | `/api/runs/{runId}` | Get run status |
| `GET`  | `/api/runs` | List all runs |
| `POST` | `/api/secrets` | Store a named secret |
| `GET`  | `/api/secrets` | List secret names (values never returned) |
| `DELETE` | `/api/secrets/{name}` | Delete a secret |

## Workflow YAML Example

```yaml
id: my-workflow
version: "1.0"
name: Build and Test

stages:
  - id: build
    executionMode: SEQUENTIAL
    steps:
      - id: trigger-build
        type: GITHUB_WORKFLOW
        config:
          repo: your-org/your-repo
          workflow: build.yml
          branch: main
          token: "secret:github-token"   # ← resolved from secrets store at runtime

  - id: perf-test
    executionMode: PARALLEL              # ← steps run simultaneously
    steps:
      - id: load-test-a
        type: JENKINS_JOB
        config:
          jenkinsUrl: "${JENKINS_URL:http://localhost:9090}"
          jobName: load-test-region-a
          username: "${JENKINS_USER:admin}"
          token: "secret:jenkins-token"  # ← resolved from secrets store at runtime
      - id: load-test-b
        type: JENKINS_JOB
        config:
          jenkinsUrl: "${JENKINS_URL:http://localhost:9090}"
          jobName: load-test-region-b
          username: "${JENKINS_USER:admin}"
          token: "secret:jenkins-token"
```

## Scripts

All scripts are in the `scripts/` directory and work from any directory:

| Script | Purpose |
|--------|---------|
| `scripts/test-e2e-secrets-flow.sh` | Full end-to-end test (secrets + all 7 workflows) |
| `scripts/test-parallel-workflow.sh` | Jenkins parallel execution test |
| `scripts/test-github-parallel-workflow.sh` | GitHub parallel execution test |
| `scripts/test-github-workflow.sh` | GitHub sequential workflow test |
| `scripts/test-jenkins-workflow.sh` | Jenkins sequential workflow test |
| `scripts/setup-jenkins-parallel-jobs.sh` | Provision Jenkins parallel test jobs |
| `scripts/start-app.sh` | Start the orchestrator |
| `scripts/stop-all.sh` | Stop all services |

## Documentation

| Document | Description |
|----------|-------------|
| [docs/ENV_SETUP_GUIDE.md](docs/ENV_SETUP_GUIDE.md) | Environment variables reference |
| [docs/H2_POSTGRES_CONFIG.md](docs/H2_POSTGRES_CONFIG.md) | Database configuration (H2 / PostgreSQL) |
| [docs/EXTERNAL_DATABASE.md](docs/EXTERNAL_DATABASE.md) | Connecting to an external database |
| [docs/INDEPENDENT_SETUP.md](docs/INDEPENDENT_SETUP.md) | Running without Docker |
| [docs/TESTING_GUIDE.md](docs/TESTING_GUIDE.md) | Testing workflows end-to-end |

## Architecture

```
WorkflowEngine
  ├── SEQUENTIAL stage → triggerStep() → pollUntilDone() → next step
  └── PARALLEL stage   → triggerStep() for all → CompletableFuture per step → allOf().join()

EnvVarResolver
  ├── "secret:name"       → SecretService → AES-256-GCM decrypt from DB
  └── "${ENV_VAR:default}" → System.getenv() fallback

Executors: JenkinsExecutor | GithubExecutor | (extensible via ExecutorRegistry)
```

## License

MIT

# Performance Test Orchestrator

A Spring Boot-based workflow orchestration engine for automating performance test execution.

## Features

- **YAML-based Workflow Definitions**: Define complex workflows declaratively
- **Jenkins Integration**: Trigger and monitor Jenkins jobs
- **GitHub Actions Integration**: Trigger and monitor GitHub workflows
- **Durable Execution**: All state persisted to PostgreSQL
- **Async Monitoring**: Background polling for long-running tasks
- **REST API**: Full API for workflow management and execution

## Quick Start

### Prerequisites
- Docker & Docker Compose
- Java 17+ (optional, for local development)

### Running with Docker Compose

```bash
docker-compose up --build
```

The API will be available at `http://localhost:8080`.

### API Endpoints

#### Register a Workflow
```bash
curl -X POST -H "Content-Type: text/yaml" \
  --data-binary @workflow.yaml \
  http://localhost:8080/api/workflows
```

#### List Workflows
```bash
curl http://localhost:8080/api/workflows
```

#### Trigger Execution
```bash
curl -X POST http://localhost:8080/api/workflows/{workflowId}/run
```

#### Check Run Status
```bash
curl http://localhost:8080/api/runs/{runId}
```

## Example Workflow

```yaml
id: sample-workflow
version: "1.0"
name: Sample Deployment Workflow
stages:
  - id: build-stage
    executionMode: SEQUENTIAL
    steps:
      - id: build-step
        type: GITHUB_WORKFLOW
        config:
           repo: your-org/your-repo
           workflow: build.yml
           branch: main
           token: "your-github-token"

  - id: test-stage
    executionMode: SEQUENTIAL
    steps:
      - id: perf-test
        type: JENKINS_JOB
        config:
           jenkinsUrl: http://jenkins.example.com
           jobName: performance-test
           username: admin
           token: "your-jenkins-token"
```

## Architecture

- **Workflow Engine**: State machine for orchestrating workflow execution
- **Task Poller**: Background service for monitoring async tasks
- **Executors**: Plugin architecture for Jenkins, GitHub, and future integrations
- **PostgreSQL**: Durable state storage

## Development

### Local Development

```bash
mvn spring-boot:run
```

### Build

```bash
mvn clean package
```

## License

MIT

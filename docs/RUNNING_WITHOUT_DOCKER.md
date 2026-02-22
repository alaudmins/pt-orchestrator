# Running pt-orchestrator Without Docker

This guide covers running the app directly on any machine with **Java 17+ and Maven** — no Docker required. Works on Mac, Linux, and Windows.

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Java JDK | 17 or later | [Adoptium](https://adoptium.net/) recommended |
| Maven | 3.8+ | **OR** use the included `./mvnw` — no install needed |
| Git | any | To clone the repo |

> **No Docker, no PostgreSQL, no external services needed.**
> The app defaults to an embedded H2 database stored in `data/orchestratordb`.

---

## Quick Start

### 1. Clone the repository

```bash
git clone <your-repo-url>
cd pt-orchestrator
```

### 2. Create your `.env` file

```bash
cp .env.example .env
```

Edit `.env` and fill in your actual values:

```properties
SPRING_PROFILES_ACTIVE=h2                    # keep as h2 (embedded DB)
SECRETS_ENCRYPTION_KEY=<generate below>      # openssl rand -base64 32
GITHUB_TOKEN=ghp_your_token_here
JENKINS_URL=http://your-jenkins-server:9090  # your real Jenkins URL
JENKINS_USER=your-jenkins-user
JENKINS_TOKEN=your-jenkins-api-token
```

Generate an encryption key:
```bash
# Mac / Linux
openssl rand -base64 32

# Windows PowerShell
[Convert]::ToBase64String((1..32 | ForEach-Object { [byte](Get-Random -Max 256) }))
```

### 3. Build the application

```bash
# Using Maven Wrapper (no Maven installation required)
./mvnw clean package -DskipTests

# OR using system Maven
mvn clean package -DskipTests
```

### 4. Run the application

**Option A — run directly from source (development):**

```bash
# Mac / Linux — loads .env automatically
export $(cat .env | grep -v '^#' | xargs)
./mvnw spring-boot:run

# Windows CMD — set env vars manually, then:
mvn spring-boot:run

# Windows PowerShell — load .env then run:
Get-Content .env | Where-Object { $_ -notmatch '^#' -and $_ -ne '' } |
  ForEach-Object { $k,$v = $_ -split '=',2; [System.Environment]::SetEnvironmentVariable($k,$v,'Process') }
./mvnw spring-boot:run
```

**Option B — run the packaged JAR:**

```bash
# Mac / Linux
export $(cat .env | grep -v '^#' | xargs)
java -jar target/pt-orchestrator-1.0.0.jar

# Windows CMD
set SPRING_PROFILES_ACTIVE=h2
set SECRETS_ENCRYPTION_KEY=your_key
set GITHUB_TOKEN=your_token
set JENKINS_URL=http://your-jenkins:9090
set JENKINS_USER=your_user
set JENKINS_TOKEN=your_token
java -jar target\pt-orchestrator-1.0.0.jar

# Or pass all values as JVM args (overrides env vars):
java -jar target/pt-orchestrator-1.0.0.jar \
  --spring.profiles.active=h2 \
  --JENKINS_URL=http://your-jenkins:9090 \
  --GITHUB_TOKEN=your_token
```

---

## Verify It's Running

Once started, the app listens on port **8080**:

| URL | Purpose |
|---|---|
| `http://localhost:8080/swagger-ui.html` | Swagger UI — explore & test all APIs |
| `http://localhost:8080/h2-console` | H2 DB browser (JDBC URL: `jdbc:h2:file:./data/orchestratordb`) |
| `http://localhost:8080/api/workflows` | List registered workflows |

---

## Pointing to Your Jenkins / GitHub

The workflow YAML files under `data/` use environment variable placeholders:

```yaml
jenkinsUrl: "${JENKINS_URL:http://localhost:9090}"
username:   "${JENKINS_USER:pt-orch}"
token:      "secret:jenkins-token"   # stored in the secrets API, not in the file
```

So just setting `JENKINS_URL=http://your-real-server:9090` (and the other vars) in your `.env` is all that's needed — no YAML edits required.

For the GitHub token and Jenkins token, store them via the Secrets API after the app starts:

```bash
# Store GitHub token
curl -X POST http://localhost:8080/api/secrets \
  -H "Content-Type: application/json" \
  -d '{"name":"github-token","value":"ghp_your_token"}'

# Store Jenkins token
curl -X POST http://localhost:8080/api/secrets \
  -H "Content-Type: application/json" \
  -d '{"name":"jenkins-token","value":"your_jenkins_api_token"}'
```

---

## Switching to PostgreSQL (Optional)

If you have a PostgreSQL instance available:

```properties
# In your .env
SPRING_PROFILES_ACTIVE=postgres
DATABASE_URL=jdbc:postgresql://your-db-host:5432/orchestrator
DATABASE_USER=postgres
DATABASE_PASSWORD=postgres
```

See [EXTERNAL_DATABASE.md](EXTERNAL_DATABASE.md) for full details.

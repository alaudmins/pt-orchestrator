# pt-orchestrator: Database Configuration Guide

The app supports two database modes controlled by a single environment variable. **H2 is the default** — no external database needed.

---

## Option 1: Docker with Built-in H2 (Recommended)

The simplest way to run the app — single container, no PostgreSQL, data persists across restarts via a Docker volume.

### Start

```bash
cd /path/to/pt-orchestrator

# First time (builds the image):
docker compose -f docker-compose.h2.yml up --build

# Subsequent starts (uses cached image):
docker compose -f docker-compose.h2.yml up
```

App is ready when you see:
```
Started OrchestratorApplication in X seconds
```

### Stop

```bash
# Graceful stop — data is preserved in the Docker volume
docker compose -f docker-compose.h2.yml down
```

### Restart

```bash
# Stop then start again — all data is still there
docker compose -f docker-compose.h2.yml down
docker compose -f docker-compose.h2.yml up
```

### Full Reset (wipes all data)

```bash
# Stop and delete the volume — database is wiped
docker compose -f docker-compose.h2.yml down -v
```

---

## Option 2: Run Locally (JAR + H2)

```bash
# Build
mvn clean package -DskipTests

# Run with H2 (default)
java -jar target/pt-orchestrator-1.0.0.jar

# Or use the helper script
./start-app.sh
```

---

## Option 3: Docker or Local with PostgreSQL

```bash
# Start a local PostgreSQL first
docker compose -f docker-compose.db.yml up -d

# Then run the app pointing to it
export SPRING_PROFILES_ACTIVE=postgres
export DATABASE_URL=jdbc:postgresql://localhost:5432/orchestrator
export DATABASE_USER=postgres
export DATABASE_PASSWORD=postgres
java -jar target/pt-orchestrator-1.0.0.jar

# Or with full Docker stack
docker compose -f docker-compose.yml --profile with-db up --build
```

---

## URLs

| URL | Description |
|---|---|
| http://localhost:8080/swagger-ui/index.html | Interactive API docs |
| http://localhost:8080/api/workflows | Workflows REST endpoint |
| http://localhost:8080/h2-console | H2 database browser (H2 mode only) |

**H2 Console connection settings:**
- JDBC URL: `jdbc:h2:file:./data/orchestratordb`
- User: `sa`
- Password: *(leave blank)*

---

## Switching Between H2 and PostgreSQL

| Mode | How |
|---|---|
| H2 (default) | `SPRING_PROFILES_ACTIVE=h2` (already set in `Dockerfile` and `docker-compose.h2.yml`) |
| PostgreSQL | `SPRING_PROFILES_ACTIVE=postgres` + set `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` |

Profile config files:
- [`application-h2.properties`](src/main/resources/application-h2.properties) — file-based H2, `ddl-auto=update`
- [`application-postgres.properties`](src/main/resources/application-postgres.properties) — PostgreSQL from env vars, `ddl-auto=update`

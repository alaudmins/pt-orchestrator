# Running with Independent Database

This guide shows how to run the PostgreSQL database and pt-orchestrator application as separate, independent services.

## Quick Start

### 1. Start the Database

```bash
./start-db.sh
```

This will:
- Start PostgreSQL in a separate container
- Expose it on port 5432
- Create a persistent volume for data
- Keep it running independently

### 2. Configure Environment (Optional)

Create a `.env` file if you want to customize settings:

```bash
cp .env.example .env
# Edit .env with your settings
```

Default configuration (if no `.env` file):
- Database: `host.docker.internal:5432/orchestrator`
- User: `postgres`
- Password: `postgres`

### 3. Start the Application

```bash
./start-app.sh
```

This will:
- Check if database is running
- Build and start the orchestrator app
- Connect to the independent database
- Expose API on port 8080

### 4. Stop Services

```bash
# Stop everything
./stop-all.sh

# Or stop individually
docker-compose -f docker-compose.app.yml down    # Stop app only
docker-compose -f docker-compose.db.yml down     # Stop database only
```

---

## Manual Commands

### Database Management

```bash
# Start database
docker-compose -f docker-compose.db.yml up -d

# View database logs
docker-compose -f docker-compose.db.yml logs -f

# Stop database (data persists)
docker-compose -f docker-compose.db.yml down

# Stop database and remove data
docker-compose -f docker-compose.db.yml down -v
```

### Application Management

```bash
# Start application
docker-compose -f docker-compose.app.yml up --build -d

# View application logs
docker-compose -f docker-compose.app.yml logs -f

# Restart application (e.g., after code changes)
docker-compose -f docker-compose.app.yml up --build -d

# Stop application
docker-compose -f docker-compose.app.yml down
```

---

## Typical Workflows

### Development Workflow

```bash
# 1. Start database once (it will keep running)
./start-db.sh

# 2. Start application
./start-app.sh

# 3. Make code changes...

# 4. Restart app to test changes
docker-compose -f docker-compose.app.yml down
./start-app.sh

# 5. Stop everything when done
./stop-all.sh
```

### Production-like Workflow

```bash
# 1. Start database
./start-db.sh

# 2. Configure production settings in .env
cat > .env << EOF
DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/orchestrator
DATABASE_USER=postgres
DATABASE_PASSWORD=secure_password
GITHUB_TOKEN=github_pat_xxxxx
JENKINS_URL=http://host.docker.internal:9090
JENKINS_TOKEN=jenkins_token_xxxxx
EOF

# 3. Start application
./start-app.sh

# Database and app now run independently with auto-restart
```

### Database Maintenance

```bash
# Backup database
docker exec orchestrator-db pg_dump -U postgres orchestrator > backup.sql

# Restore database
cat backup.sql | docker exec -i orchestrator-db psql -U postgres orchestrator

# Connect to database
docker exec -it orchestrator-db psql -U postgres orchestrator

# View database status
docker-compose -f docker-compose.db.yml ps
```

---

## Benefits of Independent Database

✅ **Independent Lifecycle**: Database and app can be started/stopped separately
✅ **Data Persistence**: Database data survives application restarts
✅ **Easier Updates**: Update application without affecting database
✅ **Better Development**: Restart app quickly without losing data
✅ **Production-like**: Mimics production where DB is external service
✅ **Resource Management**: Control database independently

---

## Architecture

```
┌─────────────────────┐     ┌──────────────────────┐
│  orchestrator-db    │     │  orchestrator-app    │
│  (PostgreSQL)       │◄────│  (Spring Boot)       │
│                     │     │                      │
│  Port: 5432         │     │  Port: 8080          │
│  Volume: persisted  │     │  Stateless           │
└─────────────────────┘     └──────────────────────┘
         ▲                           │
         │                           │
         │                           ▼
    host.docker.internal     API: http://localhost:8080
```

- Database runs as standalone container with persistent volume
- Application connects to database via `host.docker.internal`
- Both can be managed independently

---

## Troubleshooting

### Application can't connect to database

**Check database is running:**
```bash
docker ps | grep orchestrator-db
```

**Verify database health:**
```bash
docker-compose -f docker-compose.db.yml ps
```

**Test connection from host:**
```bash
psql -h localhost -p 5432 -U postgres -d orchestrator
# Password: postgres
```

### Database data is lost after restart

This shouldn't happen with the docker-compose.db.yml setup. Check the volume exists:
```bash
docker volume ls | grep orchestrator-db-data
```

If you used `docker-compose down -v`, the volume was deleted. Don't use `-v` flag unless you want to delete data.

### Port 5432 already in use

Another PostgreSQL instance is running on port 5432. Either:
1. Stop the other instance
2. Change the port in `docker-compose.db.yml`:
   ```yaml
   ports:
     - "5433:5432"  # Use port 5433 on host
   ```
   Then update `DATABASE_URL` to match.

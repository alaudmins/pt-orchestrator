#!/bin/bash

# Script to start the orchestrator application
# Supports two database profiles:
#   h2       - In-memory H2 database (default, no external DB needed)
#   postgres - External PostgreSQL database

# Determine active profile (env var takes precedence, default to h2)

# ── Resolve project root (works from any directory) ──────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"
# ─────────────────────────────────────────────────────────────────────────────

DB_PROFILE="${SPRING_PROFILES_ACTIVE:-h2}"

echo "🗄️  Database profile: ${DB_PROFILE}"
echo ""

# Only check for PostgreSQL when using the postgres profile
if [ "${DB_PROFILE}" = "postgres" ]; then
    if ! docker ps | grep -q "orchestrator-db"; then
        echo "⚠️  Warning: PostgreSQL database container 'orchestrator-db' is not running!"
        echo ""
        echo "Please start the database first:"
        echo "  ./start-db.sh"
        echo ""
        echo "Or set these environment variables for an external DB:"
        echo "  export DATABASE_URL=jdbc:postgresql://your-db-host:5432/orchestrator"
        echo "  export DATABASE_USER=your_user"
        echo "  export DATABASE_PASSWORD=your_password"
        echo ""
        read -p "Continue anyway? (y/N) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    fi
else
    echo "ℹ️  Using H2 in-memory database — no external DB required."
    echo "   Schema will be auto-created on startup and wiped on shutdown."
    echo "   H2 console available at: http://localhost:8080/h2-console"
    echo "   (JDBC URL: jdbc:h2:mem:orchestratordb)"
    echo ""
fi

echo "🚀 Starting pt-orchestrator application..."
echo ""

# Load environment variables from .env if it exists
if [ -f .env ]; then
    echo "📝 Loading configuration from .env file..."
    export $(cat .env | grep -v '^#' | xargs)
fi

# Display configuration (without sensitive values)
echo "Configuration:"
echo "  Active Profile:  ${DB_PROFILE}"
if [ "${DB_PROFILE}" = "postgres" ]; then
    echo "  Database URL:    ${DATABASE_URL:-jdbc:postgresql://localhost:5432/orchestrator}"
    echo "  Database User:   ${DATABASE_USER:-postgres}"
fi
echo "  Jenkins URL:     ${JENKINS_URL:-http://host.docker.internal:9090}"
echo "  GitHub Token:    ${GITHUB_TOKEN:+***configured***}"
echo "  Jenkins Token:   ${JENKINS_TOKEN:+***configured***}"
echo ""

# Start the application
docker-compose -f docker-compose.app.yml up --build -d

echo ""
echo "⏳ Waiting for application to start..."
sleep 10

# Check application health
if docker ps | grep -q "orchestrator-app"; then
    echo "✅ Application is running!"
    echo ""
    echo "API URL: http://localhost:8080"
    echo "Swagger: http://localhost:8080/swagger-ui.html"
    if [ "${DB_PROFILE}" != "postgres" ]; then
        echo "H2 Console: http://localhost:8080/h2-console"
    fi
    echo ""
    echo "To view logs: docker-compose -f docker-compose.app.yml logs -f"
    echo "To stop:      docker-compose -f docker-compose.app.yml down"
else
    echo "❌ Application failed to start"
    echo "Check logs: docker-compose -f docker-compose.app.yml logs"
fi

#!/bin/bash

# Script to start the orchestrator application
# Requires PostgreSQL database to be running independently

# Check if database is running
if ! docker ps | grep -q "orchestrator-db"; then
    echo "⚠️  Warning: PostgreSQL database container 'orchestrator-db' is not running!"
    echo ""
    echo "Please start the database first:"
    echo "  ./start-db.sh"
    echo ""
    echo "Or if using a different database, set these environment variables:"
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

echo "🚀 Starting pt-orchestrator application..."
echo ""

# Load environment variables from .env if it exists
if [ -f .env ]; then
    echo "📝 Loading configuration from .env file..."
    export $(cat .env | grep -v '^#' | xargs)
fi

# Display configuration (without sensitive values)
echo "Configuration:"
echo "  Database URL: ${DATABASE_URL:-jdbc:postgresql://host.docker.internal:5432/orchestrator}"
echo "  Database User: ${DATABASE_USER:-postgres}"
echo "  Jenkins URL: ${JENKINS_URL:-http://host.docker.internal:9090}"
echo "  GitHub Token: ${GITHUB_TOKEN:+***configured***}"
echo "  Jenkins Token: ${JENKINS_TOKEN:+***configured***}"
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
    echo ""
    echo "To view logs: docker-compose -f docker-compose.app.yml logs -f"
    echo "To stop: docker-compose -f docker-compose.app.yml down"
else
    echo "❌ Application failed to start"
    echo "Check logs: docker-compose -f docker-compose.app.yml logs"
fi

#!/bin/bash

# Script to start the PostgreSQL database independently
# This database will persist data across restarts

echo "🗄️  Starting PostgreSQL database..."
docker-compose -f docker-compose.db.yml up -d

echo ""
echo "⏳ Waiting for database to be healthy..."
sleep 5

# Check database health
if docker ps | grep -q "orchestrator-db.*healthy"; then
    echo "✅ Database is running and healthy!"
    echo ""
    echo "Database Details:"
    echo "  Host: localhost"
    echo "  Port: 5432"
    echo "  Database: orchestrator"
    echo "  User: postgres"
    echo "  Password: postgres"
    echo ""
    echo "Connection string for app:"
    echo "  DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/orchestrator"
else
    echo "⚠️  Database might still be starting up..."
    echo "Run 'docker-compose -f docker-compose.db.yml ps' to check status"
fi

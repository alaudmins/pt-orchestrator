#!/bin/bash

# Script to stop all services

echo "🛑 Stopping services..."

# Stop application
if docker ps -a | grep -q "orchestrator-app"; then
    echo "  Stopping orchestrator-app..."
    docker-compose -f docker-compose.app.yml down
fi

# Stop database
if docker ps -a | grep -q "orchestrator-db"; then
    echo "  Stopping orchestrator-db..."
    docker-compose -f docker-compose.db.yml down
fi

echo "✅ All services stopped"
echo ""
echo "Note: Database data is preserved in the 'orchestrator-db-data' volume"
echo "To remove data: docker volume rm orchestrator-db-data"

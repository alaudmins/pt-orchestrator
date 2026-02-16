#!/bin/bash

echo "===> 1. List all workflows"
curl -s http://localhost:8080/api/workflows | jq '.[] | {id, name, version}' || echo "⚠️  No workflows yet"

echo ""
echo "===> 2. Register sample workflow"
curl -s -X POST -H "Content-Type: text/yaml" --data-binary @sample-workflow.yaml http://localhost:8080/api/workflows > /dev/null && echo "✅ Workflow registered"

echo ""
echo "===> 3. List workflows again"
curl -s http://localhost:8080/api/workflows | jq '.[] | {workflowId, name, version}' 2>/dev/null || curl -s http://localhost:8080/api/workflows

echo ""
echo "===> Test complete!"

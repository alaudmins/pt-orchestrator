#!/bin/bash

# Environment Setup Script for pt-orchestrator
# Source this file to set all required environment variables
#
# Usage:
#   source setup-env.sh
#   OR
#   . setup-env.sh

echo "🔧 Setting up environment variables for pt-orchestrator..."

# ============================================
# GitHub Integration
# ============================================
export GITHUB_TOKEN="your_github_personal_access_token_here"

# ============================================
# Jenkins Integration
# ============================================
# Use host.docker.internal for apps running in Docker containers
export JENKINS_URL="http://host.docker.internal:9090"
export JENKINS_USER="pt-orch"
export JENKINS_TOKEN="your_jenkins_api_token_here"

# ============================================
# Database Configuration (Optional)
# ============================================
# Uncomment these if you want to override database settings
# export DATABASE_URL="jdbc:postgresql://host.docker.internal:5432/orchestrator"
# export DATABASE_USER="postgres"
# export DATABASE_PASSWORD="postgres"

echo ""
echo "✅ Environment variables set successfully!"
echo ""
echo "GitHub Token: ${GITHUB_TOKEN:0:20}..."
echo "Jenkins URL: $JENKINS_URL"
echo "Jenkins User: $JENKINS_USER"
echo "Jenkins Token: ${JENKINS_TOKEN:0:10}..."
echo ""
echo "You can now run the test scripts:"
echo "  ./test-github-workflow.sh"
echo "  ./test-jenkins-workflow.sh"

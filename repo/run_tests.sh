#!/bin/bash
set -e

echo "========================================="
echo " Starting Dockerized Test Environment    "
echo "========================================="

PROJECT_NAME="${COMPOSE_PROJECT_NAME:-$(basename "$PWD")}"
MYSQL_VOLUME="${PROJECT_NAME}_mysql_data"

# Setup .env file if it doesn't exist
if [ ! -f .env ]; then
    echo "Creating .env file from .env.example..."
    if [ -f ../.env.example ]; then
        cp ../.env.example .env
    else
        echo "ERROR: .env.example not found!"
        exit 1
    fi
fi

# Configure .env with test values
echo "Configuring .env for test environment..."
sed -i 's/MYSQL_ROOT_PASSWORD=.*/MYSQL_ROOT_PASSWORD=rootpass123/' .env
sed -i 's/MYSQL_DATABASE=.*/MYSQL_DATABASE=anju_sys/' .env
sed -i 's/MYSQL_USER=.*/MYSQL_USER=anju_user/' .env
sed -i 's/MYSQL_PASSWORD=.*/MYSQL_PASSWORD=anjupass123/' .env
sed -i 's/NACOS_CONFIG_ENABLED=.*/NACOS_CONFIG_ENABLED=false/' .env
sed -i 's/NACOS_DISCOVERY_ENABLED=.*/NACOS_DISCOVERY_ENABLED=false/' .env
sed -i 's/SECURITY_JWT_SECRET=.*/SECURITY_JWT_SECRET=anju-medical-appointment-system-secret-key-2026-test-environment-jwt-token-signing/' .env

echo ".env file configured successfully"

# Pull down any old containers
echo "Cleaning up old containers..."
docker compose --profile test down
docker volume rm "$MYSQL_VOLUME" >/dev/null 2>&1 || true

# Run the test-runner container, aborting and failing if it fails
echo "Starting test containers..."
set +e
docker compose --profile test up --build --abort-on-container-exit --exit-code-from test-runner test-runner
TEST_STATUS=$?
set -e

# Clean up
echo "Cleaning up test containers..."
docker compose --profile test down
docker volume rm "$MYSQL_VOLUME" >/dev/null 2>&1 || true

echo "========================================="
if [ $TEST_STATUS -eq 0 ]; then
    echo "[PASS] All tests passed in Docker!"
    exit 0
else
    echo "[FAIL] Tests failed in Docker environment."
    exit 1
fi

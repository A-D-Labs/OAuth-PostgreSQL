#!/bin/bash

set -e

# Single deploy entry point. The app image is built daemonlessly by Jib (no
# Dockerfile); compose then runs that image alongside Postgres (+ Redis).
#
# Usage: docker/scripts/deploy.sh [dev|pat|prod|test]
#   dev|pat|prod  build the Jib image, then `docker-compose up -d`
#   test          bring up the test Postgres and run `./mvnw verify` on the host

ENV=${1:-dev}
COMPOSE_DIR="docker/compose"
PROJECT_NAME="oauth-${ENV}"

case "$ENV" in
    dev|pat|prod|test) echo "Target environment: $ENV" ;;
    *)
        echo "Error: Invalid environment '${ENV}'"
        echo "Usage: $0 [dev|pat|prod|test]"
        exit 1
        ;;
esac

# The test flow does not deploy the app via compose — it runs the suite on the host
# against a throwaway Postgres (see docs/adr/0003-testcontainers-postgres-for-tests.md).
if [[ "$ENV" == "test" ]]; then
    echo "Starting test Postgres (localhost:5433)..."
    docker-compose -p oauth-test -f ${COMPOSE_DIR}/docker-compose.test.yml up -d
    set +e
    ./mvnw verify
    exit_code=$?
    set -e
    echo "Tearing down test Postgres..."
    docker-compose -p oauth-test -f ${COMPOSE_DIR}/docker-compose.test.yml down -v
    echo "Tests completed with exit code: $exit_code"
    exit $exit_code
fi

if [ ! -f ".env.${ENV}" ]; then
    echo "Error: .env.${ENV} file not found"
    echo "Please create this file based on .env.template"
    exit 1
fi

echo "Copying .env.${ENV} to .env for Docker Compose"
cp ".env.${ENV}" .env
export SPRING_PROFILES_ACTIVE=$ENV

echo "Building application image with Jib..."
./mvnw -q -DskipTests package jib:dockerBuild

echo "Starting deployment with Docker Compose..."
if [[ "$ENV" == "prod" ]]; then
    echo "Running production deployment with high availability settings"
    docker-compose -p $PROJECT_NAME -f ${COMPOSE_DIR}/docker-compose.yml -f ${COMPOSE_DIR}/docker-compose.${ENV}.yml up -d --scale app=2
else
    docker-compose -p $PROJECT_NAME -f ${COMPOSE_DIR}/docker-compose.yml -f ${COMPOSE_DIR}/docker-compose.${ENV}.yml up -d
fi

echo "Deployment completed successfully"
echo "Services status:"
docker-compose -p $PROJECT_NAME ps

if [[ "$ENV" == "dev" ]]; then
    echo "Application available at: http://localhost:${SERVER_PORT:-8080}"
    echo "Swagger UI: http://localhost:${SERVER_PORT:-8080}/swagger-ui.html"
elif [[ "$ENV" == "prod" ]]; then
    echo "Application deployed to production environment"
fi

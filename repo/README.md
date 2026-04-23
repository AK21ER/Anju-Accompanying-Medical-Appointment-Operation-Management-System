# Anju Medical Appointment System

Anju Medical Appointment System is a Spring Boot backend for medical appointment, property, finance, and file-management workflows. It runs with MySQL and Nacos in Docker and exposes a REST API documented through OpenAPI and Postman.

## What This Project Includes

- Authentication and session management with JWT, login, logout, and current-user endpoints.
- Appointment management endpoints for create, update, reschedule, cancel, list, and delete flows.
- Property management endpoints for creating and maintaining property records.
- Finance endpoints for bookkeeping, daily settlements, statements, refunds, invoices, and exception handling.
- File upload, preview, rollback, recycle-bin, and content access endpoints.
- Audit logging support for tracing important operations.

## Project Layout

- `backend/` - Spring Boot application and tests.
- `backend/database/migrations/init.sql` - Database initialization script used by Docker.
- `docs/` - API spec, design notes, questions, and Postman assets.
- `docker-compose.yml` - Local stack for MySQL, Nacos, backend, and test runner.
- `run_tests.sh` - Dockerized test entry point.

## Prerequisites

- Docker and Docker Compose.
- Java 17 and Maven if you want to run the backend or tests outside Docker.

## Configuration

Docker Compose expects a `.env` file in the repository root. The test script can generate one from a sibling `.env.example` if it exists, then fills in test-safe values for the Dockerized test flow.

Key environment variables used by the stack include:

- `MYSQL_ROOT_PASSWORD`
- `MYSQL_DATABASE`
- `MYSQL_USER`
- `MYSQL_PASSWORD`
- `SECURITY_JWT_SECRET`
- `NACOS_CONFIG_ENABLED`
- `NACOS_DISCOVERY_ENABLED`
- `ANJU_CRYPTO_KEY`

## Run With Docker

From the repository root, start the application stack:

```bash
docker compose up -d
```

This starts MySQL, Nacos, and the backend service. The main ports are:

- API: `8080`
- MySQL: `3307` on the host, mapped to container port `3306`
- Nacos: `8848`

The backend serves OpenAPI and Swagger UI from the configured Springdoc paths in `backend/src/main/resources/application.yml`.

## Run Tests

To run the full Dockerized unit and API test flow:

```bash
./run_tests.sh
```

The script brings up the test stack, waits for the API to be reachable, runs unit tests in `backend/unit_tests`, then runs API functional tests in `backend/API_tests`. It also cleans up the test containers and the MySQL volume afterward.

If you want to run tests directly with Maven, use the module-specific test directories under `backend/unit_tests` and `backend/API_tests`.

## Main API Areas

- Authentication: `/auth/register`, `/auth/login`, `/auth/logout`, `/auth/me`, `/auth/verify-secondary`
- Appointments: `/appointment`
- Properties: `/api/properties`
- Finance: `/finance`
- Files: `/file`

## Postman

Import these files into Postman:

- `docs/postman/Anju-Medical.postman_collection.json`
- `docs/postman/Anju-Local.postman_environment.json`

Then select the Anju Local environment, set `username` and `password` if needed, and log in once to store the bearer token. The collection scripts automatically attach auth for protected routes and fall back to HTTP Basic when no bearer token is present.

## Useful References

- API spec: `docs/api-spec.md`
- Design notes: `docs/design.md`
- API test data: `docs/api-endpoints-test-data.md`
- Questions and notes: `docs/questions.md`

## Troubleshooting

- If Docker startup fails, confirm that your `.env` values are set and that ports `8080`, `3307`, and `8848` are free.
- If tests fail early, check that the backend can reach MySQL and Nacos through the Docker network.
- If auth requests return 401, log in again and confirm the Postman environment has a current token.

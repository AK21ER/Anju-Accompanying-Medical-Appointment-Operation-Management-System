# Anju Medical Appointment System

A robust medical appointment and facility management backend system built with Spring Boot, Docker, and Nacos.

## Prerequisites

- Docker & Docker Compose

## Run The Application

From the project root, start all required services:

```bash
docker compose up -d
```

Docker Compose loads credentials from `.env` automatically.

### Exposed Ports

- **API Interface**: `8080`
- **MySQL**: `3306`
- **Nacos Configuration Server**: `8848`

## Run Tests In Docker

Run the full unit + API test suite in Docker:

```bash
./run_tests.sh
```

Before running Maven tests, the `test-runner` container performs an API readiness check against the backend service. If the API is not reachable and stable, the test run exits with failure. The script automatically tears down test containers and volumes on completion.

## Postman Quick Start

Import these files into Postman:

- `docs/postman/Anju-Medical.postman_collection.json`
- `docs/postman/Anju-Local.postman_environment.json`

Then:

1. Select the **Anju Local** environment.
2. Update `username` and `password` values if needed.
3. Run **Auth -> Login** to store the bearer `authorization` token.
4. Run protected requests like **Property -> List Properties** or **Finance -> Daily Statement Export (JSON)**.
5. Run **Auth -> Logout** to revoke the current bearer token.

The collection pre-request script automatically injects `Authorization` for protected routes under `/api/*`, `/finance/*`, `/appointment/*`, `/file/*`, and `/auth/logout`:

- Uses bearer token from the environment when present.
- Falls back to HTTP Basic using `username` + `password` if bearer token is missing.

# Investment Tracker

A personal finance and investment tracker, built as a set of Spring Boot microservices.

## Services

| Service | Description |
|---|---|
| [`investment-service`](services/investment-service) | CRUD for investments (name + amount), backed by PostgreSQL |

## Prerequisites

- JDK 17+
- Maven 3.9+
- Docker (for local Postgres and the integration tests)

## Running locally

Start PostgreSQL:

```bash
docker compose up -d
```

Run the service:

```bash
cd services/investment-service && mvn spring-boot:run
```

- API: http://localhost:8080/api/investments
- Swagger UI: http://localhost:8080/swagger-ui.html

Database connection settings can be overridden with `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`.

## Tests

```bash
cd services/investment-service
mvn test      # unit tests, no Docker needed
mvn verify    # unit + integration tests (Testcontainers, needs Docker)
```

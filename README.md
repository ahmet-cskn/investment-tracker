# Investment Tracker

A personal finance and investment tracker, built as a set of Spring Boot microservices with a React frontend.

## Components

| Component | Description |
|---|---|
| [`services/investment-service`](services/investment-service) | CRUD for investments (name + amount), backed by PostgreSQL |
| [`frontend`](frontend) | React single-page UI for managing investments |

## Prerequisites

- JDK 17+
- Maven 3.9+
- Node.js 20+ and npm
- Docker (for local Postgres and the backend integration tests)

## Running locally

Start PostgreSQL:

```bash
docker compose up -d
```

Run the backend:

```bash
cd services/investment-service && mvn spring-boot:run
```

- API: http://localhost:8080/api/investments
- Swagger UI: http://localhost:8080/swagger-ui.html

Database connection settings can be overridden with `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`.

Run the frontend (in a second terminal):

```bash
cd frontend && npm install && npm run dev
```

- UI: http://localhost:5173

The dev server proxies `/api` to the backend on port 8080, so no CORS configuration is needed.

## Tests

Backend:

```bash
cd services/investment-service
mvn test      # unit tests, no Docker needed
mvn verify    # unit + integration tests (Testcontainers, needs Docker)
```

Frontend:

```bash
cd frontend
npm test      # unit and component tests (Vitest, React Testing Library, MSW)
npm run lint
```

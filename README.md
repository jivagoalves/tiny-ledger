# Tiny Ledger

A simple in-memory ledger API for deposits, withdrawals, balances, and history.

## Features
- Record deposits and withdrawals
- View current balance
- View full transaction history
- Input validation
- In-memory thread-safe store
- Swagger/OpenAPI docs

## Architecture
- **tiny-ledger-core**: Core domain logic, DDD-based
- **tiny-ledger-web**: Spring Boot REST API (hexagonal/clean architecture)

## Run Locally

```bash
./gradlew bootRun
```

API will be available at: `http://localhost:8080`

## Ledger API

### Deposit money
```bash
curl -X POST http://localhost:8080/api/ledger/deposit \
  -H "Content-Type: application/json" \
  -d '{"amount": 100.00}'
```

### Withdraw money
```bash
curl -X POST http://localhost:8080/api/ledger/withdraw \
  -H "Content-Type: application/json" \
  -d '{"amount": 50.00}'
```

### Get current balance
```bash
curl http://localhost:8080/api/ledger/balance
```

### Get transaction history
```bash
curl http://localhost:8080/api/ledger/history
```

## API Docs
Visit [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) to view interactive documentation.

## Run Tests
```bash
./gradlew test
```

## Validation Rules
- Amounts must be greater than zero
- Withdrawals require sufficient balance

## Tech Stack
- Kotlin + Gradle
- Spring Boot
- Arrow-kt
- JUnit 5
- Swagger/OpenAPI
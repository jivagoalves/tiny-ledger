# Tiny Ledger ![Build](https://github.com/jivagoalves/tiny-ledger/actions/workflows/build.yml/badge.svg)

A simple in-memory ledger API for deposits, withdrawals, balances, and history.

## Features
- Record deposits and withdrawals
- View current balance
- View full transaction history
- Input validation
- Swagger/OpenAPI docs

### In-Memory Transactions (`InMemoryTransactionalRepository`)

Full ACID-style transactional semantics without a database:

- **Snapshot isolation** — each transaction reads from a consistent point-in-time snapshot, unaffected by concurrent writes
- **Optimistic concurrency control** — concurrent transactions proceed without locks; conflicts are detected at commit time via compare-and-set versioning
- **Automatic retry** — `withTransaction {}` transparently retries on conflict, so callers never see `OptimisticLockException`
- **Atomic commit/rollback** — pending saves and deletes are buffered and applied atomically on commit, with compensating rollback on failure
- **Thread-safe** — transaction context is `ThreadLocal`-scoped, so concurrent threads get fully isolated sessions

### Write-Ahead Log (`WalLedgerRepository`)

Durable transaction logging that survives process crashes:

- **Append-only log** — every committed transaction is serialized as a JSON line and appended to a WAL file before reaching the in-memory store, ensuring durability
- **fsync on write** — each append is followed by `FileDescriptor.sync()` to guarantee data reaches disk
- **Crash recovery** — on startup, `recover()` replays COMMITTED entries from the WAL to rebuild in-memory state
- **Torn write tolerance** — corrupt or partial lines (from crashes mid-write) are silently skipped during recovery
- **Monotonic LSN** — each entry gets an instance-scoped log sequence number; recovery re-initializes the counter from the WAL so LSNs never go backwards

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

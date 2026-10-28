# Idempotent Background Transaction Processing Service

A lightweight Spring Boot implementation for the FDE L2 practical assessment. It demonstrates idempotent transaction processing, sequence-based ordering, retry-safe simulated transient failures, input validation, a processing history, and a summary endpoint.

## Requirements
- JDK 17+
- Maven 3.8+

## Run
```bash
mvn clean test
mvn spring-boot:run
```

The API starts on `http://localhost:8080`.

## API
### Submit a batch
```bash
curl -X POST http://localhost:8080/api/transactions/batch \
  -H 'Content-Type: application/json' \
  --data-binary @sample-transactions.json
```
### Inspect results
```bash
curl http://localhost:8080/api/transactions
curl http://localhost:8080/api/summary
curl http://localhost:8080/api/accounts/ACC-201/balance
```

## Design decisions
- **Idempotency:** `transactionId` is the business idempotency key. Once a transaction is registered as processed/failed, replays with that ID do not apply the financial effect again. `requestId` is also tracked for request replay.
- **Ordering:** each account has a monotonically increasing `sequenceNumber`, starting at 1. A future sequence is buffered as `PENDING_SEQUENCE`; when a missing sequence arrives, contiguous pending items are drained in order. A sequence older than the next expected sequence is treated as stale/duplicate.
- **Retry:** `simulateTransientFailure=true` fails the first attempt only. The processor retries up to three times. The effect is applied only after the transient failure point, so the demo shows a retry without a duplicate balance change.
- **Validation:** positive amount, non-empty identifiers, supported transaction type, uppercase three-letter currency, and positive sequence number are required.
- **Observability:** SLF4J logs key transitions; `/api/summary` reports counts by status; `/api/transactions` exposes processing history.
- **Input boundary:** REST controller is thin. Processing rules live in `TransactionProcessor`, so an input adapter can later be replaced by a queue consumer, file reader, or scheduler.

## Important assumptions and limitations
This is an assessment-sized, single-process demo using in-memory collections. State is lost on restart. `synchronized` serializes writes within this JVM; it does not provide cross-instance coordination. A real implementation should use a durable database with unique constraints on idempotency keys and atomic ledger/balance updates, plus optimistic locking or row-level locks.
The sequence is per account and starts at 1. A missing sequence remains pending until the gap arrives; operational tooling should alert on long-lived gaps and allow controlled replay or repair. The demo models CREDIT, DEBIT, and REVERSAL as balance adjustments and does not implement a full accounting ledger.
For production, add database transactions, durable inbox/outbox patterns for downstream effects, authentication/authorization, rate limiting, structured correlation IDs, metrics/tracing, dead-letter handling, configurable retry/backoff, and integration tests against the chosen persistence technology.

## Tests
`TransactionProcessorTest` covers duplicate replay, out-of-order buffering and draining, transient retry without duplicate effect, and invalid amount isolation.

## AI-assisted development note
AI assistance was used to accelerate scaffolding and draft test cases. The implementation should be reviewed and validated by running `mvn clean test`, inspecting the edge cases, and ensuring the candidate can explain the idempotency key, ordering policy, retry boundary, and production limitations. Update this note to accurately reflect your own usage before submitting.

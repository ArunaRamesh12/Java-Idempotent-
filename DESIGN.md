# Design Note — Idempotent Background Transaction Processing Service

## Architecture
A Spring Boot REST adapter accepts a batch of transaction requests. `TransactionProcessor` owns validation, deduplication, per-account sequence handling, retry behavior, in-memory balance changes, history, and summary generation. The controller does not contain business rules.

## Idempotency and duplicate detection
`transactionId` is the business-level idempotency key. The processor stores the outcome by transaction ID and ignores subsequent deliveries for the same ID. `requestId` is tracked as a request replay key. The in-memory maps illustrate the pattern; production storage must enforce uniqueness at the database boundary to handle concurrent processes and restarts.

## Out-of-order strategy
Sequence-based processing is used, with sequence numbers scoped to an account and starting at 1. If sequence N arrives while the next expected number is lower, the event is buffered as `PENDING_SEQUENCE`. When a missing event arrives and is processed, the processor drains the next contiguous buffered event. Stale sequence numbers are rejected as duplicates. This is simple and deterministic, but requires a policy for missing sequences and long-lived pending records.

## Retry and consistency
A test flag simulates a transient error on the first attempt. Retry is bounded at three attempts. The transient exception occurs before the balance mutation, so the simulated retry cannot double-apply the effect. The balance update is a single in-memory operation after validation. In production, update the ledger entry, transaction status, and account balance in one database transaction; use a durable idempotency constraint and an inbox/outbox approach for external side effects.

## Lifecycle and observability
The model includes `RECEIVED`, `PROCESSING`, `PROCESSED`, `DUPLICATE`, `FAILED`, `RETRY_PENDING`, and `PENDING_SEQUENCE`. Processing history and summary counts are exposed by API. Logs include transaction IDs, attempts, and status transitions, but avoid account balances and other sensitive details.

## Testing
Unit tests cover duplicate replay, out-of-order arrival, retry-once behavior, and invalid input isolation. Run `mvn clean test`. Further tests should cover retry exhaustion, insufficient funds, duplicate payload mismatch, concurrent duplicate submissions, and persistence restart recovery.

## Production hardening
1. Persist transaction inbox, ledger, account balances, sequence checkpoints, and attempt counts.
2. Add unique constraints for transaction ID and request/idempotency key.
3. Make ledger insert and balance mutation atomic; use row-level locking or optimistic concurrency.
4. Use exponential backoff with jitter, a retry schedule, and a dead-letter queue for exhausted failures.
5. Add queue consumer acknowledgements only after durable commit; design for at-least-once delivery.
6. Add metrics for processed, duplicate, pending-gap age, retries, and failures; add tracing/correlation IDs and actionable alerts.
7. Add authentication, authorization, request size limits, rate limiting, secrets management, and audit controls.
8. Add integration and concurrency tests and document operational replay/repair procedures.

## Trade-offs
In-memory persistence keeps the exercise quick and dependency-light, but loses state on restart and cannot coordinate multiple replicas. Sequence buffering guarantees deterministic per-account ordering only when missing sequence numbers eventually arrive. The demo is not a full double-entry ledger and should not be treated as production financial software.

## AI-assisted workflow
The assessment permits AI tools. Describe specifically which suggestions were used, which code was edited, and which checks were performed. Do not claim tests passed unless you ran them locally. Review all generated code and be prepared to explain each design decision.

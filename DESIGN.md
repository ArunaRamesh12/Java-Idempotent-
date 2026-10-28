Architecture
A Spring Boot REST adapter accepts batch transaction requests. TransactionProcessor owns validation, deduplication, per-account sequence handling, retry behavior, in-memory balances, history, and summary generation. The controller handles API requests only and contains no business rules.
Idempotency and duplicate detection
transactionId acts as the primary business idempotency key to ignore duplicate submissions.
requestId tracks request replay detection.
Production storage must enforce uniqueness at the database boundary to handle concurrent requests and restarts safely.
Out-of-order strategy
Sequence processing is scoped per account starting at sequence 1. Out-of-order requests are buffered as PENDING_SEQUENCE. When missing earlier events arrive and process, contiguous buffered events are automatically drained. Stale sequence numbers are rejected.
Retry and consistency
Transient failures trigger bounded retries up to three attempts. Errors occur before balance mutation, preventing double-application. Balance updates are applied in-memory after validation. Production systems should perform ledger and balance updates atomically in a single database transaction.
Lifecycle and observability
Tracks states: RECEIVED, PROCESSING, PROCESSED, DUPLICATE, FAILED, RETRY_PENDING, and PENDING_SEQUENCE. Status transitions are exposed via summary/history APIs, with structured logging that avoids sensitive financial data.
Testing
Unit tests cover duplicate replay, out-of-order arrival, retry behavior, and input validation. 
Production hardening
Persistent storage for transactions and balances with unique database constraints.
Atomic balance mutations and row-level locking.
Distributed message queues (Kafka/RabbitMQ), retry backoff, and Dead-Letter Queues (DLQ).
Authentication, rate-limiting, and metric-based alerting.
Trade-offs
In-memory persistence loses state on application restart. Unresolved sequence gaps leave subsequent transactions in a pending state. This is a single-instance demo, not a full double-entry ledger.
AI-assisted workflow
AI tools were used for initial scaffolding, structural guidance, documentation, and test code development. All logic, execution flows, and unit tests were manually reviewed, edited, and validated locally.
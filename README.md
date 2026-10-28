# Idempotent Background Transaction Processing Service

A Spring Boot application that processes financial transactions asynchronously with background retry, per-account sequence ordering, and idempotency guarantees.

## Tech Stack & Prerequisites
- Java 17+
- Maven 3.8+
- Spring Boot

## How to Run

1. Run Tests:
   mvn clean test

2. Start Application:
   mvn spring-boot:run

App runs at `http://localhost:8080`.

## API Endpoints

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| **POST** | `/api/transactions/batch` | Submit transactions for background processing |
| **GET** | `/api/transactions/{transactionId}` | Get transaction status |
| **GET** | `/api/transactions` | View full processing history |
| **GET** | `/api/summary` | View processing summary report |
| **GET** | `/api/accounts/{accountId}/balance` | View current account balance |

## Sample Execution

**Submit Transaction:**
```bash
curl -X POST http://localhost:8080/api/transactions/batch \
  -H "Content-Type: application/json" \
  -d '[{
    "transactionId": "T100",
    "requestId": "R100",
    "accountId": "A100",
    "transactionType": "CREDIT",
    "amount": 200,
    "currency": "USD",
    "sequenceNumber": 1,
    "simulateTransientFailure": false
  }]'
package com.example.transactions.model;

public record TransactionResult(
        String transactionId,
        String requestId,
        TransactionStatus status,
        int attempts,
        String message
) {}

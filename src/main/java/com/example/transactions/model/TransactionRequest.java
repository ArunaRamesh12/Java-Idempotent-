package com.example.transactions.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record TransactionRequest(
        @NotBlank String transactionId,
        @NotBlank String requestId,
        @NotBlank String accountId,
        @NotBlank String transactionType,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "currency must be a 3-letter uppercase code") String currency,
        @NotNull @Positive Long sequenceNumber,
        boolean simulateTransientFailure
) {}

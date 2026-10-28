package com.example.transactions.controller;

import com.example.transactions.model.*;
import com.example.transactions.service.TransactionProcessor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TransactionController {
    private final TransactionProcessor processor;

    public TransactionController(TransactionProcessor processor) { this.processor = processor; }

    @PostMapping("/transactions/batch")
    public List<TransactionResult> submitBatch(@RequestBody List<TransactionRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request batch must not be empty");
        }
        return processor.submitBatch(requests);
    }

    @GetMapping("/transactions")
    public List<TransactionResult> history() { return processor.history(); }

    @GetMapping("/summary")
    public ProcessingSummary summary() { return processor.summary(); }

    @GetMapping("/accounts/{accountId}/balance")
    public Map<String, Object> balance(@PathVariable String accountId) {
        if (accountId.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accountId is required");
        BigDecimal balance = processor.balance(accountId);
        return Map.of("accountId", accountId, "balance", balance);
    }
}

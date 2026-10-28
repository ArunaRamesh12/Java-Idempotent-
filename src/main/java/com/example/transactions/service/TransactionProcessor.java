package com.example.transactions.service;

import com.example.transactions.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class TransactionProcessor {
    private static final Logger log = LoggerFactory.getLogger(TransactionProcessor.class);
    private static final int MAX_ATTEMPTS = 3;

    private final Map<String, TransactionResult> outcomesByTransactionId = new ConcurrentHashMap<>();
    private final Map<String, TransactionResult> outcomesByRequestId = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> balances = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSequenceByAccount = new ConcurrentHashMap<>();
    private final Map<String, TransactionRequest> pendingByAccountAndSequence = new ConcurrentHashMap<>();
    private final List<TransactionResult> history = Collections.synchronizedList(new ArrayList<>());
    private final Set<String> transientFailureAlreadyUsed = ConcurrentHashMap.newKeySet();
    private final AtomicInteger received = new AtomicInteger();

    public synchronized List<TransactionResult> submitBatch(List<TransactionRequest> requests) {
        List<TransactionResult> results = new ArrayList<>();
        for (TransactionRequest request : requests) {
            received.incrementAndGet();
            results.add(submitOne(request));
        }
        drainAllPending();
        return results;
    }

    public synchronized TransactionResult submitOne(TransactionRequest request) {
        if (!valid(request)) {
            TransactionResult invalid = new TransactionResult(request.transactionId(), request.requestId(),
                    TransactionStatus.FAILED, 0, "Invalid transaction fields or type");
            history.add(invalid);
            log.warn("Transaction rejected: transactionId={}, reason=validation", request.transactionId());
            return invalid;
        }

        TransactionResult sameRequest = outcomesByRequestId.get(request.requestId());
        if (sameRequest != null) {
            return recordDuplicate(request, "requestId already seen");
        }

   boolean alreadyPending = pendingByAccountAndSequence.values()
        .stream()
        .anyMatch(p -> p.transactionId().equals(request.transactionId())
                || p.requestId().equals(request.requestId()));

if (alreadyPending) {
    return recordDuplicate(request, "Transaction already pending");
}
   
        TransactionResult existing = outcomesByTransactionId.get(request.transactionId());
        if (existing != null) {
            TransactionResult duplicate = recordDuplicate(request, "transactionId already processed or registered");
            outcomesByRequestId.putIfAbsent(request.requestId(), duplicate);
            return duplicate;
        }

        String accountId = request.accountId();
        long expected = lastSequenceByAccount.getOrDefault(accountId, 0L) + 1;
        if (request.sequenceNumber() > expected) {
            pendingByAccountAndSequence.putIfAbsent(key(accountId, request.sequenceNumber()), request);
            TransactionResult pending = new TransactionResult(request.transactionId(), request.requestId(),
                    TransactionStatus.PENDING_SEQUENCE, 0, "Waiting for sequence " + expected);
            history.add(pending);
            log.info("Transaction pending for sequence gap: transactionId={}, sequence={}",
                    request.transactionId(), request.sequenceNumber());
            return pending;
        }
        if (request.sequenceNumber() < expected) {
            TransactionResult stale = recordDuplicate(request, "stale sequence; expected " + expected);
            outcomesByRequestId.putIfAbsent(request.requestId(), stale);
            return stale;
        }

        TransactionResult result = processWithRetry(request);
        if (result.status() == TransactionStatus.PROCESSED) {
            lastSequenceByAccount.put(accountId, request.sequenceNumber());
            outcomesByTransactionId.put(request.transactionId(), result);
            outcomesByRequestId.put(request.requestId(), result);
            drainAccount(accountId);
        } else {
            outcomesByTransactionId.put(request.transactionId(), result);
            outcomesByRequestId.put(request.requestId(), result);
        }
        return result;
    }

    private TransactionResult processWithRetry(TransactionRequest request) {
        int attempts = 0;
        while (attempts < MAX_ATTEMPTS) {
            attempts++;
            log.info("Transaction processing: transactionId={}, attempt={}", request.transactionId(), attempts);
            try {
                if (request.simulateTransientFailure() &&
                        transientFailureAlreadyUsed.add(request.transactionId())) {
                    throw new TransientProcessingException("Simulated transient failure");
                }
                applyEffect(request);
                TransactionResult result = new TransactionResult(request.transactionId(), request.requestId(),
                        TransactionStatus.PROCESSED, attempts, "Processed successfully");
                history.add(result);
                log.info("Transaction processed: transactionId={}, attempts={}", request.transactionId(), attempts);
                return result;
            } catch (TransientProcessingException ex) {
                if (attempts < MAX_ATTEMPTS) {
                    log.warn("Retry pending: transactionId={}, attempt={}", request.transactionId(), attempts);
                } else {
                    TransactionResult failed = new TransactionResult(request.transactionId(), request.requestId(),
                            TransactionStatus.FAILED, attempts, "Retry limit exhausted");
                    history.add(failed);
                    log.error("Transaction failed after retries: transactionId={}", request.transactionId());
                    return failed;
                }
            }
        }
        throw new IllegalStateException("Unreachable retry state");
    }

    private void applyEffect(TransactionRequest request) {
        BigDecimal current = balances.getOrDefault(request.accountId(), BigDecimal.ZERO);
        BigDecimal next;
        switch (request.transactionType()) {
            case "CREDIT" -> next = current.add(request.amount());
            case "DEBIT" -> {
                if (current.compareTo(request.amount()) < 0) {
                    throw new TransientProcessingException("Insufficient funds");
                }
                next = current.subtract(request.amount());
            }
            case "REVERSAL" -> next = current.add(request.amount());
            default -> throw new IllegalArgumentException("Unsupported transaction type");
        }
        balances.put(request.accountId(), next);
    }

    private boolean valid(TransactionRequest r) {
        return r != null && notBlank(r.transactionId()) && notBlank(r.requestId()) &&
                notBlank(r.accountId()) && r.amount() != null && r.amount().compareTo(BigDecimal.ZERO) > 0 &&
                r.currency() != null && r.currency().matches("[A-Z]{3}") &&
                r.sequenceNumber() != null && r.sequenceNumber() > 0 && notBlank(r.transactionType()) &&
                Set.of("CREDIT", "DEBIT", "REVERSAL").contains(r.transactionType());
    }

    private boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private TransactionResult recordDuplicate(TransactionRequest request, String reason) {
        TransactionResult duplicate = new TransactionResult(request.transactionId(), request.requestId(),
                TransactionStatus.DUPLICATE, 0, reason);
        history.add(duplicate);
        log.info("Duplicate ignored: transactionId={}, requestId={}", request.transactionId(), request.requestId());
        return duplicate;
    }

    private String key(String accountId, long sequence) { return accountId + ":" + sequence; }

    private void drainAllPending() {
        for (String accountId : new HashSet<>(pendingByAccountAndSequence.values().stream()
                .map(TransactionRequest::accountId).toList())) {
            drainAccount(accountId);
        }
    }

    private void drainAccount(String accountId) {
        while (true) {
            long nextSequence = lastSequenceByAccount.getOrDefault(accountId, 0L) + 1;
            TransactionRequest pending = pendingByAccountAndSequence.remove(key(accountId, nextSequence));
            if (pending == null) return;
            if (outcomesByTransactionId.containsKey(pending.transactionId())) continue;
            TransactionResult result = processWithRetry(pending);
            outcomesByTransactionId.put(pending.transactionId(), result);
            outcomesByRequestId.put(pending.requestId(), result);
            if (result.status() != TransactionStatus.PROCESSED) return;
            lastSequenceByAccount.put(accountId, pending.sequenceNumber());
        }
    }

    public BigDecimal balance(String accountId) {
        return balances.getOrDefault(accountId, BigDecimal.ZERO);
    }

    public List<TransactionResult> history() {
        synchronized (history) { return List.copyOf(history); }
    }

    public ProcessingSummary summary() {
        Map<String, Long> counts = history().stream().collect(Collectors.groupingBy(
                r -> r.status().name(), TreeMap::new, Collectors.counting()));
        int pending = (int) pendingByAccountAndSequence.size();
        return new ProcessingSummary(received.get(), counts, pending);
    }

    static class TransientProcessingException extends RuntimeException {
        TransientProcessingException(String message) { super(message); }
    }
}

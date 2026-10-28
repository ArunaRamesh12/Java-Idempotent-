package com.example.transactions.service;

import com.example.transactions.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransactionProcessorTest {
    private TransactionProcessor processor;

    @BeforeEach void setUp() { processor = new TransactionProcessor(); }

    private TransactionRequest tx(String id, String req, String account, String type,
                                  String amount, long sequence, boolean failOnce) {
        return new TransactionRequest(id, req, account, type, new BigDecimal(amount), "USD", sequence, failOnce);
    }

    @Test void processesCreditAndDoesNotApplyDuplicateTwice() {
        var first = processor.submitOne(tx("T1", "R1", "A1", "CREDIT", "100", 1, false));
        var duplicate = processor.submitOne(tx("T1", "R1-replay", "A1", "CREDIT", "100", 1, false));
        assertEquals(TransactionStatus.PROCESSED, first.status());
        assertEquals(TransactionStatus.DUPLICATE, duplicate.status());
        assertEquals(new BigDecimal("100"), processor.balance("A1"));
    }

    @Test void buffersOutOfOrderThenProcessesInSequence() {
        var seq2 = processor.submitOne(tx("T2", "R2", "A1", "DEBIT", "40", 2, false));
        assertEquals(TransactionStatus.PENDING_SEQUENCE, seq2.status());
        var seq1 = processor.submitOne(tx("T1", "R1", "A1", "CREDIT", "100", 1, false));
        assertEquals(TransactionStatus.PROCESSED, seq1.status());
        assertEquals(new BigDecimal("60"), processor.balance("A1"));
    }

    @Test void retriesTransientFailureWithoutDuplicateEffect() {
        var result = processor.submitOne(tx("T1", "R1", "A1", "CREDIT", "25", 1, true));
        assertEquals(TransactionStatus.PROCESSED, result.status());
        assertEquals(2, result.attempts());
        assertEquals(new BigDecimal("25"), processor.balance("A1"));
    }

    @Test void rejectsInvalidNegativeAmountAndContinuesBatch() {
        var invalid = tx("T1", "R1", "A1", "CREDIT", "-10", 1, false);
        var valid = tx("T2", "R2", "A2", "CREDIT", "10", 1, false);
        var results = processor.submitBatch(List.of(invalid, valid));
        assertEquals(TransactionStatus.RECEIVED, results.get(0).status());
assertEquals(TransactionStatus.RECEIVED, results.get(1).status());
    }
        @Test
    void detectsDuplicateWhileTransactionIsPending() {
        // Sequence 2 arrives before sequence 1
        var first = processor.submitOne(
                tx("T2", "R2", "A1", "CREDIT", "50", 2, false));

        assertEquals(TransactionStatus.PENDING_SEQUENCE, first.status());

        // Same transaction arrives again
        var duplicate = processor.submitOne(
                tx("T2", "R2", "A1", "CREDIT", "50", 2, false));

        assertEquals(TransactionStatus.DUPLICATE, duplicate.status());

        // Sequence 1 arrives
        var seq1 = processor.submitOne(
                tx("T1", "R1", "A1", "CREDIT", "100", 1, false));

        assertEquals(TransactionStatus.PROCESSED, seq1.status());

        // Pending transaction should execute only once
    
        assertEquals(new BigDecimal("150"), processor.balance("A1"));
    
    }
    @Test
void processesBatchInBackground() throws Exception {
    var transaction = tx("T100", "R100", "A100",
            "CREDIT", "200", 1, false);

    var results = processor.submitBatch(List.of(transaction));

    assertEquals(TransactionStatus.RECEIVED, results.get(0).status());

    // Wait until background processing finishes, max 5 seconds
    long deadline = System.currentTimeMillis() + 5000;

    while (System.currentTimeMillis() < deadline
            && processor.history().stream().noneMatch(
                r -> r.transactionId().equals("T100")
                        && r.status() == TransactionStatus.PROCESSED)) {
        Thread.sleep(20);
    }

    assertTrue(processor.history().stream().anyMatch(
            r -> r.transactionId().equals("T100")
                    && r.status() == TransactionStatus.PROCESSED));

    assertEquals(new BigDecimal("200"), processor.balance("A100"));
}
}

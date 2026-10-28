package com.example.transactions.model;

import java.util.Map;

public record ProcessingSummary(int received, Map<String, Long> countsByStatus, int pendingSequenceCount) {}

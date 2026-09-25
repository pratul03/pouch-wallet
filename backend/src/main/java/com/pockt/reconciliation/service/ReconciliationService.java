package com.pockt.reconciliation.service;

import com.pockt.reconciliation.dto.ReconciliationSummaryResponse;
import com.pockt.reconciliation.dto.ReversalResponse;
import com.pockt.transfer.domain.Transaction;

import java.util.List;
import java.util.UUID;

public interface ReconciliationService {
    ReconciliationSummaryResponse runReconciliation(int olderThanMinutes);
    ReversalResponse reverseTransaction(UUID adminId, UUID transactionId, String reason);
    void disputeTransaction(UUID userId, UUID transactionId, String reason);
    List<Transaction> getPendingTransactions(int limit, int offset);
}

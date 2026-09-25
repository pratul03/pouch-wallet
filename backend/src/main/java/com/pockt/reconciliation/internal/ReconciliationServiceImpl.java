package com.pockt.reconciliation.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pockt.infrastructure.exception.ErrorCode;
import com.pockt.infrastructure.exception.PocktException;
import com.pockt.reconciliation.dto.ReconciliationSummaryResponse;
import com.pockt.reconciliation.dto.ReversalResponse;
import com.pockt.reconciliation.repository.ReconciliationRepository;
import com.pockt.reconciliation.service.ReconciliationService;
import com.pockt.transfer.domain.AuditLog;
import com.pockt.transfer.domain.NotificationOutbox;
import com.pockt.transfer.domain.Transaction;
import com.pockt.transfer.repository.AuditRepository;
import com.pockt.transfer.repository.OutboxRepository;
import com.pockt.transfer.repository.TransactionRepository;
import com.pockt.wallet.domain.Wallet;
import com.pockt.wallet.repository.WalletRepository;
import com.pockt.wallet.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReconciliationServiceImpl implements ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationServiceImpl.class);

    private final ReconciliationRepository reconciliationRepository;
    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final WalletService walletService;
    private final AuditRepository auditRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ReconciliationServiceImpl(
            ReconciliationRepository reconciliationRepository,
            TransactionRepository transactionRepository,
            WalletRepository walletRepository,
            WalletService walletService,
            AuditRepository auditRepository,
            OutboxRepository outboxRepository,
            ObjectMapper objectMapper
    ) {
        this.reconciliationRepository = reconciliationRepository;
        this.transactionRepository = transactionRepository;
        this.walletRepository = walletRepository;
        this.walletService = walletService;
        this.auditRepository = auditRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 60000) // Runs every 60 seconds
    public void scheduledReconciliation() {
        try {
            runReconciliation(5);
        } catch (Exception e) {
            log.error("Scheduled transaction reconciliation run failed: {}", e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public ReconciliationSummaryResponse runReconciliation(int olderThanMinutes) {
        Instant threshold = Instant.now().minus(olderThanMinutes, ChronoUnit.MINUTES);
        List<Transaction> stuckList = reconciliationRepository.findStuckPendingTransactions(threshold, 25);

        if (stuckList.isEmpty()) {
            return new ReconciliationSummaryResponse(0, 0, 0, Instant.now());
        }

        log.info("Reconciliation job found {} stuck PENDING transactions older than {} min", stuckList.size(), olderThanMinutes);
        int autoReconciled = 0;
        int refunded = 0;

        for (Transaction tx : stuckList) {
            try {
                // If a transaction timed out while pending, mark it FAILED and refund sender
                reconciliationRepository.markReversed(tx.id(), "Auto-reconciled: transaction timed out after " + olderThanMinutes + " minutes");
                walletService.credit(tx.senderWalletId(), tx.amount());

                Wallet senderWallet = walletRepository.findById(tx.senderWalletId()).orElse(null);
                UUID userId = senderWallet != null ? senderWallet.userId() : null;

                if (userId != null) {
                    auditRepository.save(new AuditLog(
                            UUID.randomUUID(),
                            "TRANSACTION",
                            tx.id(),
                            "AUTO_RECONCILIATION_REFUND",
                            userId,
                            "{\"status\":\"PENDING\"}",
                            "{\"status\":\"REVERSED\",\"refundedAmount\":" + tx.amount() + "}",
                            null,
                            Instant.now()
                    ));
                    String payload = objectMapper.writeValueAsString(Map.of(
                            "transactionId", tx.id().toString(),
                            "amountCents", tx.amount(),
                            "reason", "Transaction timed out. Funds safely restored to your wallet."
                    ));
                    outboxRepository.save(new NotificationOutbox(
                            UUID.randomUUID(),
                            userId,
                            "TRANSACTION_AUTO_REFUNDED",
                            payload,
                            "PENDING",
                            0,
                            null,
                            Instant.now()
                    ));
                }

                autoReconciled++;
                refunded++;
                log.info("Auto-reconciled & refunded stuck tx {}", tx.id());
            } catch (Exception e) {
                log.error("Failed to auto-reconcile tx {}: {}", tx.id(), e.getMessage(), e);
            }
        }

        return new ReconciliationSummaryResponse(stuckList.size(), autoReconciled, refunded, Instant.now());
    }

    @Override
    @Transactional
    public ReversalResponse reverseTransaction(UUID adminId, UUID transactionId, String reason) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new PocktException(ErrorCode.VALIDATION_ERROR, "Transaction not found", HttpStatus.NOT_FOUND));

        if (!"COMPLETED".equalsIgnoreCase(tx.status().name())) {
            throw new PocktException(ErrorCode.VALIDATION_ERROR,
                    "Only COMPLETED transactions can be reversed. Current status: " + tx.status(), HttpStatus.BAD_REQUEST);
        }

        // Reverse money flow: debit receiver, credit sender
        walletService.debit(tx.receiverWalletId(), tx.amount());
        walletService.credit(tx.senderWalletId(), tx.amount());

        reconciliationRepository.markReversed(transactionId, reason);

        Wallet senderWallet = walletRepository.findById(tx.senderWalletId()).orElseThrow();
        Wallet receiverWallet = walletRepository.findById(tx.receiverWalletId()).orElseThrow();

        // Audit trails
        auditRepository.save(new AuditLog(
                UUID.randomUUID(),
                "WALLET",
                tx.receiverWalletId(),
                "REVERSAL_DEBIT",
                adminId,
                "{\"amount\":" + tx.amount() + "}",
                "{\"reversedTxId\":\"" + transactionId + "\"}",
                null,
                Instant.now()
        ));
        auditRepository.save(new AuditLog(
                UUID.randomUUID(),
                "WALLET",
                tx.senderWalletId(),
                "REVERSAL_CREDIT",
                adminId,
                "{\"amount\":" + tx.amount() + "}",
                "{\"reversedTxId\":\"" + transactionId + "\"}",
                null,
                Instant.now()
        ));

        // Outbox notification to both parties
        try {
            String payloadSender = objectMapper.writeValueAsString(Map.of(
                    "transactionId", transactionId.toString(),
                    "amountCents", tx.amount(),
                    "type", "TRANSFER_REVERSED_REFUNDED"
            ));
            outboxRepository.save(new NotificationOutbox(
                    UUID.randomUUID(),
                    senderWallet.userId(),
                    "TRANSFER_REVERSED_REFUNDED",
                    payloadSender,
                    "PENDING",
                    0,
                    null,
                    Instant.now()
            ));

            String payloadReceiver = objectMapper.writeValueAsString(Map.of(
                    "transactionId", transactionId.toString(),
                    "amountCents", tx.amount(),
                    "type", "TRANSFER_REVERSED_DEBITED"
            ));
            outboxRepository.save(new NotificationOutbox(
                    UUID.randomUUID(),
                    receiverWallet.userId(),
                    "TRANSFER_REVERSED_DEBITED",
                    payloadReceiver,
                    "PENDING",
                    0,
                    null,
                    Instant.now()
            ));
        } catch (JsonProcessingException e) {
            log.error("Failed to build reversal notification payloads", e);
        }

        log.info("Transaction {} reversed by admin {}: reason='{}'", transactionId, adminId, reason);
        return new ReversalResponse(
                transactionId,
                tx.status().name(),
                "REVERSED",
                tx.amount(),
                reason,
                Instant.now()
        );
    }

    @Override
    @Transactional
    public void disputeTransaction(UUID userId, UUID transactionId, String reason) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new PocktException(ErrorCode.VALIDATION_ERROR, "Transaction not found", HttpStatus.NOT_FOUND));

        Wallet senderWallet = walletRepository.findById(tx.senderWalletId()).orElseThrow();
        Wallet receiverWallet = walletRepository.findById(tx.receiverWalletId()).orElseThrow();

        boolean isParty = senderWallet.userId().equals(userId) || receiverWallet.userId().equals(userId);
        if (!isParty) {
            throw new PocktException(ErrorCode.FORBIDDEN, "Not authorized to dispute this transaction", HttpStatus.FORBIDDEN);
        }

        reconciliationRepository.flagDispute(transactionId, reason);
        log.warn("User {} disputed transaction {}: reason='{}'", userId, transactionId, reason);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Transaction> getPendingTransactions(int limit, int offset) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        int safeOffset = Math.max(offset, 0);
        return reconciliationRepository.findPendingTransactions(safeLimit, safeOffset);
    }
}

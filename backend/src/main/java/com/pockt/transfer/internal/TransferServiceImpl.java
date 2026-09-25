package com.pockt.transfer.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pockt.infrastructure.exception.DuplicateTransferException;
import com.pockt.infrastructure.exception.ForbiddenException;
import com.pockt.infrastructure.exception.InsufficientBalanceException;
import com.pockt.infrastructure.exception.InvalidAmountException;
import com.pockt.infrastructure.exception.ReceiverNotFoundException;
import com.pockt.infrastructure.exception.SelfTransferException;
import com.pockt.infrastructure.exception.WalletNotFoundException;
import com.pockt.infrastructure.ratelimit.RateLimitService;
import com.pockt.infrastructure.web.PageResponse;
import com.pockt.transfer.domain.AuditLog;
import com.pockt.transfer.domain.NotificationOutbox;
import com.pockt.transfer.domain.Transaction;
import com.pockt.transfer.domain.TransactionStatus;
import com.pockt.transfer.dto.TransactionResponse;
import com.pockt.transfer.dto.TransferRequest;
import com.pockt.transfer.dto.TransferResponse;
import com.pockt.transfer.event.TransferCompletedEvent;
import com.pockt.transfer.repository.AuditRepository;
import com.pockt.transfer.repository.OutboxRepository;
import com.pockt.transfer.repository.TransactionRepository;
import com.pockt.transfer.service.TransferService;
import com.pockt.user.dto.UserResponse;
import com.pockt.user.service.UserService;
import com.pockt.wallet.domain.Wallet;
import com.pockt.wallet.dto.WalletResponse;
import com.pockt.wallet.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class TransferServiceImpl implements TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferServiceImpl.class);

    private final TransactionRepository transactionRepository;
    private final AuditRepository auditRepository;
    private final OutboxRepository outboxRepository;
    private final WalletService walletService;
    private final UserService userService;
    private final RateLimitService rateLimitService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public TransferServiceImpl(TransactionRepository transactionRepository,
                               AuditRepository auditRepository,
                               OutboxRepository outboxRepository,
                               WalletService walletService,
                               UserService userService,
                               RateLimitService rateLimitService,
                               ApplicationEventPublisher eventPublisher,
                               ObjectMapper objectMapper) {
        this.transactionRepository = transactionRepository;
        this.auditRepository = auditRepository;
        this.outboxRepository = outboxRepository;
        this.walletService = walletService;
        this.userService = userService;
        this.rateLimitService = rateLimitService;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransferResponse send(TransferRequest request, UUID senderUserId) {
        if (request.amount() <= 0) {
            throw new InvalidAmountException("Transfer amount must be greater than zero.");
        }

        String idempotencyKey = request.idempotencyKey();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            idempotencyKey = UUID.randomUUID().toString();
        }

        // Idempotency check: return existing transaction if already processed
        Optional<Transaction> existingTxOpt = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existingTxOpt.isPresent()) {
            Transaction existing = existingTxOpt.get();
            log.info("Idempotency match found for key={}: returning cached transaction {}", idempotencyKey, existing.id());
            Wallet senderWallet = walletService.getWalletForUpdate(existing.senderWalletId());
            UserResponse receiver = userService.findByPhone(request.receiverPhone())
                    .orElse(new UserResponse(UUID.randomUUID(), request.receiverPhone(), "Recipient", "VERIFIED", Instant.now()));

            return TransferResponse.of(
                    existing.id(),
                    existing.status().name(),
                    existing.amount(),
                    existing.currency(),
                    receiver.fullName(),
                    senderWallet.balance(),
                    existing.createdAt()
            );
        }

        UserResponse senderUser = userService.getProfile(senderUserId);
        if (senderUser.phone().equals(request.receiverPhone())) {
            throw new SelfTransferException();
        }

        // Rate limit check
        rateLimitService.checkTransferRateLimit(senderUserId.toString());

        // Resolve receiver
        UserResponse receiverUser = userService.findByPhone(request.receiverPhone())
                .orElseThrow(() -> new ReceiverNotFoundException(request.receiverPhone()));

        if (receiverUser.id().equals(senderUserId)) {
            throw new SelfTransferException();
        }

        String currency = request.currency().toUpperCase().trim();
        Wallet senderWallet = walletService.getOrCreateWallet(senderUserId, currency);
        Wallet receiverWallet = walletService.getOrCreateWallet(receiverUser.id(), currency);

        if (senderWallet.id().equals(receiverWallet.id())) {
            throw new SelfTransferException();
        }

        // DEADLOCK PREVENTION: Always lock wallets in consistent UUID order
        UUID firstId = senderWallet.id().compareTo(receiverWallet.id()) < 0 ? senderWallet.id() : receiverWallet.id();
        UUID secondId = senderWallet.id().compareTo(receiverWallet.id()) < 0 ? receiverWallet.id() : senderWallet.id();

        Wallet lockedFirst = walletService.getWalletForUpdate(firstId);
        Wallet lockedSecond = walletService.getWalletForUpdate(secondId);

        Wallet lockedSender = lockedFirst.id().equals(senderWallet.id()) ? lockedFirst : lockedSecond;
        Wallet lockedReceiver = lockedFirst.id().equals(senderWallet.id()) ? lockedSecond : lockedFirst;

        // Balance check inside lock
        if (lockedSender.balance() < request.amount()) {
            throw new InsufficientBalanceException();
        }

        // Debit and credit
        walletService.debit(lockedSender.id(), request.amount());
        walletService.credit(lockedReceiver.id(), request.amount());

        Instant now = Instant.now();
        UUID txId = UUID.randomUUID();

        // Record Transaction
        Transaction tx = new Transaction(
                txId,
                idempotencyKey,
                lockedSender.id(),
                lockedReceiver.id(),
                request.amount(),
                currency,
                TransactionStatus.COMPLETED,
                request.description(),
                null,
                now,
                now
        );
        transactionRepository.save(tx);

        // Record Audit Logs (Append-only)
        recordAuditLogs(tx, senderUserId, receiverUser.id(), lockedSender, lockedReceiver, request.amount());

        // Record Outbox entries for transactional notifications
        recordOutboxEntries(tx, senderUserId, receiverUser.id(), senderUser.fullName(), receiverUser.fullName());

        // In-process event for decoupled notification handling
        eventPublisher.publishEvent(new TransferCompletedEvent(
                txId,
                senderUserId,
                receiverUser.id(),
                senderUser.fullName(),
                receiverUser.fullName(),
                request.amount(),
                currency
        ));

        long senderBalanceAfter = lockedSender.balance() - request.amount();

        log.info("Transfer completed successfully: txId={}, amount={} {}, sender={}, receiver={}",
                txId, request.amount(), currency, senderUserId, receiverUser.id());

        return TransferResponse.of(
                txId,
                tx.status().name(),
                tx.amount(),
                currency,
                receiverUser.fullName(),
                senderBalanceAfter,
                now
        );
    }

    private void recordAuditLogs(Transaction tx, UUID senderUserId, UUID receiverUserId,
                                 Wallet senderBefore, Wallet receiverBefore, long amount) {
        Instant now = Instant.now();
        // Sender audit log (DEBITED)
        auditRepository.save(new AuditLog(
                UUID.randomUUID(),
                "WALLET",
                senderBefore.id(),
                "DEBITED",
                senderUserId,
                "{\"balance\":" + senderBefore.balance() + "}",
                "{\"balance\":" + (senderBefore.balance() - amount) + ",\"txId\":\"" + tx.id() + "\"}",
                null,
                now
        ));

        // Receiver audit log (CREDITED)
        auditRepository.save(new AuditLog(
                UUID.randomUUID(),
                "WALLET",
                receiverBefore.id(),
                "CREDITED",
                senderUserId,
                "{\"balance\":" + receiverBefore.balance() + "}",
                "{\"balance\":" + (receiverBefore.balance() + amount) + ",\"txId\":\"" + tx.id() + "\"}",
                null,
                now
        ));
    }

    private void recordOutboxEntries(Transaction tx, UUID senderUserId, UUID receiverUserId,
                                     String senderName, String receiverName) {
        try {
            Instant now = Instant.now();

            String senderPayload = objectMapper.writeValueAsString(Map.of(
                    "transactionId", tx.id().toString(),
                    "amount", tx.amount(),
                    "currency", tx.currency(),
                    "recipientName", receiverName,
                    "title", "Transfer Sent",
                    "body", "You sent " + tx.amount() / 100.0 + " " + tx.currency() + " to " + receiverName
            ));

            String receiverPayload = objectMapper.writeValueAsString(Map.of(
                    "transactionId", tx.id().toString(),
                    "amount", tx.amount(),
                    "currency", tx.currency(),
                    "senderName", senderName,
                    "title", "Transfer Received",
                    "body", senderName + " sent you " + tx.amount() / 100.0 + " " + tx.currency()
            ));

            outboxRepository.save(new NotificationOutbox(
                    UUID.randomUUID(),
                    senderUserId,
                    "TRANSFER_SENT",
                    senderPayload,
                    "PENDING",
                    0,
                    null,
                    now
            ));

            outboxRepository.save(new NotificationOutbox(
                    UUID.randomUUID(),
                    receiverUserId,
                    "TRANSFER_RECEIVED",
                    receiverPayload,
                    "PENDING",
                    0,
                    null,
                    now
            ));
        } catch (Exception e) {
            log.error("Failed to serialize notification outbox payload", e);
            throw new RuntimeException("Could not serialize notification payload", e);
        }
    }

    @Override
    public PageResponse<TransactionResponse> getHistory(UUID userId, String cursor, int limit) {
        List<WalletResponse> userWallets = walletService.getUserWallets(userId);
        if (userWallets.isEmpty()) {
            return new PageResponse<>(Collections.emptyList(), null, false);
        }

        // For MVP, look up transactions across user's primary wallet
        UUID walletId = userWallets.getFirst().id();

        Instant cursorTime = null;
        UUID cursorId = null;

        if (cursor != null && !cursor.isBlank()) {
            try {
                String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                String[] parts = decoded.split("_");
                if (parts.length == 2) {
                    cursorTime = Instant.parse(parts[0]);
                    cursorId = UUID.fromString(parts[1]);
                }
            } catch (Exception e) {
                log.warn("Invalid cursor format: {}", cursor);
            }
        }

        // Fetch limit + 1 to know if there's a next page
        List<Transaction> txList = transactionRepository.findByWalletIdCursor(walletId, cursorTime, cursorId, limit + 1);

        boolean hasMore = txList.size() > limit;
        List<Transaction> pageItems = hasMore ? txList.subList(0, limit) : txList;

        List<TransactionResponse> responses = new ArrayList<>();
        for (Transaction tx : pageItems) {
            boolean isDebit = tx.senderWalletId().equals(walletId);
            String type = isDebit ? "DEBIT" : "CREDIT";
            String counterpartyName = isDebit ? "Recipient" : "Sender";

            try {
                UUID counterpartyWalletId = isDebit ? tx.receiverWalletId() : tx.senderWalletId();
                // Find owner of counterparty wallet
                Wallet cpWallet = walletService.getWalletForUpdate(counterpartyWalletId);
                UserResponse cpUser = userService.getProfile(cpWallet.userId());
                counterpartyName = cpUser.fullName();
            } catch (Exception e) {
                log.debug("Could not resolve counterparty name for tx {}", tx.id());
            }

            responses.add(TransactionResponse.of(
                    tx.id(),
                    type,
                    tx.amount(),
                    tx.currency(),
                    counterpartyName,
                    tx.description(),
                    tx.status().name(),
                    tx.createdAt()
            ));
        }

        String nextCursor = null;
        if (hasMore && !pageItems.isEmpty()) {
            Transaction last = pageItems.getLast();
            String rawCursor = last.createdAt().toString() + "_" + last.id().toString();
            nextCursor = Base64.getUrlEncoder().encodeToString(rawCursor.getBytes(StandardCharsets.UTF_8));
        }

        return new PageResponse<>(responses, nextCursor, hasMore);
    }

    @Override
    public TransactionResponse getById(UUID transactionId, UUID requestingUserId) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new WalletNotFoundException("Transaction not found: " + transactionId));

        Wallet senderWallet = walletService.getWalletForUpdate(tx.senderWalletId());
        Wallet receiverWallet = walletService.getWalletForUpdate(tx.receiverWalletId());

        if (!senderWallet.userId().equals(requestingUserId) && !receiverWallet.userId().equals(requestingUserId)) {
            throw new ForbiddenException("You are not authorized to view this transaction.");
        }

        boolean isDebit = senderWallet.userId().equals(requestingUserId);
        String type = isDebit ? "DEBIT" : "CREDIT";

        UUID counterpartyUserId = isDebit ? receiverWallet.userId() : senderWallet.userId();
        String counterpartyName = "Unknown";
        try {
            counterpartyName = userService.getProfile(counterpartyUserId).fullName();
        } catch (Exception ignored) {}

        return TransactionResponse.of(
                tx.id(),
                type,
                tx.amount(),
                tx.currency(),
                counterpartyName,
                tx.description(),
                tx.status().name(),
                tx.createdAt()
        );
    }
}

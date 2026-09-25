package com.pockt.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pockt.infrastructure.exception.InsufficientBalanceException;
import com.pockt.infrastructure.exception.ReceiverNotFoundException;
import com.pockt.infrastructure.exception.SelfTransferException;
import com.pockt.infrastructure.ratelimit.RateLimitService;
import com.pockt.transfer.domain.AuditLog;
import com.pockt.transfer.domain.NotificationOutbox;
import com.pockt.transfer.domain.Transaction;
import com.pockt.transfer.domain.TransactionStatus;
import com.pockt.transfer.dto.TransferRequest;
import com.pockt.transfer.dto.TransferResponse;
import com.pockt.transfer.event.TransferCompletedEvent;
import com.pockt.transfer.internal.TransferServiceImpl;
import com.pockt.transfer.repository.AuditRepository;
import com.pockt.transfer.repository.OutboxRepository;
import com.pockt.transfer.repository.TransactionRepository;
import com.pockt.user.dto.UserResponse;
import com.pockt.user.service.UserService;
import com.pockt.wallet.domain.Wallet;
import com.pockt.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AuditRepository auditRepository;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private WalletService walletService;

    @Mock
    private UserService userService;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private TransferServiceImpl transferService;

    private UUID senderUserId;
    private UUID receiverUserId;
    private UUID senderWalletId;
    private UUID receiverWalletId;
    private String senderPhone;
    private String receiverPhone;

    @BeforeEach
    void setUp() {
        senderUserId = UUID.randomUUID();
        receiverUserId = UUID.randomUUID();
        senderWalletId = UUID.randomUUID();
        receiverWalletId = UUID.randomUUID();
        senderPhone = "+2348011111111";
        receiverPhone = "+2348022222222";
    }

    @Test
    void transfer_happyPath_debitsAndCredits() {
        TransferRequest request = new TransferRequest(receiverPhone, 3000L, "USD", "Lunch split", "key-1");

        UserResponse senderUser = new UserResponse(senderUserId, senderPhone, "Ade Sender", "VERIFIED", Instant.now());
        UserResponse receiverUser = new UserResponse(receiverUserId, receiverPhone, "Bob Receiver", "VERIFIED", Instant.now());

        Wallet senderWallet = new Wallet(senderWalletId, senderUserId, "USD", 10000L, true, Instant.now(), Instant.now());
        Wallet receiverWallet = new Wallet(receiverWalletId, receiverUserId, "USD", 1000L, true, Instant.now(), Instant.now());

        when(transactionRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(userService.getProfile(senderUserId)).thenReturn(senderUser);
        when(userService.findByPhone(receiverPhone)).thenReturn(Optional.of(receiverUser));
        when(walletService.getOrCreateWallet(senderUserId, "USD")).thenReturn(senderWallet);
        when(walletService.getOrCreateWallet(receiverUserId, "USD")).thenReturn(receiverWallet);
        when(walletService.getWalletForUpdate(senderWalletId)).thenReturn(senderWallet);
        when(walletService.getWalletForUpdate(receiverWalletId)).thenReturn(receiverWallet);

        TransferResponse response = transferService.send(request, senderUserId);

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.amount()).isEqualTo(3000L);
        assertThat(response.senderBalanceAfter()).isEqualTo(7000L);

        verify(walletService).debit(senderWalletId, 3000L);
        verify(walletService).credit(receiverWalletId, 3000L);
        verify(transactionRepository).save(any(Transaction.class));
        verify(auditRepository, times(2)).save(any(AuditLog.class));
        verify(outboxRepository, times(2)).save(any(NotificationOutbox.class));
        verify(eventPublisher).publishEvent(any(TransferCompletedEvent.class));
    }

    @Test
    void transfer_selfTransfer_throwsSelfTransferException() {
        TransferRequest request = new TransferRequest(senderPhone, 1000L, "USD", "Self test", "key-2");

        UserResponse senderUser = new UserResponse(senderUserId, senderPhone, "Ade Sender", "VERIFIED", Instant.now());
        when(transactionRepository.findByIdempotencyKey("key-2")).thenReturn(Optional.empty());
        when(userService.getProfile(senderUserId)).thenReturn(senderUser);

        assertThatThrownBy(() -> transferService.send(request, senderUserId))
                .isInstanceOf(SelfTransferException.class);

        verify(walletService, never()).debit(any(), anyLong());
    }

    @Test
    void transfer_insufficientBalance_throwsException_noDebitOccurs() {
        TransferRequest request = new TransferRequest(receiverPhone, 50000L, "USD", "Too much", "key-3");

        UserResponse senderUser = new UserResponse(senderUserId, senderPhone, "Ade Sender", "VERIFIED", Instant.now());
        UserResponse receiverUser = new UserResponse(receiverUserId, receiverPhone, "Bob Receiver", "VERIFIED", Instant.now());

        Wallet senderWallet = new Wallet(senderWalletId, senderUserId, "USD", 1000L, true, Instant.now(), Instant.now());
        Wallet receiverWallet = new Wallet(receiverWalletId, receiverUserId, "USD", 0L, true, Instant.now(), Instant.now());

        when(transactionRepository.findByIdempotencyKey("key-3")).thenReturn(Optional.empty());
        when(userService.getProfile(senderUserId)).thenReturn(senderUser);
        when(userService.findByPhone(receiverPhone)).thenReturn(Optional.of(receiverUser));
        when(walletService.getOrCreateWallet(senderUserId, "USD")).thenReturn(senderWallet);
        when(walletService.getOrCreateWallet(receiverUserId, "USD")).thenReturn(receiverWallet);
        when(walletService.getWalletForUpdate(senderWalletId)).thenReturn(senderWallet);
        when(walletService.getWalletForUpdate(receiverWalletId)).thenReturn(receiverWallet);

        assertThatThrownBy(() -> transferService.send(request, senderUserId))
                .isInstanceOf(InsufficientBalanceException.class);

        verify(walletService, never()).debit(any(), anyLong());
        verify(walletService, never()).credit(any(), anyLong());
    }

    @Test
    void transfer_duplicateIdempotencyKey_returnsCachedResult() {
        Transaction existing = new Transaction(
                UUID.randomUUID(),
                "key-dup",
                senderWalletId,
                receiverWalletId,
                2000L,
                "USD",
                TransactionStatus.COMPLETED,
                "Old transfer",
                null,
                Instant.now(),
                Instant.now()
        );

        when(transactionRepository.findByIdempotencyKey("key-dup")).thenReturn(Optional.of(existing));
        when(walletService.getWalletForUpdate(senderWalletId)).thenReturn(
                new Wallet(senderWalletId, senderUserId, "USD", 8000L, true, Instant.now(), Instant.now())
        );

        TransferRequest request = new TransferRequest(receiverPhone, 2000L, "USD", "Old transfer", "key-dup");
        TransferResponse response = transferService.send(request, senderUserId);

        assertThat(response.transactionId()).isEqualTo(existing.id());
        assertThat(response.status()).isEqualTo("COMPLETED");
        verify(walletService, never()).debit(any(), anyLong());
    }

    @Test
    void transfer_receiverPhoneNotFound_throwsReceiverNotFoundException() {
        TransferRequest request = new TransferRequest("+2348099999999", 1000L, "USD", "Unknown recipient", "key-unknown");
        UserResponse senderUser = new UserResponse(senderUserId, senderPhone, "Ade Sender", "VERIFIED", Instant.now());

        when(transactionRepository.findByIdempotencyKey("key-unknown")).thenReturn(Optional.empty());
        when(userService.getProfile(senderUserId)).thenReturn(senderUser);
        when(userService.findByPhone("+2348099999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.send(request, senderUserId))
                .isInstanceOf(ReceiverNotFoundException.class);
    }
}

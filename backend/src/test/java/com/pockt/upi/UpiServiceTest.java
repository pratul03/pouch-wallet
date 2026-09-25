package com.pockt.upi;

import com.pockt.infrastructure.exception.SelfTransferException;
import com.pockt.infrastructure.exception.UpiHandleAlreadyExistsException;
import com.pockt.infrastructure.exception.UpiHandleNotFoundException;
import com.pockt.transfer.dto.TransferRequest;
import com.pockt.transfer.dto.TransferResponse;
import com.pockt.transfer.service.TransferService;
import com.pockt.upi.domain.UpiHandle;
import com.pockt.upi.dto.CreateUpiHandleRequest;
import com.pockt.upi.dto.UpiHandleResponse;
import com.pockt.upi.dto.UpiPaymentRequest;
import com.pockt.upi.dto.UpiPaymentResponse;
import com.pockt.upi.dto.VerifyVpaResponse;
import com.pockt.upi.internal.UpiServiceImpl;
import com.pockt.upi.repository.UpiRepository;
import com.pockt.user.dto.UserResponse;
import com.pockt.user.service.UserService;
import com.pockt.wallet.domain.Wallet;
import com.pockt.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpiServiceTest {

    @Mock private UpiRepository upiRepository;
    @Mock private UserService userService;
    @Mock private WalletService walletService;
    @Mock private TransferService transferService;

    private UpiServiceImpl upiService;

    private final UUID senderId = UUID.randomUUID();
    private final UUID receiverId = UUID.randomUUID();
    private final UUID walletId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        upiService = new UpiServiceImpl(upiRepository, userService, walletService, transferService);
    }

    @Test
    void createHandle_shouldSucceedWhenAvailable() {
        when(upiRepository.existsByVpa("alice@pockt")).thenReturn(false);
        Wallet wallet = new Wallet(walletId, senderId, "USD", 0L, true, Instant.now(), Instant.now());
        when(walletService.getOrCreateWallet(senderId, "USD")).thenReturn(wallet);
        when(upiRepository.findByUserId(senderId)).thenReturn(List.of());

        CreateUpiHandleRequest request = new CreateUpiHandleRequest("alice@pockt", null);
        UpiHandleResponse response = upiService.createHandle(senderId, request);

        assertThat(response).isNotNull();
        assertThat(response.vpa()).isEqualTo("alice@pockt");
        verify(upiRepository).create(any(UpiHandle.class));
    }

    @Test
    void createHandle_shouldThrowWhenTaken() {
        when(upiRepository.existsByVpa("alice@pockt")).thenReturn(true);
        CreateUpiHandleRequest request = new CreateUpiHandleRequest("alice@pockt", null);

        assertThatThrownBy(() -> upiService.createHandle(senderId, request))
                .isInstanceOf(UpiHandleAlreadyExistsException.class);
    }

    @Test
    void verifyVpa_shouldReturnAccountNameWhenExists() {
        UpiHandle handle = new UpiHandle(UUID.randomUUID(), receiverId, "bob@pockt", walletId, true, Instant.now());
        when(upiRepository.findByVpa("bob@pockt")).thenReturn(Optional.of(handle));
        when(userService.getProfile(receiverId)).thenReturn(new UserResponse(receiverId, "+1234567890", "Bob Jones", "VERIFIED", Instant.now()));

        VerifyVpaResponse response = upiService.verifyVpa("bob@pockt");

        assertThat(response.isValid()).isTrue();
        assertThat(response.accountHolderName()).isEqualTo("Bob Jones");
    }

    @Test
    void payViaUpi_shouldSucceed() {
        UpiHandle receiverHandle = new UpiHandle(UUID.randomUUID(), receiverId, "bob@pockt", walletId, true, Instant.now());
        when(upiRepository.findByVpa("bob@pockt")).thenReturn(Optional.of(receiverHandle));
        when(userService.getProfile(receiverId)).thenReturn(new UserResponse(receiverId, "+1987654321", "Bob Jones", "VERIFIED", Instant.now()));

        UpiHandle senderHandle = new UpiHandle(UUID.randomUUID(), senderId, "alice@pockt", walletId, true, Instant.now());
        when(upiRepository.findDefaultByUserId(senderId)).thenReturn(Optional.of(senderHandle));

        UUID txId = UUID.randomUUID();
        TransferResponse transferResp = TransferResponse.of(txId, "COMPLETED", 2500L, "USD", "Bob Jones", 7500L, Instant.now());
        when(transferService.send(any(TransferRequest.class), eq(senderId))).thenReturn(transferResp);

        UpiPaymentRequest request = new UpiPaymentRequest("bob@pockt", 2500L, "123456", "Lunch", "idemp-1");
        UpiPaymentResponse response = upiService.payViaUpi(senderId, request);

        assertThat(response).isNotNull();
        assertThat(response.transactionId()).isEqualTo(txId);
        assertThat(response.amount()).isEqualTo(2500L);
        assertThat(response.receiverVpa()).isEqualTo("bob@pockt");
        verify(userService).verifyPin(senderId, "123456");
    }

    @Test
    void payViaUpi_shouldPreventSelfTransfer() {
        UpiHandle myHandle = new UpiHandle(UUID.randomUUID(), senderId, "alice@pockt", walletId, true, Instant.now());
        when(upiRepository.findByVpa("alice@pockt")).thenReturn(Optional.of(myHandle));

        UpiPaymentRequest request = new UpiPaymentRequest("alice@pockt", 2500L, "123456", "Self", "idemp-2");

        assertThatThrownBy(() -> upiService.payViaUpi(senderId, request))
                .isInstanceOf(SelfTransferException.class);
    }
}

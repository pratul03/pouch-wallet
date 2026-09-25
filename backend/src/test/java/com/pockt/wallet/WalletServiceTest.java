package com.pockt.wallet;

import com.pockt.infrastructure.exception.ForbiddenException;
import com.pockt.infrastructure.exception.InvalidAmountException;
import com.pockt.wallet.domain.Wallet;
import com.pockt.wallet.dto.WalletResponse;
import com.pockt.wallet.internal.WalletServiceImpl;
import com.pockt.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private WalletServiceImpl walletService;

    @Test
    void createWallet_shouldCreateAndReturnWallet() {
        UUID userId = UUID.randomUUID();
        when(walletRepository.findByUserIdAndCurrency(userId, "USD")).thenReturn(Optional.empty());

        WalletResponse response = walletService.createWallet(userId, "USD");

        assertThat(response).isNotNull();
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.balance()).isEqualTo(0L);
        verify(walletRepository).create(any(Wallet.class));
    }

    @Test
    void topUp_shouldCreditBalance() {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        Wallet walletBefore = new Wallet(walletId, userId, "USD", 5000L, true, Instant.now(), Instant.now());
        Wallet walletAfter = new Wallet(walletId, userId, "USD", 15000L, true, Instant.now(), Instant.now());

        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(walletBefore);
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(walletAfter));

        WalletResponse response = walletService.topUp(walletId, 10000L, userId);

        assertThat(response.balance()).isEqualTo(15000L);
        verify(walletRepository).credit(walletId, 10000L);
    }

    @Test
    void topUp_shouldThrowWhenUnauthorized() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        Wallet wallet = new Wallet(walletId, userId, "USD", 5000L, true, Instant.now(), Instant.now());

        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(wallet);

        assertThatThrownBy(() -> walletService.topUp(walletId, 10000L, otherUserId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void topUp_shouldThrowWhenAmountZeroOrNegative() {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();

        assertThatThrownBy(() -> walletService.topUp(walletId, 0L, userId))
                .isInstanceOf(InvalidAmountException.class);

        assertThatThrownBy(() -> walletService.topUp(walletId, -100L, userId))
                .isInstanceOf(InvalidAmountException.class);
    }
}

package com.pockt.card;

import com.pockt.card.domain.Card;
import com.pockt.card.domain.CreditAccount;
import com.pockt.card.dto.ApplyCreditCardRequest;
import com.pockt.card.dto.CardChargeRequest;
import com.pockt.card.dto.CardChargeResponse;
import com.pockt.card.dto.CardResponse;
import com.pockt.card.dto.CardRevealResponse;
import com.pockt.card.dto.CreditAccountResponse;
import com.pockt.card.dto.IssueDebitCardRequest;
import com.pockt.card.dto.RepayCreditRequest;
import com.pockt.card.dto.RevealCardRequest;
import com.pockt.card.internal.CardServiceImpl;
import com.pockt.card.repository.CardRepository;
import com.pockt.card.repository.CreditAccountRepository;
import com.pockt.infrastructure.exception.CardInactiveException;
import com.pockt.user.dto.UserResponse;
import com.pockt.user.service.UserService;
import com.pockt.wallet.domain.Wallet;
import com.pockt.wallet.dto.WalletResponse;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardServiceTest {

    @Mock private CardRepository cardRepository;
    @Mock private CreditAccountRepository creditAccountRepository;
    @Mock private WalletService walletService;
    @Mock private UserService userService;

    private CardServiceImpl cardService;

    private final UUID userId = UUID.randomUUID();
    private final UUID cardId = UUID.randomUUID();
    private final UUID walletId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        cardService = new CardServiceImpl(cardRepository, creditAccountRepository, walletService, userService);
    }

    @Test
    void issueDebitCard_shouldGenerateMaskedCard() {
        when(userService.getProfile(userId)).thenReturn(new UserResponse(userId, "+1234567890", "Ade Bello", "VERIFIED", Instant.now()));
        Wallet wallet = new Wallet(walletId, userId, "USD", 1000L, true, Instant.now(), Instant.now());
        when(walletService.getOrCreateWallet(userId, "USD")).thenReturn(wallet);

        IssueDebitCardRequest request = new IssueDebitCardRequest("VISA", null);
        CardResponse response = cardService.issueDebitCard(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.cardType()).isEqualTo("DEBIT");
        assertThat(response.cardNetwork()).isEqualTo("VISA");
        assertThat(response.cardNumberMasked()).startsWith("•••• •••• •••• ");
        verify(cardRepository).create(any(Card.class));
    }

    @Test
    void applyCreditCard_shouldCreateCardAndCreditAccount() {
        when(creditAccountRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(userService.getProfile(userId)).thenReturn(new UserResponse(userId, "+1234567890", "Ade Bello", "VERIFIED", Instant.now()));

        ApplyCreditCardRequest request = new ApplyCreditCardRequest("RUPAY");
        CreditAccountResponse response = cardService.applyCreditCard(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.totalCreditLimit()).isEqualTo(2500000L);
        assertThat(response.availableCreditLimit()).isEqualTo(2500000L);
        verify(cardRepository).create(any(Card.class));
        verify(creditAccountRepository).create(any(CreditAccount.class));
    }

    @Test
    void revealCard_shouldReturnFullPanAndCvvWhenPinValid() {
        Card card = new Card(
                cardId, userId, walletId, "DEBIT", "RUPAY",
                "•••• •••• •••• 1234", "6521123456781234", 12, 2030, "789",
                "Ade Bello", 500000L, true, "ACTIVE", Instant.now(), Instant.now()
        );

        doNothing().when(userService).verifyPin(userId, "123456");
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));

        RevealCardRequest request = new RevealCardRequest("123456");
        CardRevealResponse response = cardService.revealCard(userId, cardId, request);

        assertThat(response).isNotNull();
        assertThat(response.cardNumberFull()).isEqualTo("6521 1234 5678 1234");
        assertThat(response.cvv()).isEqualTo("789");
    }

    @Test
    void simulateCardCharge_debitShouldDeductFromWallet() {
        Card card = new Card(
                cardId, userId, walletId, "DEBIT", "RUPAY",
                "•••• •••• •••• 1234", "6521123456781234", 12, 2030, "789",
                "Ade Bello", 500000L, true, "ACTIVE", Instant.now(), Instant.now()
        );

        when(cardRepository.findByIdForUpdate(cardId)).thenReturn(Optional.of(card));

        CardChargeRequest request = new CardChargeRequest(2500L, "Amazon", "789");
        CardChargeResponse response = cardService.simulateCardCharge(cardId, request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("APPROVED");
        verify(walletService).debit(walletId, 2500L);
    }

    @Test
    void simulateCardCharge_shouldThrowWhenCardFrozen() {
        Card card = new Card(
                cardId, userId, walletId, "DEBIT", "RUPAY",
                "•••• •••• •••• 1234", "6521123456781234", 12, 2030, "789",
                "Ade Bello", 500000L, true, "FROZEN", Instant.now(), Instant.now()
        );

        when(cardRepository.findByIdForUpdate(cardId)).thenReturn(Optional.of(card));
        CardChargeRequest request = new CardChargeRequest(2500L, "Amazon", "789");

        assertThatThrownBy(() -> cardService.simulateCardCharge(cardId, request))
                .isInstanceOf(CardInactiveException.class);
    }
}

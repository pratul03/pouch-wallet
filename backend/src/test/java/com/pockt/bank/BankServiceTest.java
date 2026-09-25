package com.pockt.bank;

import com.pockt.bank.domain.BankAccount;
import com.pockt.bank.domain.BankTransaction;
import com.pockt.bank.dto.AddMoneyFromBankRequest;
import com.pockt.bank.dto.BankAccountResponse;
import com.pockt.bank.dto.BankTransactionResponse;
import com.pockt.bank.dto.LinkBankAccountRequest;
import com.pockt.bank.dto.WithdrawToBankRequest;
import com.pockt.bank.internal.BankServiceImpl;
import com.pockt.bank.repository.BankAccountRepository;
import com.pockt.bank.repository.BankTransactionRepository;
import com.pockt.infrastructure.exception.BankInsufficientFundsException;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankServiceTest {

    @Mock private BankAccountRepository bankAccountRepository;
    @Mock private BankTransactionRepository bankTransactionRepository;
    @Mock private WalletService walletService;
    @Mock private UserService userService;

    private BankServiceImpl bankService;

    private final UUID userId = UUID.randomUUID();
    private final UUID bankAccountId = UUID.randomUUID();
    private final UUID walletId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        bankService = new BankServiceImpl(bankAccountRepository, bankTransactionRepository, walletService, userService);
    }

    @Test
    void linkBankAccount_shouldSucceed() {
        when(bankAccountRepository.existsByAccountNumberAndIfsc(userId, "1234567890", "HDFC0001234")).thenReturn(false);
        when(bankAccountRepository.findByUserId(userId)).thenReturn(List.of());

        LinkBankAccountRequest request = new LinkBankAccountRequest("HDFC Bank", "1234567890", "HDFC0001234", "Ade Bello");
        BankAccountResponse response = bankService.linkBankAccount(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.bankName()).isEqualTo("HDFC Bank");
        assertThat(response.isPrimary()).isTrue();
        verify(bankAccountRepository).create(any(BankAccount.class));
    }

    @Test
    void addMoneyFromBank_shouldDepositToWallet() {
        BankAccount bankAccount = new BankAccount(
                bankAccountId, userId, "HDFC Bank", "1234567890", "HDFC0001234",
                "Ade Bello", 100000L, true, "ACTIVE", Instant.now(), Instant.now()
        );

        Wallet wallet = new Wallet(walletId, userId, "USD", 5000L, true, Instant.now(), Instant.now());
        WalletResponse walletResp = new WalletResponse(walletId, "USD", 5000L, "$50.00", true, Instant.now());

        doNothing().when(userService).verifyPin(userId, "123456");
        when(bankAccountRepository.findByIdForUpdate(bankAccountId)).thenReturn(Optional.of(bankAccount));
        when(walletService.getUserWallets(userId)).thenReturn(List.of(walletResp));
        when(walletService.getWalletForUpdate(walletId)).thenReturn(wallet);

        AddMoneyFromBankRequest request = new AddMoneyFromBankRequest(bankAccountId, null, 2000L, "123456");
        BankTransactionResponse response = bankService.addMoneyFromBank(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.amount()).isEqualTo(2000L);
        assertThat(response.type()).isEqualTo("DEPOSIT_TO_WALLET");
        verify(bankAccountRepository).updateBalance(bankAccountId, 98000L);
        verify(walletService).credit(walletId, 2000L);
        verify(bankTransactionRepository).create(any(BankTransaction.class));
    }

    @Test
    void addMoneyFromBank_shouldThrowWhenBankBalanceInsufficient() {
        BankAccount bankAccount = new BankAccount(
                bankAccountId, userId, "HDFC Bank", "1234567890", "HDFC0001234",
                "Ade Bello", 500L, true, "ACTIVE", Instant.now(), Instant.now()
        );

        doNothing().when(userService).verifyPin(userId, "123456");
        when(bankAccountRepository.findByIdForUpdate(bankAccountId)).thenReturn(Optional.of(bankAccount));

        AddMoneyFromBankRequest request = new AddMoneyFromBankRequest(bankAccountId, null, 2000L, "123456");

        assertThatThrownBy(() -> bankService.addMoneyFromBank(userId, request))
                .isInstanceOf(BankInsufficientFundsException.class);
    }

    @Test
    void withdrawToBank_shouldDebitWalletAndCreditBank() {
        BankAccount bankAccount = new BankAccount(
                bankAccountId, userId, "HDFC Bank", "1234567890", "HDFC0001234",
                "Ade Bello", 10000L, true, "ACTIVE", Instant.now(), Instant.now()
        );

        Wallet wallet = new Wallet(walletId, userId, "USD", 10000L, true, Instant.now(), Instant.now());
        WalletResponse walletResp = new WalletResponse(walletId, "USD", 10000L, "$100.00", true, Instant.now());

        doNothing().when(userService).verifyPin(userId, "123456");
        when(bankAccountRepository.findByIdForUpdate(bankAccountId)).thenReturn(Optional.of(bankAccount));
        when(walletService.getUserWallets(userId)).thenReturn(List.of(walletResp));
        when(walletService.getWalletForUpdate(walletId)).thenReturn(wallet);

        WithdrawToBankRequest request = new WithdrawToBankRequest(bankAccountId, null, 3000L, "123456");
        BankTransactionResponse response = bankService.withdrawToBank(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.amount()).isEqualTo(3000L);
        assertThat(response.type()).isEqualTo("WITHDRAW_TO_BANK");
        verify(walletService).debit(walletId, 3000L);
        verify(bankAccountRepository).updateBalance(bankAccountId, 13000L);
        verify(bankTransactionRepository).create(any(BankTransaction.class));
    }
}

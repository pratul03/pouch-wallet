package com.pockt.bank.internal;

import com.pockt.bank.domain.BankAccount;
import com.pockt.bank.domain.BankTransaction;
import com.pockt.bank.dto.AddMoneyFromBankRequest;
import com.pockt.bank.dto.BankAccountResponse;
import com.pockt.bank.dto.BankTransactionResponse;
import com.pockt.bank.dto.LinkBankAccountRequest;
import com.pockt.bank.dto.WithdrawToBankRequest;
import com.pockt.bank.repository.BankAccountRepository;
import com.pockt.bank.repository.BankTransactionRepository;
import com.pockt.bank.service.BankService;
import com.pockt.infrastructure.exception.BankAccountAlreadyExistsException;
import com.pockt.infrastructure.exception.BankAccountNotFoundException;
import com.pockt.infrastructure.exception.BankInsufficientFundsException;
import com.pockt.infrastructure.exception.ForbiddenException;
import com.pockt.user.service.UserService;
import com.pockt.wallet.domain.Wallet;
import com.pockt.wallet.dto.WalletResponse;
import com.pockt.wallet.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class BankServiceImpl implements BankService {

    private static final Logger log = LoggerFactory.getLogger(BankServiceImpl.class);
    private static final long DEFAULT_SIMULATED_BALANCE = 5000000L; // 50,000.00 in cents

    private final BankAccountRepository bankAccountRepository;
    private final BankTransactionRepository bankTransactionRepository;
    private final WalletService walletService;
    private final UserService userService;

    public BankServiceImpl(
            BankAccountRepository bankAccountRepository,
            BankTransactionRepository bankTransactionRepository,
            WalletService walletService,
            UserService userService
    ) {
        this.bankAccountRepository = bankAccountRepository;
        this.bankTransactionRepository = bankTransactionRepository;
        this.walletService = walletService;
        this.userService = userService;
    }

    @Override
    @Transactional
    public BankAccountResponse linkBankAccount(UUID userId, LinkBankAccountRequest request) {
        if (bankAccountRepository.existsByAccountNumberAndIfsc(userId, request.accountNumber(), request.ifscCode())) {
            throw new BankAccountAlreadyExistsException("This bank account is already linked to your profile");
        }

        List<BankAccount> existing = bankAccountRepository.findByUserId(userId);
        boolean isPrimary = existing.isEmpty();

        BankAccount account = new BankAccount(
                UUID.randomUUID(),
                userId,
                request.bankName(),
                request.accountNumber(),
                request.ifscCode(),
                request.accountHolderName(),
                DEFAULT_SIMULATED_BALANCE,
                isPrimary,
                "ACTIVE",
                Instant.now(),
                Instant.now()
        );

        bankAccountRepository.create(account);
        log.info("Linked new bank account {} for user {}", account.id(), userId);
        return BankAccountResponse.fromDomain(account);
    }

    @Override
    public List<BankAccountResponse> getBankAccounts(UUID userId) {
        return bankAccountRepository.findByUserId(userId).stream()
                .map(BankAccountResponse::fromDomain)
                .toList();
    }

    @Override
    public BankAccountResponse getBankAccount(UUID userId, UUID bankAccountId) {
        BankAccount account = bankAccountRepository.findById(bankAccountId)
                .orElseThrow(() -> new BankAccountNotFoundException(bankAccountId));
        if (!account.userId().equals(userId)) {
            throw new ForbiddenException();
        }
        return BankAccountResponse.fromDomain(account);
    }

    @Override
    @Transactional
    public void setPrimaryAccount(UUID userId, UUID bankAccountId) {
        BankAccount account = bankAccountRepository.findById(bankAccountId)
                .orElseThrow(() -> new BankAccountNotFoundException(bankAccountId));
        if (!account.userId().equals(userId)) {
            throw new ForbiddenException();
        }
        bankAccountRepository.setPrimary(bankAccountId, userId);
    }

    @Override
    @Transactional
    public BankTransactionResponse addMoneyFromBank(UUID userId, AddMoneyFromBankRequest request) {
        userService.verifyPin(userId, request.pin());

        BankAccount bankAccount = bankAccountRepository.findByIdForUpdate(request.bankAccountId())
                .orElseThrow(() -> new BankAccountNotFoundException(request.bankAccountId()));

        if (!bankAccount.userId().equals(userId)) {
            throw new ForbiddenException();
        }

        if (bankAccount.simulatedBalance() < request.amount()) {
            throw new BankInsufficientFundsException("Insufficient funds in bank account. Available: "
                    + bankAccount.simulatedBalance() + " cents");
        }

        Wallet targetWallet = resolveWallet(userId, request.walletId());

        // Deduct from Bank Account
        long newBankBalance = bankAccount.simulatedBalance() - request.amount();
        bankAccountRepository.updateBalance(bankAccount.id(), newBankBalance);

        // Credit to Wallet
        walletService.credit(targetWallet.id(), request.amount());

        String referenceNumber = "BNK-DEP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        BankTransaction tx = new BankTransaction(
                UUID.randomUUID(),
                bankAccount.id(),
                targetWallet.id(),
                "DEPOSIT_TO_WALLET",
                request.amount(),
                targetWallet.currency(),
                "COMPLETED",
                referenceNumber,
                Instant.now()
        );

        bankTransactionRepository.create(tx);
        log.info("Deposited {} from bank account {} to wallet {}, ref={}",
                request.amount(), bankAccount.id(), targetWallet.id(), referenceNumber);

        return BankTransactionResponse.fromDomain(tx);
    }

    @Override
    @Transactional
    public BankTransactionResponse withdrawToBank(UUID userId, WithdrawToBankRequest request) {
        userService.verifyPin(userId, request.pin());

        BankAccount bankAccount = bankAccountRepository.findByIdForUpdate(request.bankAccountId())
                .orElseThrow(() -> new BankAccountNotFoundException(request.bankAccountId()));

        if (!bankAccount.userId().equals(userId)) {
            throw new ForbiddenException();
        }

        Wallet targetWallet = resolveWallet(userId, request.walletId());

        // Debit Wallet (throws InsufficientBalanceException if not enough)
        walletService.debit(targetWallet.id(), request.amount());

        // Credit Bank Account
        long newBankBalance = bankAccount.simulatedBalance() + request.amount();
        bankAccountRepository.updateBalance(bankAccount.id(), newBankBalance);

        String referenceNumber = "BNK-WTH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        BankTransaction tx = new BankTransaction(
                UUID.randomUUID(),
                bankAccount.id(),
                targetWallet.id(),
                "WITHDRAW_TO_BANK",
                request.amount(),
                targetWallet.currency(),
                "COMPLETED",
                referenceNumber,
                Instant.now()
        );

        bankTransactionRepository.create(tx);
        log.info("Withdrew {} from wallet {} to bank account {}, ref={}",
                request.amount(), targetWallet.id(), bankAccount.id(), referenceNumber);

        return BankTransactionResponse.fromDomain(tx);
    }

    @Override
    public List<BankTransactionResponse> getBankTransactions(UUID userId, UUID bankAccountId, int limit) {
        BankAccount bankAccount = bankAccountRepository.findById(bankAccountId)
                .orElseThrow(() -> new BankAccountNotFoundException(bankAccountId));

        if (!bankAccount.userId().equals(userId)) {
            throw new ForbiddenException();
        }

        return bankTransactionRepository.findByBankAccountId(bankAccountId, Math.min(limit, 100)).stream()
                .map(BankTransactionResponse::fromDomain)
                .toList();
    }

    private Wallet resolveWallet(UUID userId, UUID requestedWalletId) {
        if (requestedWalletId != null) {
            Wallet wallet = walletService.getWalletForUpdate(requestedWalletId);
            if (!wallet.userId().equals(userId)) {
                throw new ForbiddenException();
            }
            return wallet;
        }

        List<WalletResponse> userWallets = walletService.getUserWallets(userId);
        if (userWallets.isEmpty()) {
            return walletService.getOrCreateWallet(userId, "USD");
        }
        return walletService.getWalletForUpdate(userWallets.get(0).id());
    }
}

package com.pockt.card.internal;

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
import com.pockt.card.dto.UpdateCardSettingsRequest;
import com.pockt.card.repository.CardRepository;
import com.pockt.card.repository.CreditAccountRepository;
import com.pockt.card.service.CardService;
import com.pockt.infrastructure.exception.CardNotFoundException;
import com.pockt.infrastructure.exception.ForbiddenException;
import com.pockt.infrastructure.util.MoneyUtils;
import com.pockt.user.dto.UserResponse;
import com.pockt.user.service.UserService;
import com.pockt.wallet.dto.WalletResponse;
import com.pockt.wallet.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CardServiceImpl implements CardService {

    private static final Logger log = LoggerFactory.getLogger(CardServiceImpl.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long DEFAULT_CREDIT_LIMIT = 2500000L; // $25,000.00 / 2,500,000 cents

    private final CardRepository cardRepository;
    private final CreditAccountRepository creditAccountRepository;
    private final WalletService walletService;
    private final UserService userService;

    public CardServiceImpl(
            CardRepository cardRepository,
            CreditAccountRepository creditAccountRepository,
            WalletService walletService,
            UserService userService
    ) {
        this.cardRepository = cardRepository;
        this.creditAccountRepository = creditAccountRepository;
        this.walletService = walletService;
        this.userService = userService;
    }

    @Override
    @Transactional
    public CardResponse issueDebitCard(UUID userId, IssueDebitCardRequest request) {
        UserResponse user = userService.getProfile(userId);

        UUID walletId = request.walletId();
        if (walletId == null) {
            walletId = walletService.getOrCreateWallet(userId, "USD").id();
        }

        String network = request.network() != null ? request.network().toUpperCase() : "RUPAY";
        if (!List.of("RUPAY", "VISA", "MASTERCARD").contains(network)) {
            network = "RUPAY";
        }

        String pan = generateCardNumber(network);
        String masked = maskPan(pan);
        String cvv = String.format("%03d", RANDOM.nextInt(900) + 100);

        LocalDate now = LocalDate.now();
        int expiryMonth = now.getMonthValue();
        int expiryYear = now.getYear() + 5;

        Card card = new Card(
                UUID.randomUUID(),
                userId,
                walletId,
                "DEBIT",
                network,
                masked,
                pan,
                expiryMonth,
                expiryYear,
                cvv,
                user.fullName(),
                5000000L, // 50,000.00 daily limit
                true,
                "ACTIVE",
                Instant.now(),
                Instant.now()
        );

        cardRepository.create(card);
        log.info("Issued digital debit card {} for user {}", card.id(), userId);
        return CardResponse.fromDomain(card);
    }

    @Override
    @Transactional
    public CreditAccountResponse applyCreditCard(UUID userId, ApplyCreditCardRequest request) {
        Optional<CreditAccount> existing = creditAccountRepository.findByUserId(userId);
        if (existing.isPresent()) {
            return CreditAccountResponse.fromDomain(existing.get());
        }

        UserResponse user = userService.getProfile(userId);
        String network = request.network() != null ? request.network().toUpperCase() : "RUPAY";
        if (!List.of("RUPAY", "VISA", "MASTERCARD").contains(network)) {
            network = "RUPAY";
        }

        String pan = generateCardNumber(network);
        String masked = maskPan(pan);
        String cvv = String.format("%03d", RANDOM.nextInt(900) + 100);

        LocalDate now = LocalDate.now();
        int expiryMonth = now.getMonthValue();
        int expiryYear = now.getYear() + 5;

        Card card = new Card(
                UUID.randomUUID(),
                userId,
                null,
                "CREDIT",
                network,
                masked,
                pan,
                expiryMonth,
                expiryYear,
                cvv,
                user.fullName(),
                DEFAULT_CREDIT_LIMIT,
                true,
                "ACTIVE",
                Instant.now(),
                Instant.now()
        );
        cardRepository.create(card);

        CreditAccount creditAccount = new CreditAccount(
                UUID.randomUUID(),
                userId,
                card.id(),
                DEFAULT_CREDIT_LIMIT,
                DEFAULT_CREDIT_LIMIT,
                0L,
                "ACTIVE",
                Instant.now(),
                Instant.now()
        );
        creditAccountRepository.create(creditAccount);

        log.info("Provisioned credit card {} and credit account {} for user {}", card.id(), creditAccount.id(), userId);
        return CreditAccountResponse.fromDomain(creditAccount);
    }

    @Override
    public List<CardResponse> getUserCards(UUID userId) {
        return cardRepository.findByUserId(userId).stream()
                .map(CardResponse::fromDomain)
                .toList();
    }

    @Override
    public CardResponse getCard(UUID userId, UUID cardId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new CardNotFoundException(cardId));
        if (!card.userId().equals(userId)) {
            throw new ForbiddenException();
        }
        return CardResponse.fromDomain(card);
    }

    @Override
    public CardRevealResponse revealCard(UUID userId, UUID cardId, RevealCardRequest request) {
        userService.verifyPin(userId, request.pin());

        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new CardNotFoundException(cardId));
        if (!card.userId().equals(userId)) {
            throw new ForbiddenException();
        }

        String formattedExpiry = String.format("%02d/%d", card.expiryMonth(), card.expiryYear() % 100);
        return new CardRevealResponse(
                card.id(),
                formatPanWithSpaces(card.cardNumberFull()),
                formattedExpiry,
                card.cvvPlain(),
                card.cardHolderName(),
                card.cardType(),
                card.cardNetwork()
        );
    }

    @Override
    @Transactional
    public CardResponse updateSettings(UUID userId, UUID cardId, UpdateCardSettingsRequest request) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new CardNotFoundException(cardId));
        if (!card.userId().equals(userId)) {
            throw new ForbiddenException();
        }

        boolean onlineEnabled = request.onlineEnabled() != null ? request.onlineEnabled() : card.onlineEnabled();
        long dailyLimit = request.dailyLimitCents() != null ? request.dailyLimitCents() : card.dailyLimitCents();

        cardRepository.updateSettings(cardId, onlineEnabled, dailyLimit);
        return getCard(userId, cardId);
    }

    @Override
    @Transactional
    public CardResponse toggleFreeze(UUID userId, UUID cardId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new CardNotFoundException(cardId));
        if (!card.userId().equals(userId)) {
            throw new ForbiddenException();
        }

        String newStatus = "ACTIVE".equalsIgnoreCase(card.status()) ? "FROZEN" : "ACTIVE";
        cardRepository.updateStatus(cardId, newStatus);
        return getCard(userId, cardId);
    }

    @Override
    @Transactional
    public CardChargeResponse simulateCardCharge(UUID cardId, CardChargeRequest request) {
        Card card = cardRepository.findByIdForUpdate(cardId)
                .orElseThrow(() -> new CardNotFoundException(cardId));

        if (!"ACTIVE".equalsIgnoreCase(card.status())) {
            throw new com.pockt.infrastructure.exception.CardInactiveException("Card is not active (status: " + card.status() + ")");
        }

        if (!card.onlineEnabled()) {
            throw new com.pockt.infrastructure.exception.CardInactiveException("Online card payments are disabled on this card");
        }

        if (request.amount() > card.dailyLimitCents()) {
            throw new com.pockt.infrastructure.exception.ValidationException("Charge exceeds daily card limit");
        }

        if ("DEBIT".equalsIgnoreCase(card.cardType())) {
            UUID walletId = card.walletId();
            if (walletId == null) {
                walletId = walletService.getOrCreateWallet(card.userId(), "USD").id();
            }
            walletService.debit(walletId, request.amount());
        } else if ("CREDIT".equalsIgnoreCase(card.cardType())) {
            CreditAccount creditAcc = creditAccountRepository.findByUserIdForUpdate(card.userId())
                    .orElseThrow(() -> new com.pockt.infrastructure.exception.CreditLimitExceededException("Credit account not found"));

            if (creditAcc.availableCreditLimit() < request.amount()) {
                throw new com.pockt.infrastructure.exception.CreditLimitExceededException("Available credit limit exceeded");
            }

            long newAvail = creditAcc.availableCreditLimit() - request.amount();
            long newBill = creditAcc.currentBillAmount() + request.amount();
            creditAccountRepository.updateBalances(creditAcc.id(), newAvail, newBill);
        }

        UUID txId = UUID.randomUUID();
        log.info("Simulated card charge of {} on card {} at merchant {}", request.amount(), card.id(), request.merchantName());

        return new CardChargeResponse(
                txId,
                card.id(),
                card.cardType(),
                card.cardNumberMasked(),
                request.amount(),
                MoneyUtils.format(request.amount(), "USD"),
                request.merchantName(),
                "APPROVED",
                "Payment authorized successfully",
                Instant.now()
        );
    }

    @Override
    public CreditAccountResponse getCreditAccount(UUID userId) {
        CreditAccount account = creditAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new com.pockt.infrastructure.exception.ValidationException("No credit account found for this user"));
        return CreditAccountResponse.fromDomain(account);
    }

    @Override
    @Transactional
    public CreditAccountResponse repayCredit(UUID userId, RepayCreditRequest request) {
        userService.verifyPin(userId, request.pin());

        CreditAccount creditAcc = creditAccountRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new com.pockt.infrastructure.exception.ValidationException("No credit account found"));

        if (creditAcc.currentBillAmount() <= 0) {
            throw new com.pockt.infrastructure.exception.ValidationException("No outstanding credit bill to repay");
        }

        long repayAmount = Math.min(request.amount(), creditAcc.currentBillAmount());

        UUID walletId = request.walletId();
        if (walletId == null) {
            List<WalletResponse> wallets = walletService.getUserWallets(userId);
            walletId = wallets.isEmpty() ? walletService.createWallet(userId, "USD").id() : wallets.get(0).id();
        }

        // Debit wallet to repay credit
        walletService.debit(walletId, repayAmount);

        long newBill = creditAcc.currentBillAmount() - repayAmount;
        long newAvail = Math.min(creditAcc.totalCreditLimit(), creditAcc.availableCreditLimit() + repayAmount);
        creditAccountRepository.updateBalances(creditAcc.id(), newAvail, newBill);

        log.info("Repaid credit bill of {} for user {}", repayAmount, userId);
        return getCreditAccount(userId);
    }

    private String generateCardNumber(String network) {
        String prefix;
        switch (network) {
            case "VISA" -> prefix = "4111";
            case "MASTERCARD" -> prefix = "5200";
            default -> prefix = "6521"; // RuPay
        }

        StringBuilder sb = new StringBuilder(prefix);
        for (int i = 0; i < 11; i++) {
            sb.append(RANDOM.nextInt(10));
        }

        int checkDigit = computeLuhnCheckDigit(sb.toString());
        sb.append(checkDigit);
        return sb.toString();
    }

    private int computeLuhnCheckDigit(String partialNumber) {
        int sum = 0;
        boolean alternate = true;
        for (int i = partialNumber.length() - 1; i >= 0; i--) {
            int n = Integer.parseInt(partialNumber.substring(i, i + 1));
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n = (n % 10) + 1;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return (10 - (sum % 10)) % 10;
    }

    private String maskPan(String pan) {
        if (pan.length() >= 4) {
            return "•••• •••• •••• " + pan.substring(pan.length() - 4);
        }
        return pan;
    }

    private String formatPanWithSpaces(String pan) {
        if (pan == null || pan.length() != 16) return pan;
        return pan.substring(0, 4) + " " + pan.substring(4, 8) + " " + pan.substring(8, 12) + " " + pan.substring(12, 16);
    }
}

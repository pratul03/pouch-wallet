package com.pockt.wallet.internal;

import com.pockt.infrastructure.exception.ForbiddenException;
import com.pockt.infrastructure.exception.InvalidAmountException;
import com.pockt.infrastructure.exception.WalletNotFoundException;
import com.pockt.user.event.UserRegisteredEvent;
import com.pockt.wallet.domain.Wallet;
import com.pockt.wallet.dto.WalletResponse;
import com.pockt.wallet.repository.WalletRepository;
import com.pockt.wallet.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class WalletServiceImpl implements WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletServiceImpl.class);

    private final WalletRepository walletRepository;

    public WalletServiceImpl(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    @Override
    @Transactional
    public WalletResponse createWallet(UUID userId, String currency) {
        String cleanCurrency = currency != null ? currency.toUpperCase().trim() : "USD";
        Optional<Wallet> existing = walletRepository.findByUserIdAndCurrency(userId, cleanCurrency);
        if (existing.isPresent()) {
            return WalletResponse.fromDomain(existing.get());
        }

        Instant now = Instant.now();
        Wallet wallet = new Wallet(
                UUID.randomUUID(),
                userId,
                cleanCurrency,
                0L,
                true,
                now,
                now
        );
        walletRepository.create(wallet);
        log.info("Created wallet {} for user {} in currency {}", wallet.id(), userId, cleanCurrency);
        return WalletResponse.fromDomain(wallet);
    }

    @EventListener
    public void onUserRegistered(UserRegisteredEvent event) {
        log.info("Received UserRegisteredEvent for user {}, initializing default USD wallet", event.userId());
        createWallet(event.userId(), "USD");
    }

    @Override
    public WalletResponse getWallet(UUID walletId, UUID requestingUserId) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));

        if (!wallet.userId().equals(requestingUserId)) {
            throw new ForbiddenException("You are not authorized to view this wallet.");
        }

        return WalletResponse.fromDomain(wallet);
    }

    @Override
    public List<WalletResponse> getUserWallets(UUID userId) {
        return walletRepository.findByUserId(userId).stream()
                .map(WalletResponse::fromDomain)
                .toList();
    }

    @Override
    @Transactional
    public WalletResponse topUp(UUID walletId, long amountCents, UUID requestingUserId) {
        if (amountCents <= 0) {
            throw new InvalidAmountException("Top-up amount must be greater than zero.");
        }

        Wallet wallet = walletRepository.findByIdForUpdate(walletId);
        if (!wallet.userId().equals(requestingUserId)) {
            throw new ForbiddenException("You are not authorized to top up this wallet.");
        }

        walletRepository.credit(walletId, amountCents);
        Wallet updatedWallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));

        log.info("Top-up completed for wallet {}: +{} cents. New balance: {}",
                walletId, amountCents, updatedWallet.balance());

        return WalletResponse.fromDomain(updatedWallet);
    }

    @Override
    public Wallet getWalletForUpdate(UUID walletId) {
        return walletRepository.findByIdForUpdate(walletId);
    }

    @Override
    public void debit(UUID walletId, long amountCents) {
        walletRepository.debit(walletId, amountCents);
    }

    @Override
    public void credit(UUID walletId, long amountCents) {
        walletRepository.credit(walletId, amountCents);
    }

    @Override
    @Transactional
    public Wallet getOrCreateWallet(UUID userId, String currency) {
        String cleanCurrency = currency != null ? currency.toUpperCase().trim() : "USD";
        return walletRepository.findByUserIdAndCurrency(userId, cleanCurrency)
                .orElseGet(() -> {
                    Instant now = Instant.now();
                    Wallet newWallet = new Wallet(
                            UUID.randomUUID(),
                            userId,
                            cleanCurrency,
                            0L,
                            true,
                            now,
                            now
                    );
                    walletRepository.create(newWallet);
                    return newWallet;
                });
    }
}

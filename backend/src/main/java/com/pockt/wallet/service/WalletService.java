package com.pockt.wallet.service;

import com.pockt.wallet.domain.Wallet;
import com.pockt.wallet.dto.WalletResponse;

import java.util.List;
import java.util.UUID;

public interface WalletService {
    WalletResponse createWallet(UUID userId, String currency);
    WalletResponse getWallet(UUID walletId, UUID requestingUserId);
    List<WalletResponse> getUserWallets(UUID userId);
    WalletResponse topUp(UUID walletId, long amountCents, UUID requestingUserId);
    Wallet getWalletForUpdate(UUID walletId);
    void debit(UUID walletId, long amountCents);
    void credit(UUID walletId, long amountCents);
    Wallet getOrCreateWallet(UUID userId, String currency);
}

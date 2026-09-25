package com.pockt.infrastructure.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class WalletInactiveException extends PocktException {
    public WalletInactiveException(UUID walletId) {
        super(ErrorCode.WALLET_INACTIVE, HttpStatus.UNPROCESSABLE_ENTITY, "Wallet is disabled: " + walletId);
    }
}

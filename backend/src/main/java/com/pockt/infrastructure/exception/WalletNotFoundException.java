package com.pockt.infrastructure.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class WalletNotFoundException extends PocktException {
    public WalletNotFoundException(UUID walletId) {
        super(ErrorCode.WALLET_NOT_FOUND, HttpStatus.NOT_FOUND, "Wallet not found: " + walletId);
    }

    public WalletNotFoundException(String message) {
        super(ErrorCode.WALLET_NOT_FOUND, HttpStatus.NOT_FOUND, message);
    }
}

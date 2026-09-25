package com.pockt.infrastructure.exception;

import org.springframework.http.HttpStatus;

public class UpiHandleNotFoundException extends PocktException {
    public UpiHandleNotFoundException(String vpa) {
        super(ErrorCode.UPI_HANDLE_NOT_FOUND, HttpStatus.NOT_FOUND, "UPI ID not found: " + vpa);
    }
}

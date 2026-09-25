package com.pockt.infrastructure.exception;

import org.springframework.http.HttpStatus;

public class UpiHandleAlreadyExistsException extends PocktException {
    public UpiHandleAlreadyExistsException(String vpa) {
        super(ErrorCode.UPI_HANDLE_EXISTS, HttpStatus.CONFLICT, "UPI handle '" + vpa + "' is already taken");
    }
}

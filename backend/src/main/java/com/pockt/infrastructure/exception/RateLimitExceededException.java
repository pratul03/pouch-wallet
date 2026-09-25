package com.pockt.infrastructure.exception;

import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends PocktException {
    public RateLimitExceededException() {
        super(ErrorCode.RATE_LIMIT_EXCEEDED, HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded. Please try again later.");
    }

    public RateLimitExceededException(String message) {
        super(ErrorCode.RATE_LIMIT_EXCEEDED, HttpStatus.TOO_MANY_REQUESTS, message);
    }
}

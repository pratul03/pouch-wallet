package com.pockt.infrastructure.web;

import com.pockt.infrastructure.exception.ErrorCode;
import com.pockt.infrastructure.exception.PocktException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(PocktException.class)
    public ResponseEntity<ApiResponse<Void>> handlePocktException(PocktException ex) {
        log.warn("Domain exception: code={}, status={}, message={}", ex.getCode(), ex.getStatus(), ex.getMessage());
        ApiError error = ApiError.of(ex.getCode(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.error(error));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        FieldError firstError = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String field = firstError != null ? firstError.getField() : null;
        String message = firstError != null ? firstError.getDefaultMessage() : "Invalid input parameters.";

        log.warn("Validation failure on field={}: {}", field, message);
        ApiError error = ApiError.of(ErrorCode.VALIDATION_ERROR, message, field);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException ex) {
        log.warn("Missing required header: {}", ex.getHeaderName());
        ApiError error = ApiError.of(ErrorCode.VALIDATION_ERROR, "Missing required header: " + ex.getHeaderName(), ex.getHeaderName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException ex) {
        log.warn("Authentication failure: {}", ex.getMessage());
        ApiError error = ApiError.of(ErrorCode.BAD_CREDENTIALS, "Authentication failed.");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(error));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        ApiError error = ApiError.of(ErrorCode.FORBIDDEN, "Access to this resource is denied.");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(error));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex) {
        log.error("Unhandled internal server error", ex);
        ApiError error = ApiError.of(ErrorCode.INTERNAL_ERROR, "An unexpected server error occurred.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(error));
    }
}

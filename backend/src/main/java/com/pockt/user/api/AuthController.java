package com.pockt.user.api;

import com.pockt.infrastructure.exception.InvalidTokenException;
import com.pockt.infrastructure.web.ApiResponse;
import com.pockt.user.dto.LoginRequest;
import com.pockt.user.dto.OtpLoginRequest;
import com.pockt.user.dto.OtpSendRequest;
import com.pockt.user.dto.OtpVerifyRequest;
import com.pockt.user.dto.RefreshTokenRequest;
import com.pockt.user.dto.RegisterRequest;
import com.pockt.user.dto.ResetPinRequest;
import com.pockt.user.dto.TokenResponse;
import com.pockt.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Authentication, registration, and PIN management endpoints")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/otp/send")
    @Operation(summary = "Send OTP to phone number")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendOtp(@Valid @RequestBody OtpSendRequest request) {
        userService.sendOtp(request.phone(), request.purpose());
        return ResponseEntity.ok(ApiResponse.success(Map.of("expiresInSeconds", 300)));
    }

    @PostMapping("/otp/verify")
    @Operation(summary = "Verify OTP and obtain temporary registration/reset token")
    public ResponseEntity<ApiResponse<Map<String, String>>> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        String tempToken = userService.verifyOtp(request.phone(), request.otp(), request.purpose());
        return ResponseEntity.ok(ApiResponse.success(Map.of("tempToken", tempToken)));
    }

    @PostMapping("/register")
    @Operation(summary = "Complete registration with full name and 6-digit PIN")
    public ResponseEntity<ApiResponse<TokenResponse>> register(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody RegisterRequest request) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new InvalidTokenException("Missing Bearer tempToken in Authorization header");
        }
        String tempToken = authHeader.substring(7).trim();
        TokenResponse response = userService.register(request, tempToken);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/login")
    @Operation(summary = "Login with phone and PIN")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse response = userService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/login/otp")
    @Operation(summary = "Passwordless login with phone and OTP")
    public ResponseEntity<ApiResponse<TokenResponse>> loginWithOtp(@Valid @RequestBody OtpLoginRequest request) {
        TokenResponse response = userService.loginWithOtp(request.phone(), request.otp());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/pin/reset")
    @Operation(summary = "Reset 6-digit PIN using verified OTP temporary token")
    public ResponseEntity<ApiResponse<Map<String, String>>> resetPin(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody ResetPinRequest request) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new InvalidTokenException("Missing Bearer tempToken in Authorization header");
        }
        String tempToken = authHeader.substring(7).trim();
        userService.resetPin(tempToken, request.newPin());
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "PIN reset successfully. Please login with your new PIN.")));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token to obtain a new token pair")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        TokenResponse response = userService.refreshToken(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Invalidate refresh token and logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        userService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}

package com.pockt.user.service;

import com.pockt.user.domain.OtpPurpose;
import com.pockt.user.dto.LoginRequest;
import com.pockt.user.dto.RegisterRequest;
import com.pockt.user.dto.TokenResponse;
import com.pockt.user.dto.UserResponse;

import java.util.Optional;
import java.util.UUID;

public interface UserService {
    void sendOtp(String phone, OtpPurpose purpose);
    String verifyOtp(String phone, String otp, OtpPurpose purpose);
    TokenResponse register(RegisterRequest request, String tempToken);
    TokenResponse login(LoginRequest request);
    TokenResponse refreshToken(String refreshToken);
    void logout(String refreshToken);
    void registerFcmToken(UUID userId, String fcmToken);
    UserResponse getProfile(UUID userId);
    UserResponse updateProfile(UUID userId, String fullName);
    Optional<UserResponse> findByPhone(String phone);
}

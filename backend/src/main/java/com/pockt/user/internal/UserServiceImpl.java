package com.pockt.user.internal;

import com.pockt.infrastructure.exception.BadCredentialsException;
import com.pockt.infrastructure.exception.InvalidTokenException;
import com.pockt.infrastructure.exception.PhoneAlreadyRegisteredException;
import com.pockt.infrastructure.exception.UserNotFoundException;
import com.pockt.infrastructure.ratelimit.RateLimitService;
import com.pockt.infrastructure.security.JwtService;
import com.pockt.user.domain.OtpPurpose;
import com.pockt.user.domain.User;
import com.pockt.user.dto.LoginRequest;
import com.pockt.user.dto.RegisterRequest;
import com.pockt.user.dto.TokenResponse;
import com.pockt.user.dto.UserResponse;
import com.pockt.user.event.UserRegisteredEvent;
import com.pockt.user.repository.UserRepository;
import com.pockt.user.service.OtpService;
import com.pockt.user.service.UserService;
import io.jsonwebtoken.Claims;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final OtpService otpService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final RateLimitService rateLimitService;
    private final ApplicationEventPublisher eventPublisher;

    public UserServiceImpl(UserRepository userRepository,
                           OtpService otpService,
                           JwtService jwtService,
                           PasswordEncoder passwordEncoder,
                           RateLimitService rateLimitService,
                           ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.otpService = otpService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.rateLimitService = rateLimitService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void sendOtp(String phone, OtpPurpose purpose) {
        if (purpose == OtpPurpose.REGISTRATION && userRepository.existsByPhone(phone)) {
            throw new PhoneAlreadyRegisteredException(phone);
        }
        if ((purpose == OtpPurpose.LOGIN || purpose == OtpPurpose.PIN_RESET) && !userRepository.existsByPhone(phone)) {
            throw new UserNotFoundException("No account registered with phone: " + phone);
        }
        otpService.send(phone, purpose);
    }

    @Override
    public String verifyOtp(String phone, String otp, OtpPurpose purpose) {
        otpService.verify(phone, otp, purpose);
        return jwtService.generateTempToken(phone);
    }

    @Override
    @Transactional
    public TokenResponse register(RegisterRequest request, String tempToken) {
        Claims claims = jwtService.parseAndValidateTempToken(tempToken);
        String phone = claims.get("phone", String.class);
        if (phone == null || phone.isBlank()) {
            throw new InvalidTokenException("Temp token missing phone claim");
        }

        if (userRepository.existsByPhone(phone)) {
            throw new PhoneAlreadyRegisteredException(phone);
        }

        Instant now = Instant.now();
        UUID userId = UUID.randomUUID();
        String pinHash = passwordEncoder.encode(request.pin());

        User user = new User(
                userId,
                phone,
                request.fullName().trim(),
                pinHash,
                null,
                "PENDING",
                "USER",
                true,
                now,
                now
        );

        userRepository.create(user);

        // Publish event to trigger default wallet creation & default UPI handle creation
        eventPublisher.publishEvent(new UserRegisteredEvent(userId, phone));

        String accessToken = jwtService.generateAccessToken(userId, phone, user.role());
        String refreshToken = jwtService.generateRefreshToken(userId);

        return new TokenResponse(userId, accessToken, refreshToken, jwtService.getAccessTokenTtlSeconds());
    }

    @Override
    public TokenResponse login(LoginRequest request) {
        rateLimitService.checkLoginRateLimit(request.phone());

        User user = userRepository.findByPhone(request.phone())
                .orElseThrow(BadCredentialsException::new);

        if (!passwordEncoder.matches(request.pin(), user.pinHash())) {
            throw new BadCredentialsException();
        }

        String accessToken = jwtService.generateAccessToken(user.id(), user.phone(), user.role());
        String refreshToken = jwtService.generateRefreshToken(user.id());

        return new TokenResponse(user.id(), accessToken, refreshToken, jwtService.getAccessTokenTtlSeconds());
    }

    @Override
    @Transactional
    public TokenResponse loginWithOtp(String phone, String otp) {
        rateLimitService.checkLoginRateLimit(phone);

        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new UserNotFoundException("No account registered with phone: " + phone));

        otpService.verify(phone, otp, OtpPurpose.LOGIN);

        String accessToken = jwtService.generateAccessToken(user.id(), user.phone(), user.role());
        String refreshToken = jwtService.generateRefreshToken(user.id());

        return new TokenResponse(user.id(), accessToken, refreshToken, jwtService.getAccessTokenTtlSeconds());
    }

    @Override
    public TokenResponse refreshToken(String refreshToken) {
        UUID userId = jwtService.validateRefreshToken(refreshToken)
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired refresh token"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        jwtService.invalidateRefreshToken(refreshToken);

        String newAccessToken = jwtService.generateAccessToken(user.id(), user.phone(), user.role());
        String newRefreshToken = jwtService.generateRefreshToken(user.id());

        return new TokenResponse(user.id(), newAccessToken, newRefreshToken, jwtService.getAccessTokenTtlSeconds());
    }

    @Override
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            jwtService.invalidateRefreshToken(refreshToken);
        }
    }

    @Override
    @Transactional
    public void resetPin(String tempToken, String newPin) {
        Claims claims = jwtService.parseAndValidateTempToken(tempToken);
        String phone = claims.get("phone", String.class);
        if (phone == null || phone.isBlank()) {
            throw new InvalidTokenException("Temp token missing phone claim");
        }

        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new UserNotFoundException("No user found with phone: " + phone));

        String newPinHash = passwordEncoder.encode(newPin);
        userRepository.updatePinHash(user.id(), newPinHash);
    }

    @Override
    @Transactional
    public void changePin(UUID userId, String oldPin, String newPin) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!passwordEncoder.matches(oldPin, user.pinHash())) {
            throw new BadCredentialsException();
        }

        String newPinHash = passwordEncoder.encode(newPin);
        userRepository.updatePinHash(userId, newPinHash);
    }

    @Override
    public void verifyPin(UUID userId, String pin) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        if (!passwordEncoder.matches(pin, user.pinHash())) {
            throw new BadCredentialsException();
        }
    }

    @Override
    @Transactional
    public void registerFcmToken(UUID userId, String fcmToken) {
        userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        userRepository.updateFcmToken(userId, fcmToken);
    }

    @Override
    public UserResponse getProfile(UUID userId) {
        return userRepository.findById(userId)
                .map(UserResponse::fromDomain)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    @Override
    @Transactional
    public UserResponse updateProfile(UUID userId, String fullName) {
        userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        userRepository.updateFullName(userId, fullName.trim());
        return getProfile(userId);
    }

    @Override
    @Transactional
    public UserResponse updateStatus(UUID userId, boolean isActive) {
        userRepository.findByIdAdmin(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        userRepository.updateActiveStatus(userId, isActive);
        return userRepository.findByIdAdmin(userId).map(UserResponse::fromDomain).orElseThrow();
    }

    @Override
    public Optional<UserResponse> findByPhone(String phone) {
        return userRepository.findByPhone(phone).map(UserResponse::fromDomain);
    }

    @Override
    public List<UserResponse> listUsers(String search, String kycStatus, Boolean isActive, int limit, int offset) {
        return userRepository.findAllUsers(search, kycStatus, isActive, limit, offset).stream()
                .map(UserResponse::fromDomain)
                .toList();
    }

    @Override
    public long countUsers() {
        return userRepository.countAllUsers();
    }
}

package com.pockt.user;

import com.pockt.infrastructure.exception.BadCredentialsException;
import com.pockt.infrastructure.exception.PhoneAlreadyRegisteredException;
import com.pockt.infrastructure.ratelimit.RateLimitService;
import com.pockt.infrastructure.security.JwtService;
import com.pockt.user.domain.User;
import com.pockt.user.dto.LoginRequest;
import com.pockt.user.dto.RegisterRequest;
import com.pockt.user.dto.TokenResponse;
import com.pockt.user.event.UserRegisteredEvent;
import com.pockt.user.internal.UserServiceImpl;
import com.pockt.user.repository.UserRepository;
import com.pockt.user.service.OtpService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OtpService otpService;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void register_shouldCreateUserAndPublishEvent() {
        Claims claims = mock(Claims.class);
        when(claims.get("phone", String.class)).thenReturn("+2348012345678");
        when(jwtService.parseAndValidateTempToken("valid-temp-token")).thenReturn(claims);
        when(userRepository.existsByPhone("+2348012345678")).thenReturn(false);
        when(passwordEncoder.encode("123456")).thenReturn("hashed-pin");
        when(jwtService.generateAccessToken(any(), any())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);

        RegisterRequest request = new RegisterRequest("Ade Bello", "123456");
        TokenResponse response = userService.register(request, "valid-temp-token");

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        verify(userRepository).create(any(User.class));
        verify(eventPublisher).publishEvent(any(UserRegisteredEvent.class));
    }

    @Test
    void register_shouldThrowWhenPhoneAlreadyExists() {
        Claims claims = mock(Claims.class);
        when(claims.get("phone", String.class)).thenReturn("+2348012345678");
        when(jwtService.parseAndValidateTempToken("valid-temp-token")).thenReturn(claims);
        when(userRepository.existsByPhone("+2348012345678")).thenReturn(true);

        RegisterRequest request = new RegisterRequest("Ade Bello", "123456");

        assertThatThrownBy(() -> userService.register(request, "valid-temp-token"))
                .isInstanceOf(PhoneAlreadyRegisteredException.class);
    }

    @Test
    void login_shouldSucceedWithCorrectCredentials() {
        User user = new User(
                UUID.randomUUID(),
                "+2348012345678",
                "Ade Bello",
                "hashed-pin",
                null,
                "VERIFIED",
                true,
                Instant.now(),
                Instant.now()
        );

        when(userRepository.findByPhone("+2348012345678")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("123456", "hashed-pin")).thenReturn(true);
        when(jwtService.generateAccessToken(user.id(), user.phone())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(user.id())).thenReturn("refresh-token");
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);

        LoginRequest request = new LoginRequest("+2348012345678", "123456");
        TokenResponse response = userService.login(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
    }

    @Test
    void login_shouldThrowWhenPinWrong() {
        User user = new User(
                UUID.randomUUID(),
                "+2348012345678",
                "Ade Bello",
                "hashed-pin",
                null,
                "VERIFIED",
                true,
                Instant.now(),
                Instant.now()
        );

        when(userRepository.findByPhone("+2348012345678")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("000000", "hashed-pin")).thenReturn(false);

        LoginRequest request = new LoginRequest("+2348012345678", "000000");

        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }
}

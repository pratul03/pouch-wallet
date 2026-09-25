package com.pockt.user;

import com.pockt.infrastructure.exception.InvalidOtpException;
import com.pockt.infrastructure.exception.OtpExpiredException;
import com.pockt.infrastructure.ratelimit.RateLimitService;
import com.pockt.user.domain.OtpPurpose;
import com.pockt.user.domain.OtpVerification;
import com.pockt.user.internal.OtpServiceImpl;
import com.pockt.user.repository.OtpRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock
    private OtpRepository otpRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private OtpServiceImpl otpService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(otpService, "otpTtlSeconds", 300L);
        ReflectionTestUtils.setField(otpService, "maxAttempts", 3);
        ReflectionTestUtils.setField(otpService, "lockoutSeconds", 600L);
    }

    @Test
    void send_shouldSaveHashedOtp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(any())).thenReturn(null);
        when(passwordEncoder.encode(any())).thenReturn("hashed-otp");

        otpService.send("+2348012345678", OtpPurpose.REGISTRATION);

        verify(otpRepository).save(any(OtpVerification.class));
    }

    @Test
    void verify_shouldSucceedWhenOtpMatches() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(any())).thenReturn("0");

        UUID id = UUID.randomUUID();
        OtpVerification verification = new OtpVerification(
                id,
                "+2348012345678",
                "hashed-otp",
                OtpPurpose.REGISTRATION,
                Instant.now().plusSeconds(300),
                false,
                Instant.now()
        );

        when(otpRepository.findLatestUnused("+2348012345678", OtpPurpose.REGISTRATION))
                .thenReturn(Optional.of(verification));
        when(passwordEncoder.matches("123456", "hashed-otp")).thenReturn(true);

        boolean result = otpService.verify("+2348012345678", "123456", OtpPurpose.REGISTRATION);

        assertThat(result).isTrue();
        verify(otpRepository).markUsed(id);
    }

    @Test
    void verify_shouldThrowWhenExpired() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(any())).thenReturn("0");

        OtpVerification expired = new OtpVerification(
                UUID.randomUUID(),
                "+2348012345678",
                "hashed-otp",
                OtpPurpose.REGISTRATION,
                Instant.now().minusSeconds(10),
                false,
                Instant.now().minusSeconds(310)
        );

        when(otpRepository.findLatestUnused("+2348012345678", OtpPurpose.REGISTRATION))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> otpService.verify("+2348012345678", "123456", OtpPurpose.REGISTRATION))
                .isInstanceOf(OtpExpiredException.class);
    }

    @Test
    void verify_shouldThrowWhenOtpDoesNotMatch() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(any())).thenReturn("0");

        OtpVerification verification = new OtpVerification(
                UUID.randomUUID(),
                "+2348012345678",
                "hashed-otp",
                OtpPurpose.REGISTRATION,
                Instant.now().plusSeconds(300),
                false,
                Instant.now()
        );

        when(otpRepository.findLatestUnused("+2348012345678", OtpPurpose.REGISTRATION))
                .thenReturn(Optional.of(verification));
        when(passwordEncoder.matches("999999", "hashed-otp")).thenReturn(false);

        assertThatThrownBy(() -> otpService.verify("+2348012345678", "999999", OtpPurpose.REGISTRATION))
                .isInstanceOf(InvalidOtpException.class);
    }
}

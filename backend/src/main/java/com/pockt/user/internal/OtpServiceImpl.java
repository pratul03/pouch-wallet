package com.pockt.user.internal;

import com.pockt.infrastructure.exception.InvalidOtpException;
import com.pockt.infrastructure.exception.OtpExpiredException;
import com.pockt.infrastructure.exception.OtpMaxAttemptsException;
import com.pockt.infrastructure.ratelimit.RateLimitService;
import com.pockt.user.domain.OtpPurpose;
import com.pockt.user.domain.OtpVerification;
import com.pockt.user.repository.OtpRepository;
import com.pockt.user.service.OtpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class OtpServiceImpl implements OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpServiceImpl.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final RateLimitService rateLimitService;
    private final StringRedisTemplate redisTemplate;

    @Value("${pockt.otp.ttl-seconds:300}")
    private long otpTtlSeconds;

    @Value("${pockt.otp.max-attempts:3}")
    private int maxAttempts;

    @Value("${pockt.otp.lockout-seconds:600}")
    private long lockoutSeconds;

    public OtpServiceImpl(OtpRepository otpRepository,
                          PasswordEncoder passwordEncoder,
                          RateLimitService rateLimitService,
                          StringRedisTemplate redisTemplate) {
        this.otpRepository = otpRepository;
        this.passwordEncoder = passwordEncoder;
        this.rateLimitService = rateLimitService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    @Transactional
    public void send(String phone, OtpPurpose purpose) {
        rateLimitService.checkOtpSendRateLimit(phone);

        String attemptsKey = "otp_attempts:" + phone + ":" + purpose.name();
        String currentAttemptsStr = redisTemplate.opsForValue().get(attemptsKey);
        if (currentAttemptsStr != null && Integer.parseInt(currentAttemptsStr) >= maxAttempts) {
            throw new OtpMaxAttemptsException();
        }

        String plainOtp = String.format("%06d", RANDOM.nextInt(1_000_000));
        String hashedOtp = passwordEncoder.encode(plainOtp);
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(otpTtlSeconds);

        OtpVerification verification = new OtpVerification(
                UUID.randomUUID(),
                phone,
                hashedOtp,
                purpose,
                expiresAt,
                false,
                now
        );

        otpRepository.save(verification);

        // In dev and local mode, log OTP clearly for testing convenience
        log.info("==================================================");
        log.info("OTP DISPATCH [{}]: phone={}, code={}", purpose, phone, plainOtp);
        log.info("==================================================");
    }

    @Override
    @Transactional
    public boolean verify(String phone, String otp, OtpPurpose purpose) {
        String attemptsKey = "otp_attempts:" + phone + ":" + purpose.name();
        String currentAttemptsStr = redisTemplate.opsForValue().get(attemptsKey);
        int currentAttempts = currentAttemptsStr != null ? Integer.parseInt(currentAttemptsStr) : 0;
        if (currentAttempts >= maxAttempts) {
            throw new OtpMaxAttemptsException();
        }

        Optional<OtpVerification> opt = otpRepository.findLatestUnused(phone, purpose);
        if (opt.isEmpty()) {
            throw new InvalidOtpException();
        }

        OtpVerification verification = opt.get();
        if (Instant.now().isAfter(verification.expiresAt())) {
            throw new OtpExpiredException();
        }

        if (!passwordEncoder.matches(otp, verification.otpHash())) {
            Long newAttempts = redisTemplate.opsForValue().increment(attemptsKey);
            if (newAttempts != null && newAttempts == 1) {
                redisTemplate.expire(attemptsKey, Duration.ofSeconds(lockoutSeconds));
            }
            if (newAttempts != null && newAttempts >= maxAttempts) {
                throw new OtpMaxAttemptsException();
            }
            throw new InvalidOtpException();
        }

        otpRepository.markUsed(verification.id());
        redisTemplate.delete(attemptsKey);
        return true;
    }
}

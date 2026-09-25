package com.pockt.infrastructure.ratelimit;

import com.pockt.infrastructure.exception.RateLimitExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;

    @Value("${pockt.rate-limit.transfer-per-minute:10}")
    private int transferLimitPerMinute;

    @Value("${pockt.rate-limit.login-per-minute:5}")
    private int loginLimitPerMinute;

    @Value("${pockt.rate-limit.otp-per-10-minutes:3}")
    private int otpLimitPerTenMinutes;

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void checkTransferRateLimit(String userId) {
        checkRateLimit("rate:transfer:" + userId, transferLimitPerMinute, Duration.ofMinutes(1),
                "Transfer rate limit exceeded (maximum " + transferLimitPerMinute + " per minute).");
    }

    public void checkLoginRateLimit(String phone) {
        checkRateLimit("rate:login:" + phone, loginLimitPerMinute, Duration.ofMinutes(1),
                "Too many login attempts. Please wait before trying again.");
    }

    public void checkOtpSendRateLimit(String phone) {
        checkRateLimit("rate:otp:" + phone, otpLimitPerTenMinutes, Duration.ofMinutes(10),
                "Too many OTP requests for this phone number. Please wait 10 minutes.");
    }

    private void checkRateLimit(String key, int maxRequests, Duration window, String errorMessage) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, window);
        }
        if (count != null && count > maxRequests) {
            throw new RateLimitExceededException(errorMessage);
        }
    }
}

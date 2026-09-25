package com.pockt.infrastructure.security;

import com.pockt.infrastructure.exception.InvalidTokenException;
import com.pockt.infrastructure.exception.TokenExpiredException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final ResourceLoader resourceLoader;
    private final StringRedisTemplate redisTemplate;

    @Value("${pockt.jwt.private-key-path:classpath:keys/private.pem}")
    private String privateKeyPath;

    @Value("${pockt.jwt.public-key-path:classpath:keys/public.pem}")
    private String publicKeyPath;

    @Value("${pockt.jwt.access-token-ttl-seconds:900}")
    private long accessTokenTtlSeconds;

    @Value("${pockt.jwt.refresh-token-ttl-seconds:2592000}")
    private long refreshTokenTtlSeconds;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    public JwtService(ResourceLoader resourceLoader, StringRedisTemplate redisTemplate) {
        this.resourceLoader = resourceLoader;
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void initKeys() {
        try {
            this.privateKey = loadPrivateKey(privateKeyPath);
            this.publicKey = loadPublicKey(publicKeyPath);
            log.info("JWT RSA256 keys successfully initialized");
        } catch (Exception e) {
            log.error("Failed to load JWT RSA keys from paths: {} and {}", privateKeyPath, publicKeyPath, e);
            throw new IllegalStateException("Could not load RSA keys for JWT", e);
        }
    }

    private PrivateKey loadPrivateKey(String path) throws Exception {
        Resource resource = resourceLoader.getResource(path);
        try (InputStream is = resource.getInputStream()) {
            String keyStr = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            String privateKeyPEM = keyStr
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] encoded = Base64.getDecoder().decode(privateKeyPEM);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
            return keyFactory.generatePrivate(keySpec);
        }
    }

    private PublicKey loadPublicKey(String path) throws Exception {
        Resource resource = resourceLoader.getResource(path);
        try (InputStream is = resource.getInputStream()) {
            String keyStr = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            String publicKeyPEM = keyStr
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] encoded = Base64.getDecoder().decode(publicKeyPEM);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
            return keyFactory.generatePublic(keySpec);
        }
    }

    public String generateAccessToken(UUID userId, String phone, String role) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(accessTokenTtlSeconds);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("phone", phone)
                .claim("role", role != null ? role : "USER")
                .claim("type", "ACCESS")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public String generateAccessToken(UUID userId, String phone) {
        return generateAccessToken(userId, phone, "USER");
    }

    public String generateTempToken(String phone) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(600); // 10 minutes

        return Jwts.builder()
                .subject("TEMP")
                .claim("phone", phone)
                .claim("type", "TEMP")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public String generateRefreshToken(UUID userId) {
        String token = UUID.randomUUID().toString();
        String redisKey = "refresh_token:" + token;
        redisTemplate.opsForValue().set(redisKey, userId.toString(), Duration.ofSeconds(refreshTokenTtlSeconds));
        return token;
    }

    public Optional<UUID> validateRefreshToken(String refreshToken) {
        String redisKey = "refresh_token:" + refreshToken;
        String userIdStr = redisTemplate.opsForValue().get(redisKey);
        if (userIdStr == null) {
            return Optional.empty();
        }
        return Optional.of(UUID.fromString(userIdStr));
    }

    public void invalidateRefreshToken(String refreshToken) {
        String redisKey = "refresh_token:" + refreshToken;
        redisTemplate.delete(redisKey);
    }

    public Claims parseAndValidate(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new TokenExpiredException();
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("Invalid or malformed token");
        }
    }

    public Claims parseAndValidateTempToken(String token) {
        Claims claims = parseAndValidate(token);
        if (!"TEMP".equals(claims.get("type"))) {
            throw new InvalidTokenException("Expected temporary registration token");
        }
        return claims;
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }
}

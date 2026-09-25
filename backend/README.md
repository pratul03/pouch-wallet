# Pockt — Standalone Server

> Production-grade peer-to-peer digital wallet backend built for high-load resilience.
> Single deployable Spring Boot JAR · Modular Monolith architecture · PostgreSQL 16+ · Redis 7+

---

## Tech Stack

- **Runtime**: Java 21 LTS (Virtual Threads enabled: `spring.threads.virtual.enabled=true`)
- **Framework**: Spring Boot 3.3.4
- **Database Access**: Spring JDBC (`NamedParameterJdbcTemplate`) with HikariCP
- **Migrations**: Flyway (`src/main/resources/db/migration`)
- **Security**: Spring Security 6 + RS256 JWT (`jjwt` 0.12.6)
- **Cache & Rate Limiting**: Redis (Spring Data Redis / Lettuce)
- **API Documentation**: SpringDoc OpenAPI 3 / Swagger UI
- **Testing**: JUnit 5, Mockito, ArchUnit

---

## Architecture & Module Structure

```
com.pockt
├── PocktApplication.java          # Spring Boot main class
├── infrastructure/                # Cross-cutting: security, JWT, ratelimit, exceptions
├── user/                          # User module: auth, OTP, profiles, BCrypt PIN
├── wallet/                        # Wallet module: balance, auto-creation, mock top-up
├── transfer/                      # Transfer module: atomic saga, pessimistic locking, idempotency
└── notification/                  # Notification module: transactional outbox poller, push/SMS
```

### Module Boundary Principles
- **Loose Coupling**: Modules interact only through public `*Service` interfaces and events.
- **Deadlock-Free Locking**: Wallets are always locked in consistent UUID order (`min(UUID)` first).
- **Idempotency Guarantee**: Transfers deduplicate using `X-Idempotency-Key` or body key.
- **Append-Only Audit**: `audit_log` is immutable and recorded with every transfer.
- **Transactional Outbox**: Decoupled push notification delivery using `SKIP LOCKED`.

---

## Quick Start (Local Development)

### 1. Requirements
- Java 21+ (`openjdk 21`)
- PostgreSQL (`localhost:5432`, database: `pockt`, user: `pockt`, password: `pockt`)
- Redis (`localhost:6379`)

### 2. Run Tests
```bash
./gradlew test
```
All unit tests, ArchUnit architectural boundary checks, and the 20-thread concurrency test are executed.

### 3. Start the Server
```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```
or run the built JAR:
```bash
java -jar build/libs/pockt-0.1.0.jar --spring.profiles.active=dev
```

### 4. Interactive API Documentation (Swagger UI)
Visit: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

### 5. Health Check
```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

---

## API Summary

All responses follow the unified envelope:
```json
{
  "success": true,
  "data": {},
  "error": null,
  "requestId": "uuid",
  "timestamp": "2026-09-26T10:00:00Z"
}
```

### Auth & User (`/api/v1/auth`, `/api/v1/users`)
- `POST /api/v1/auth/otp/send` — Request 6-digit OTP to phone
- `POST /api/v1/auth/otp/verify` — Verify OTP and receive temporary registration token
- `POST /api/v1/auth/register` — Complete registration with full name & 6-digit PIN
- `POST /api/v1/auth/login` — Login with phone + PIN to receive access & refresh tokens
- `POST /api/v1/auth/refresh` — Rotate refresh token
- `POST /api/v1/auth/logout` — Invalidate refresh token
- `GET /api/v1/users/me` — Current user profile
- `PATCH /api/v1/users/me` — Update full name
- `GET /api/v1/users/search?phone=` — Lookup user for transfer flow
- `POST /api/v1/users/me/fcm-token` — Register FCM device push token

### Wallets (`/api/v1/wallets`)
- `GET /api/v1/wallets` — List current user's wallets
- `GET /api/v1/wallets/{id}` — Get wallet balance
- `POST /api/v1/wallets/{id}/topup` — Mock balance funding (MVP)

### Transfers (`/api/v1/transfers`)
- `POST /api/v1/transfers` — Initiate transfer (`X-Idempotency-Key` header)
- `GET /api/v1/transfers?cursor=&limit=20` — Cursor-based transaction history
- `GET /api/v1/transfers/{id}` — Full transaction detail

# Digital Wallet MVP — Project Planner

> Peer-to-peer money transfer application built for heavy-load resilience.
> Backend: Java 21 + Spring Boot 3.3 + JDBC + PostgreSQL
> Mobile: Flutter 3.x (iOS + Android)
> Infrastructure: Redis · Kafka · Docker · GitHub Actions

---

## Table of Contents

1. [Goal & Vision](#1-goal--vision)
2. [Tech Stack](#2-tech-stack)
3. [Architecture Overview](#3-architecture-overview)
4. [Database Schema](#4-database-schema)
5. [Microservices & Layers](#5-microservices--layers)
6. [Feature Breakdown](#6-feature-breakdown)
7. [API Reference](#7-api-reference)
8. [Code Principles](#8-code-principles)
9. [Test Strategy](#9-test-strategy)
10. [Phase Plan](#10-phase-plan)
11. [Non-Functional Requirements](#11-non-functional-requirements)
12. [Folder Structure](#12-folder-structure)

---

## 1. Goal & Vision

Build a production-grade **peer-to-peer digital wallet** where users can:

- Register with a phone number and verify via OTP
- Hold a wallet balance in one or more currencies
- Send and receive money instantly with guaranteed consistency
- View full transaction history with real-time push notifications
- Trust that no race condition, duplicate charge, or failed saga goes unhandled

The backend is designed to sustain **10,000+ concurrent users** and survive any single service or database failure without data loss or double-charging.

### MVP Success Criteria

- A user can register, fund their wallet (mock top-up), send money to another user by phone number, and receive a push notification — all within 3 seconds end-to-end.
- Simultaneous transfers from the same wallet never overdraft the account.
- A failed transfer at any step fully rolls back with no money lost.
- 95th percentile API response time under 200 ms under load.

---

## 2. Tech Stack

### Backend

| Layer            | Technology                                              | Reason                                               |
| ---------------- | ------------------------------------------------------- | ---------------------------------------------------- |
| Language         | Java 21                                                 | Virtual threads, pattern matching, records           |
| Framework        | Spring Boot 3.3                                         | Production-ready, deep PostgreSQL + JDBC support     |
| Data access      | Spring JDBC (JdbcTemplate / NamedParameterJdbcTemplate) | Full SQL control, no ORM magic hiding lock behaviour |
| Connection pool  | HikariCP (bundled)                                      | Fastest JDBC pool; built-in health checks            |
| Auth             | Spring Security 6 + JWT (jjwt 0.12)                     | Stateless; RS256 signed tokens                       |
| Resilience       | Resilience4j 2.x                                        | Circuit breakers, retry, rate limiter, bulkhead      |
| Async / events   | Spring ApplicationEvents → Kafka (phase 2)              | Decoupled notification pipeline                      |
| Cache / sessions | Redis (Spring Data Redis)                               | Token bucket rate limiting; session store            |
| API docs         | SpringDoc OpenAPI 3                                     | Auto-generated from annotations                      |
| Validation       | Jakarta Bean Validation 3                               | Declarative input validation                         |
| Build            | Gradle 8 (Kotlin DSL)                                   | Faster than Maven; cache-friendly                    |

### Mobile

| Layer              | Technology                    |
| ------------------ | ----------------------------- |
| Framework          | Flutter 3.x (Dart)            |
| State management   | Riverpod 2                    |
| HTTP client        | Dio + Retrofit-style code gen |
| Local storage      | Flutter Secure Storage (JWT)  |
| Push notifications | Firebase Messaging (FCM)      |
| Biometrics         | local_auth package            |
| Navigation         | GoRouter                      |

### Infrastructure

| Component      | Technology                                  |
| -------------- | ------------------------------------------- |
| Database       | PostgreSQL 16                               |
| Cache          | Redis 7                                     |
| Message broker | Apache Kafka 3.7                            |
| Containers     | Docker + Docker Compose                     |
| CI/CD          | GitHub Actions                              |
| Secrets        | Environment variables → Vault (phase 3)     |
| Monitoring     | Micrometer + Prometheus + Grafana (phase 3) |

---

## 3. Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                  Flutter Mobile App                      │
│   Auth · Wallet · Send/Receive · History · Notifications │
└───────────────────────┬─────────────────────────────────┘
                        │ HTTPS + JWT (RS256)
                        ▼
┌─────────────────────────────────────────────────────────┐
│                   API Gateway Service                    │
│        JWT filter · Rate limiter · Request routing       │
└──┬──────────┬──────────┬───────────────────────┬────────┘
   │          │          │                       │
   ▼          ▼          ▼                       ▼
User      Wallet     Transfer              Notification
Service   Service    Service               Service
   │          │          │                       │
   └──────────┴──────────┴───────────────────────┘
                        │ JDBC
                        ▼
               ┌─────────────────┐
               │   PostgreSQL 16  │
               │ users · wallets  │
               │ transactions     │
               │ audit_log        │
               └─────────────────┘
                        │
          ┌─────────────┴──────────────┐
          ▼                            ▼
      Redis 7                    Kafka 3.7
  (sessions, rate limits)    (transfer events,
                              notification queue)
```

### Request lifecycle — money transfer

```
Flutter → API Gateway (validate JWT, check rate limit)
       → Transfer Service
           1. Validate request (idempotency key check)
           2. BEGIN TRANSACTION
           3. SELECT wallets FOR UPDATE (pessimistic lock)
           4. Check sender balance ≥ amount
           5. Debit sender wallet
           6. Credit receiver wallet
           7. INSERT into transactions (status=COMPLETED)
           8. INSERT into audit_log
           9. COMMIT
          10. Publish TransferCompletedEvent → Kafka
              → Notification Service → FCM push
```

---

## 4. Database Schema

### Design rules

- All IDs are `UUID` (generated by the application, not the DB)
- All timestamps are `TIMESTAMPTZ` (UTC stored, timezone-aware)
- `audit_log` is append-only — no UPDATE or DELETE ever
- Monetary amounts stored as `BIGINT` (smallest currency unit, e.g. cents)
- `wallets.balance` is locked with `SELECT ... FOR UPDATE` on every write path
- `transactions.idempotency_key` has a unique index to prevent duplicate charges

```sql
-- ─────────────────────────────────────────
-- users
-- ─────────────────────────────────────────
CREATE TABLE users (
    id              UUID PRIMARY KEY,
    phone           VARCHAR(20)  NOT NULL UNIQUE,
    full_name       VARCHAR(120) NOT NULL,
    pin_hash        VARCHAR(255) NOT NULL,           -- BCrypt(6-digit PIN)
    fcm_token       VARCHAR(512),                    -- push notification token
    kyc_status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
                                                     -- PENDING | VERIFIED | REJECTED
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_phone ON users (phone);

-- ─────────────────────────────────────────
-- otp_verifications
-- ─────────────────────────────────────────
CREATE TABLE otp_verifications (
    id          UUID PRIMARY KEY,
    phone       VARCHAR(20)  NOT NULL,
    otp_hash    VARCHAR(255) NOT NULL,               -- BCrypt of 6-digit OTP
    purpose     VARCHAR(30)  NOT NULL,               -- REGISTRATION | LOGIN | PIN_RESET
    expires_at  TIMESTAMPTZ  NOT NULL,
    used        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_otp_phone_purpose ON otp_verifications (phone, purpose, used);

-- ─────────────────────────────────────────
-- wallets
-- ─────────────────────────────────────────
CREATE TABLE wallets (
    id          UUID PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users(id),
    currency    CHAR(3)      NOT NULL DEFAULT 'USD',
    balance     BIGINT       NOT NULL DEFAULT 0 CHECK (balance >= 0),
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, currency)
);

CREATE INDEX idx_wallets_user_id ON wallets (user_id);

-- ─────────────────────────────────────────
-- transactions
-- ─────────────────────────────────────────
CREATE TABLE transactions (
    id               UUID PRIMARY KEY,
    idempotency_key  VARCHAR(64)  NOT NULL UNIQUE,
    sender_wallet_id UUID         NOT NULL REFERENCES wallets(id),
    receiver_wallet_id UUID       NOT NULL REFERENCES wallets(id),
    amount           BIGINT       NOT NULL CHECK (amount > 0),
    currency         CHAR(3)      NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
                                           -- PENDING | COMPLETED | FAILED | REVERSED
    description      VARCHAR(255),
    saga_id          UUID,                 -- groups saga steps
    failure_reason   VARCHAR(500),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transactions_sender   ON transactions (sender_wallet_id, created_at DESC);
CREATE INDEX idx_transactions_receiver ON transactions (receiver_wallet_id, created_at DESC);
CREATE INDEX idx_transactions_idempotency ON transactions (idempotency_key);

-- ─────────────────────────────────────────
-- audit_log  (append-only, never update/delete)
-- ─────────────────────────────────────────
CREATE TABLE audit_log (
    id            UUID PRIMARY KEY,
    entity_type   VARCHAR(50)   NOT NULL,   -- USER | WALLET | TRANSACTION
    entity_id     UUID          NOT NULL,
    action        VARCHAR(50)   NOT NULL,   -- CREATED | CREDITED | DEBITED | STATUS_CHANGED
    actor_id      UUID,                     -- user who triggered; NULL = system
    old_value     JSONB,
    new_value     JSONB,
    ip_address    INET,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_entity ON audit_log (entity_type, entity_id, created_at DESC);

-- ─────────────────────────────────────────
-- notification_outbox  (transactional outbox pattern)
-- ─────────────────────────────────────────
CREATE TABLE notification_outbox (
    id           UUID PRIMARY KEY,
    user_id      UUID         NOT NULL REFERENCES users(id),
    type         VARCHAR(50)  NOT NULL,   -- TRANSFER_SENT | TRANSFER_RECEIVED | OTP
    payload      JSONB        NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempts     INT          NOT NULL DEFAULT 0,
    next_retry   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_outbox_pending ON notification_outbox (status, next_retry)
    WHERE status = 'PENDING';
```

---

## 5. Microservices & Layers

Each Spring Boot service follows the same internal layering:

```
Controller (HTTP layer)
    ↓
Service (business logic, @Transactional)
    ↓
Repository (JDBC, raw SQL)
    ↓
PostgreSQL
```

No JPA. No Hibernate. JDBC only — so every query, every lock, every index hint is explicit and visible.

### 5.1 User Service (`/api/v1/users`, `/api/v1/auth`)

Responsibilities:

- Phone registration flow (send OTP → verify OTP → set PIN → issue JWT)
- Login (phone + PIN → issue JWT)
- JWT refresh
- Profile read / update
- FCM token registration
- KYC status (stub for MVP, real integration in phase 3)

Key classes:

- `UserController` — REST endpoints
- `AuthController` — login, register, OTP, refresh
- `UserService` — orchestrates registration, OTP generation, JWT issuance
- `OtpService` — generates 6-digit OTP, BCrypt-hashes it, stores with TTL
- `JwtService` — RS256 sign / verify using `jjwt`
- `UserRepository` — JDBC queries on `users` and `otp_verifications`

### 5.2 Wallet Service (`/api/v1/wallets`)

Responsibilities:

- Create wallet on user registration (event-driven)
- Get balance
- Mock top-up (MVP only — real payment gateway in phase 3)
- List wallets for a user

Key classes:

- `WalletController`
- `WalletService` — balance reads, top-up with audit log
- `WalletRepository` — JDBC with `SELECT ... FOR UPDATE` for writes

### 5.3 Transfer Service (`/api/v1/transfers`)

Responsibilities:

- Initiate transfer (the saga)
- Idempotency check on every request
- Pessimistic locking of both wallets
- Debit sender, credit receiver atomically
- Write to `transactions` and `audit_log` in the same transaction
- Publish `TransferCompletedEvent` to the notification outbox
- Transaction history with cursor-based pagination
- Reversal / refund (phase 2)

Key classes:

- `TransferController`
- `TransferService` — the saga coordinator
- `TransferRepository` — JDBC on `transactions`, `wallets`
- `AuditRepository` — insert-only on `audit_log`
- `NotificationOutboxRepository` — insert into outbox within the same DB transaction
- `OutboxPoller` — `@Scheduled` job, polls outbox using `SKIP LOCKED`, publishes to Kafka

### 5.4 Notification Service (`internal, no public API`)

Responsibilities:

- Consume Kafka `transfer.events` topic
- Send FCM push to sender (transfer sent) and receiver (transfer received)
- Send SMS OTP via Twilio (stubbed in dev)
- Mark outbox row as DELIVERED or schedule retry

Key classes:

- `TransferEventConsumer` — `@KafkaListener`
- `FcmService` — Firebase Admin SDK push sender
- `SmsService` — Twilio client wrapper (interface + mock in dev profile)

### 5.5 API Gateway (Spring Boot filter chain, not a separate service in MVP)

Responsibilities:

- Validate JWT on every protected route
- Rate limiting per user (Redis token bucket, 60 req/min default)
- Request ID injection (`X-Request-Id` header)
- Global exception handler → consistent error envelope
- HTTPS enforcement

---

## 6. Feature Breakdown

### Auth & Identity

| #   | Feature                  | Notes                                                         |
| --- | ------------------------ | ------------------------------------------------------------- |
| A1  | Phone + OTP registration | 6-digit OTP, 5 min TTL, max 3 attempts                        |
| A2  | 6-digit PIN setup        | BCrypt hashed, never stored in plain text                     |
| A3  | Login with phone + PIN   | Returns access token (15 min) + refresh token (30 days)       |
| A4  | JWT refresh              | Refresh token is stored in Redis; rotated on use              |
| A5  | Logout                   | Invalidate refresh token in Redis                             |
| A6  | Biometric login          | Flutter local_auth; calls same login endpoint with stored PIN |
| A7  | FCM token registration   | Called after login; stored on `users.fcm_token`               |

### Wallet

| #   | Feature                            | Notes                                                  |
| --- | ---------------------------------- | ------------------------------------------------------ |
| W1  | Auto-create wallet on registration | USD by default; event-driven via `UserRegisteredEvent` |
| W2  | View balance                       | Real-time read from DB (no cache for balance)          |
| W3  | Mock top-up                        | POST /wallets/{id}/topup — phase 1 only                |
| W4  | Multi-currency wallet              | Phase 2; schema already supports it                    |

### Transfers

| #   | Feature                    | Notes                                                 |
| --- | -------------------------- | ----------------------------------------------------- |
| T1  | Send money by phone number | Resolve phone → wallet → saga                         |
| T2  | Idempotency guarantee      | Client sends `X-Idempotency-Key`; server deduplicates |
| T3  | Insufficient balance guard | Checked inside locked transaction                     |
| T4  | Transaction history        | Cursor-based pagination, 20 per page                  |
| T5  | Transaction detail         | Full saga state, status, timestamps                   |
| T6  | Transfer reversal          | Phase 2; compensating transaction pattern             |

### Notifications

| #   | Feature                   | Notes                                  |
| --- | ------------------------- | -------------------------------------- |
| N1  | Push on transfer sent     | "You sent $50 to Alice"                |
| N2  | Push on transfer received | "Bob sent you $30"                     |
| N3  | OTP SMS                   | Stubbed with log output in dev profile |
| N4  | In-app notification feed  | Phase 2                                |

---

## 7. API Reference

All responses follow this envelope:

```json
{
  "success": true,
  "data": {},
  "error": null,
  "requestId": "uuid",
  "timestamp": "2026-09-26T10:00:00Z"
}
```

Error shape:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "INSUFFICIENT_BALANCE",
    "message": "Wallet balance is too low for this transfer",
    "field": null
  }
}
```

### Auth endpoints

```
POST   /api/v1/auth/otp/send          Send OTP to phone
POST   /api/v1/auth/otp/verify        Verify OTP → returns temp token
POST   /api/v1/auth/register          Complete registration (name + PIN)
POST   /api/v1/auth/login             Phone + PIN → access + refresh tokens
POST   /api/v1/auth/refresh           Rotate refresh token
POST   /api/v1/auth/logout            Invalidate refresh token
POST   /api/v1/auth/fcm-token         Register FCM device token
```

### User endpoints

```
GET    /api/v1/users/me               Get own profile
PATCH  /api/v1/users/me               Update name
GET    /api/v1/users/search?phone=    Find user by phone (for send flow)
```

### Wallet endpoints

```
GET    /api/v1/wallets                List my wallets
GET    /api/v1/wallets/{id}           Get wallet + balance
POST   /api/v1/wallets/{id}/topup     Mock top-up (MVP only)
```

### Transfer endpoints

```
POST   /api/v1/transfers              Initiate transfer
GET    /api/v1/transfers              List my transactions (paginated)
GET    /api/v1/transfers/{id}         Get transaction detail
```

#### POST /api/v1/transfers — request body

```json
{
  "receiverPhone": "+1234567890",
  "amount": 5000,
  "currency": "USD",
  "description": "Lunch split",
  "idempotencyKey": "client-generated-uuid"
}
```

#### POST /api/v1/transfers — success response

```json
{
  "success": true,
  "data": {
    "transactionId": "uuid",
    "status": "COMPLETED",
    "amount": 5000,
    "currency": "USD",
    "senderBalance": 45000,
    "createdAt": "2026-09-26T10:00:00Z"
  }
}
```

### Error codes

| Code                   | HTTP | Meaning                          |
| ---------------------- | ---- | -------------------------------- |
| `INVALID_OTP`          | 400  | OTP wrong or expired             |
| `OTP_MAX_ATTEMPTS`     | 429  | Too many OTP attempts            |
| `USER_NOT_FOUND`       | 404  | Phone not registered             |
| `WALLET_NOT_FOUND`     | 404  | Wallet does not exist            |
| `INSUFFICIENT_BALANCE` | 422  | Sender balance too low           |
| `DUPLICATE_TRANSFER`   | 409  | Idempotency key already used     |
| `RECEIVER_NOT_FOUND`   | 404  | Receiver phone not registered    |
| `SELF_TRANSFER`        | 422  | Sender and receiver are the same |
| `RATE_LIMIT_EXCEEDED`  | 429  | Too many requests                |
| `TOKEN_EXPIRED`        | 401  | JWT expired                      |
| `INVALID_TOKEN`        | 401  | JWT invalid or tampered          |

---

## 8. Code Principles

### 8.1 General

- **No magic.** No JPA, no auto-generated queries. Every SQL statement is explicit, readable, and reviewable.
- **Fail fast.** Validate inputs at the controller layer before any DB call.
- **Immutability first.** Use Java records for DTOs and request/response objects.
- **Explicit over implicit.** Prefer named constants over string literals. Prefer `enum` over `String` for status fields.
- **No checked exceptions in service layer.** Use custom `RuntimeException` subclasses with error codes.
- **One responsibility per class.** A repository only runs SQL. A service only runs business logic. A controller only reads HTTP input and writes HTTP output.

### 8.2 Money handling

```java
// NEVER use double or float for money
// ALWAYS store in smallest unit (cents)
// ALWAYS use BigDecimal for display conversion

// Correct:
long amountCents = 5000; // $50.00

// Wrong:
double amount = 50.00; // float precision errors will destroy you
```

### 8.3 Transactions and locking

```java
// Transfer service — always lock both wallets in consistent ID order
// to prevent deadlock (lower UUID first)
@Transactional(isolation = Isolation.READ_COMMITTED)
public TransactionResult transfer(TransferCommand cmd) {
    UUID firstId  = min(cmd.senderWalletId(), cmd.receiverWalletId());
    UUID secondId = max(cmd.senderWalletId(), cmd.receiverWalletId());

    Wallet first  = walletRepository.findByIdForUpdate(firstId);
    Wallet second = walletRepository.findByIdForUpdate(secondId);

    Wallet sender   = first.id().equals(cmd.senderWalletId()) ? first : second;
    Wallet receiver = first.id().equals(cmd.senderWalletId()) ? second : first;

    if (sender.balance() < cmd.amount()) {
        throw new InsufficientBalanceException();
    }
    // debit and credit ...
}
```

### 8.4 Idempotency

```java
// Every write endpoint checks idempotency key before executing
public TransactionResult transfer(TransferCommand cmd) {
    Optional<Transaction> existing =
        transactionRepository.findByIdempotencyKey(cmd.idempotencyKey());
    if (existing.isPresent()) {
        return TransactionResult.fromExisting(existing.get()); // return cached result
    }
    // proceed with saga ...
}
```

### 8.5 Repository pattern

```java
// Repositories take and return domain objects, not ResultSet rows
// Use RowMapper to map ResultSet → record cleanly

public record Wallet(UUID id, UUID userId, String currency, long balance, boolean isActive) {}

private static final RowMapper<Wallet> WALLET_MAPPER = (rs, rowNum) -> new Wallet(
    UUID.fromString(rs.getString("id")),
    UUID.fromString(rs.getString("user_id")),
    rs.getString("currency"),
    rs.getLong("balance"),
    rs.getBoolean("is_active")
);
```

### 8.6 Exception hierarchy

```
WalletException (base RuntimeException)
├── InsufficientBalanceException       (422)
├── WalletNotFoundException            (404)
├── DuplicateTransferException         (409)
├── UserNotFoundException              (404)
├── InvalidOtpException                (400)
├── OtpMaxAttemptsException            (429)
├── SelfTransferException              (422)
└── ReceiverNotFoundException          (404)
```

### 8.7 Virtual threads (Java 21)

```java
// application.properties
spring.threads.virtual.enabled=true

// This single line turns every request thread into a virtual thread.
// JDBC blocking calls (SELECT FOR UPDATE) become cheap — no thread starved.
// No CompletableFuture gymnastics needed in the service layer.
```

### 8.8 Resilience patterns

```java
// Circuit breaker on external calls (FCM, SMS)
@CircuitBreaker(name = "fcm", fallbackMethod = "fcmFallback")
@Retry(name = "fcm")
public void sendPush(String token, String title, String body) { ... }

// Rate limiter on transfer endpoint (per user, via Redis)
// Configured in application.yml:
// resilience4j.ratelimiter.instances.transfer.limitForPeriod=10
// resilience4j.ratelimiter.instances.transfer.limitRefreshPeriod=1m
```

---

## 9. Test Strategy

### 9.1 Testing pyramid

```
          ┌──────────────┐
          │   E2E Tests   │  ← 5%  (Postman / Flutter integration)
          └──────┬───────┘
         ┌───────┴────────┐
         │ Integration     │  ← 25% (Spring @SpringBootTest + Testcontainers)
         └───────┬─────────┘
        ┌────────┴─────────┐
        │    Unit Tests     │  ← 70% (JUnit 5 + Mockito)
        └──────────────────┘
```

### 9.2 Unit tests

Every service class has a corresponding unit test. No Spring context. No database. Pure logic.

```java
// TransferServiceTest.java
@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock WalletRepository walletRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock AuditRepository auditRepository;
    @InjectMocks TransferService transferService;

    @Test
    void transfer_shouldDebitSenderAndCreditReceiver() {
        var senderWallet   = new Wallet(UUID.randomUUID(), userId, "USD", 10000L, true);
        var receiverWallet = new Wallet(UUID.randomUUID(), receiverId, "USD", 0L, true);
        var cmd = new TransferCommand(senderWallet.id(), receiverWallet.id(), 3000L, "USD", "key-1", "Test");

        when(walletRepository.findByIdForUpdate(any())).thenReturn(senderWallet, receiverWallet);

        TransactionResult result = transferService.transfer(cmd);

        assertThat(result.status()).isEqualTo(TransactionStatus.COMPLETED);
        verify(walletRepository).debit(senderWallet.id(), 3000L);
        verify(walletRepository).credit(receiverWallet.id(), 3000L);
    }

    @Test
    void transfer_shouldThrow_whenBalanceInsufficient() {
        var senderWallet = new Wallet(UUID.randomUUID(), userId, "USD", 100L, true);
        // amount > balance
        var cmd = new TransferCommand(senderWallet.id(), receiverId, 5000L, "USD", "key-2", "Test");

        when(walletRepository.findByIdForUpdate(any())).thenReturn(senderWallet, receiverWallet);

        assertThatThrownBy(() -> transferService.transfer(cmd))
            .isInstanceOf(InsufficientBalanceException.class);

        verify(walletRepository, never()).debit(any(), anyLong());
    }

    @Test
    void transfer_shouldReturnCachedResult_whenIdempotencyKeyExists() {
        var existingTx = new Transaction(/* ... status=COMPLETED ... */);
        when(transactionRepository.findByIdempotencyKey("key-3")).thenReturn(Optional.of(existingTx));

        TransactionResult result = transferService.transfer(cmdWithKey("key-3"));

        assertThat(result.status()).isEqualTo(TransactionStatus.COMPLETED);
        verify(walletRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void transfer_shouldThrow_whenSenderEqualsReceiver() {
        UUID sameId = UUID.randomUUID();
        assertThatThrownBy(() -> transferService.transfer(new TransferCommand(sameId, sameId, 100L, ...)))
            .isInstanceOf(SelfTransferException.class);
    }
}
```

### 9.3 Repository integration tests (Testcontainers)

```java
@SpringBootTest
@Testcontainers
class WalletRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired WalletRepository walletRepository;

    @Test
    @Transactional
    void findByIdForUpdate_shouldAcquirePessimisticLock() {
        UUID walletId = createTestWallet(10000L);
        Wallet wallet = walletRepository.findByIdForUpdate(walletId);
        assertThat(wallet.balance()).isEqualTo(10000L);
    }

    @Test
    void debit_shouldPreventNegativeBalance() {
        UUID walletId = createTestWallet(100L);
        assertThatThrownBy(() -> walletRepository.debit(walletId, 500L))
            .hasMessageContaining("check constraint");
    }
}
```

### 9.4 Controller integration tests

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TransferControllerTest {

    @Autowired TestRestTemplate rest;

    @Test
    void postTransfer_shouldReturn200_withValidRequest() {
        String jwt = loginAndGetToken("+12345678901");
        HttpHeaders headers = bearerHeaders(jwt);
        headers.set("X-Idempotency-Key", UUID.randomUUID().toString());

        var body = Map.of("receiverPhone", "+10987654321", "amount", 1000, "currency", "USD");

        ResponseEntity<ApiResponse<TransactionResult>> resp =
            rest.exchange("/api/v1/transfers", POST, new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<>() {});

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().data().status()).isEqualTo("COMPLETED");
    }

    @Test
    void postTransfer_shouldReturn409_onDuplicateIdempotencyKey() {
        String key = UUID.randomUUID().toString();
        sendTransfer(key); // first call succeeds
        ResponseEntity<?> second = sendTransfer(key); // duplicate
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void postTransfer_shouldReturn422_whenBalanceInsufficient() { ... }

    @Test
    void postTransfer_shouldReturn401_withoutToken() { ... }

    @Test
    void postTransfer_shouldReturn429_whenRateLimitExceeded() { ... }
}
```

### 9.5 Concurrency tests

```java
@Test
void concurrentTransfers_shouldNeverOverdraft() throws InterruptedException {
    UUID walletId = createWalletWithBalance(10000L); // $100
    int threads = 20;
    long transferAmount = 1000L; // each tries to send $10 → only 10 can succeed

    ExecutorService executor = Executors.newFixedThreadPool(threads);
    List<Future<Boolean>> futures = IntStream.range(0, threads)
        .mapToObj(i -> executor.submit(() -> {
            try {
                transferService.transfer(new TransferCommand(walletId, receiverId, transferAmount, ...));
                return true;
            } catch (InsufficientBalanceException e) {
                return false;
            }
        }))
        .toList();

    executor.awaitTermination(10, TimeUnit.SECONDS);
    long succeeded = futures.stream().filter(f -> getUnchecked(f)).count();

    assertThat(succeeded).isEqualTo(10); // exactly 10 succeed
    assertThat(walletRepository.getBalance(walletId)).isEqualTo(0L); // drained to zero, not negative
}
```

### 9.6 Flutter tests

```dart
// Unit test — transfer bloc / provider
test('transfer emits success state on valid input', () async {
  final mockRepo = MockTransferRepository();
  when(mockRepo.send(any)).thenAnswer((_) async => TransactionResult.success());
  final notifier = TransferNotifier(mockRepo);

  await notifier.send(amount: 50.00, receiverPhone: '+12345678901');

  expect(notifier.state, isA<TransferSuccess>());
});

// Widget test — send money screen
testWidgets('shows error when amount is zero', (tester) async {
  await tester.pumpWidget(const SendMoneyScreen());
  await tester.tap(find.byKey(Key('send-button')));
  await tester.pump();
  expect(find.text('Enter an amount greater than zero'), findsOneWidget);
});
```

---

## 10. Phase Plan

### Phase 1 — Core MVP (Weeks 1–6)

Goal: A user can register, hold a balance, and send money to another user.

| Week | Deliverable                                                              |
| ---- | ------------------------------------------------------------------------ |
| 1    | Project setup · Docker Compose · PostgreSQL schema · Gradle multi-module |
| 2    | User Service: OTP · registration · login · JWT                           |
| 3    | Wallet Service: create wallet · balance · mock top-up                    |
| 4    | Transfer Service: saga · locking · idempotency · audit log               |
| 5    | Notification Service: outbox poller · FCM push                           |
| 6    | Flutter: auth flow · wallet screen · send money · history                |

**Definition of done:** Two physical phones can register, fund wallets, and transfer money end-to-end. All Phase 1 unit and integration tests pass. No overdraft possible under concurrent load.

---

### Phase 2 — Hardening (Weeks 7–10)

| Week | Deliverable                                                 |
| ---- | ----------------------------------------------------------- |
| 7    | Kafka integration · replace Spring Events with Kafka topics |
| 8    | Transfer reversal / refund · multi-currency wallet          |
| 9    | In-app notification feed · pagination improvements          |
| 10   | Load testing (Gatling) · performance tuning · index review  |

---

### Phase 3 — Production Ready (Weeks 11–14)

| Week | Deliverable                                                        |
| ---- | ------------------------------------------------------------------ |
| 11   | Real payment gateway integration (Stripe / Paystack)               |
| 12   | KYC integration · AML transaction screening                        |
| 13   | Vault secrets · Prometheus + Grafana monitoring · PagerDuty alerts |
| 14   | iOS + Android store submission · penetration test · audit          |

---

## 11. Non-Functional Requirements

### Performance targets

| Metric                     | Target                  |
| -------------------------- | ----------------------- |
| Transfer API p95 latency   | < 200 ms                |
| Balance read p99 latency   | < 50 ms                 |
| Concurrent users (MVP)     | 1,000                   |
| Concurrent users (phase 2) | 10,000                  |
| DB connection pool size    | 20 per service instance |
| Max HikariCP wait timeout  | 5 seconds               |

### Resilience

- Circuit breaker opens after 5 consecutive failures on FCM / SMS
- Transfer endpoint rate-limited to 10 requests/minute per user
- OTP endpoint rate-limited to 3 attempts per phone per 10 minutes
- All external HTTP calls have a 3-second timeout
- Database queries have a 5-second statement timeout (`SET statement_timeout = '5s'`)

### Security

- JWTs signed with RS256 (asymmetric); private key never leaves the auth service
- PINs and OTPs are BCrypt-hashed before storage
- All endpoints require HTTPS in production
- `audit_log` has `GRANT INSERT` only — no UPDATE or DELETE even for app users
- SQL injection: impossible by design (only parameterised JDBC queries)
- All financial amounts validated server-side; client values are never trusted

### Observability

- Every request has an `X-Request-Id` header (UUID) logged end-to-end
- Structured JSON logging (Logback + logstash-logback-encoder)
- Micrometer metrics on every service: transfer rate, error rate, DB pool usage
- Kafka consumer lag tracked per topic partition

---

## 12. Folder Structure

### Spring Boot (Gradle multi-module)

```
wallet-backend/
├── build.gradle.kts                    # root build
├── settings.gradle.kts                 # module declarations
├── docker-compose.yml                  # postgres, redis, kafka, zookeeper
├── .env.example
│
├── common/                             # shared DTOs, exceptions, utils
│   └── src/main/java/com/wallet/common/
│       ├── dto/          ApiResponse.java, PageResponse.java
│       ├── exception/    WalletException.java, ErrorCode.java
│       └── util/         MoneyUtils.java, UuidUtils.java
│
├── user-service/
│   └── src/
│       ├── main/java/com/wallet/user/
│       │   ├── controller/   AuthController.java, UserController.java
│       │   ├── service/      UserService.java, OtpService.java, JwtService.java
│       │   ├── repository/   UserRepository.java, OtpRepository.java
│       │   ├── domain/       User.java, OtpVerification.java   (records)
│       │   ├── dto/          RegisterRequest.java, LoginRequest.java, TokenResponse.java
│       │   └── config/       SecurityConfig.java, JwtConfig.java
│       └── test/java/com/wallet/user/
│           ├── UserServiceTest.java
│           ├── OtpServiceTest.java
│           └── AuthControllerTest.java
│
├── wallet-service/
│   └── src/
│       ├── main/java/com/wallet/wallet/
│       │   ├── controller/   WalletController.java
│       │   ├── service/      WalletService.java
│       │   ├── repository/   WalletRepository.java
│       │   ├── domain/       Wallet.java
│       │   └── dto/          WalletResponse.java, TopUpRequest.java
│       └── test/
│
├── transfer-service/
│   └── src/
│       ├── main/java/com/wallet/transfer/
│       │   ├── controller/   TransferController.java
│       │   ├── service/      TransferService.java, OutboxPollerService.java
│       │   ├── repository/   TransactionRepository.java, AuditRepository.java
│       │   │                 NotificationOutboxRepository.java
│       │   ├── domain/       Transaction.java, AuditLog.java
│       │   ├── dto/          TransferRequest.java, TransferResponse.java
│       │   └── event/        TransferCompletedEvent.java
│       └── test/
│           ├── TransferServiceTest.java
│           ├── TransferConcurrencyTest.java
│           └── TransferControllerTest.java
│
└── notification-service/
    └── src/
        ├── main/java/com/wallet/notification/
        │   ├── consumer/     TransferEventConsumer.java
        │   ├── service/      FcmService.java, SmsService.java
        │   └── config/       KafkaConfig.java, FirebaseConfig.java
        └── test/
```

### Flutter

```
wallet-flutter/
├── pubspec.yaml
├── lib/
│   ├── main.dart
│   ├── app.dart                        # GoRouter setup, providers
│   ├── core/
│   │   ├── api/        dio_client.dart, api_endpoints.dart
│   │   ├── error/      app_exception.dart, error_handler.dart
│   │   ├── storage/    secure_storage.dart
│   │   └── utils/      money_formatter.dart, validators.dart
│   ├── features/
│   │   ├── auth/
│   │   │   ├── data/       auth_repository.dart, auth_remote_source.dart
│   │   │   ├── domain/     user_model.dart
│   │   │   └── presentation/
│   │   │       ├── screens/   phone_screen.dart, otp_screen.dart,
│   │   │       │              pin_setup_screen.dart, login_screen.dart
│   │   │       └── providers/ auth_provider.dart
│   │   ├── wallet/
│   │   │   ├── data/       wallet_repository.dart
│   │   │   └── presentation/
│   │   │       ├── screens/   wallet_home_screen.dart, topup_screen.dart
│   │   │       └── providers/ wallet_provider.dart
│   │   ├── transfer/
│   │   │   ├── data/       transfer_repository.dart
│   │   │   └── presentation/
│   │   │       ├── screens/   send_screen.dart, confirm_screen.dart,
│   │   │       │              success_screen.dart
│   │   │       └── providers/ transfer_provider.dart
│   │   └── history/
│   │       ├── data/       transaction_repository.dart
│   │       └── presentation/
│   │           ├── screens/   history_screen.dart, detail_screen.dart
│   │           └── providers/ history_provider.dart
│   └── shared/
│       ├── widgets/    amount_input.dart, phone_input.dart,
│       │               transaction_tile.dart, loading_button.dart
│       └── theme/      app_theme.dart, colors.dart, text_styles.dart
└── test/
    ├── unit/
    │   ├── transfer_provider_test.dart
    │   └── money_formatter_test.dart
    └── widget/
        ├── send_screen_test.dart
        └── wallet_home_test.dart
```

---

## Quick-start commands

```bash
# Start infrastructure
docker-compose up -d

# Run migrations (Flyway)
./gradlew :user-service:flywayMigrate

# Start all services
./gradlew :user-service:bootRun &
./gradlew :wallet-service:bootRun &
./gradlew :transfer-service:bootRun &
./gradlew :notification-service:bootRun &

# Run all tests
./gradlew test

# Flutter
cd wallet-flutter
flutter pub get
flutter run
```

---

_Last updated: 2026-09-26 — Phase 1 MVP planning_

# STAND_ALONE_APP

# Pockt — Standalone Server Planner

> Single deployable Spring Boot JAR · Modular package architecture · PostgreSQL + Redis
> Runs as `java -jar pockt.jar` locally · Docker container in production
> Backend for the Pockt Flutter mobile wallet app

---

## Table of Contents

1. [Architecture Philosophy](#1-architecture-philosophy)
2. [Tech Stack](#2-tech-stack)
3. [Module Structure](#3-module-structure)
4. [Package Architecture](#4-package-architecture)
5. [Database Schema](#5-database-schema)
6. [Internal Module Contracts](#6-internal-module-contracts)
7. [API Reference](#7-api-reference)
8. [Configuration & Profiles](#8-configuration--profiles)
9. [Code Principles](#9-code-principles)
10. [Test Strategy](#10-test-strategy)
11. [Docker & Deployment](#11-docker--deployment)
12. [Phase Plan](#12-phase-plan)
13. [Folder Structure](#13-folder-structure)
14. [Quick Start](#14-quick-start)

---

## 1. Architecture Philosophy

### Why a modular monolith, not microservices?

Microservices solve scale problems you don't have yet and introduce operational problems you don't need. A modular monolith gives you:

| Concern                         | Microservices                           | Modular Monolith                    |
| ------------------------------- | --------------------------------------- | ----------------------------------- |
| Deployment                      | 4+ JARs, 4+ ports, service discovery    | 1 JAR, 1 port                       |
| Database transactions           | Distributed sagas, eventual consistency | Simple `@Transactional`             |
| Local development               | Docker Compose with 6 containers        | `java -jar` + Postgres + Redis      |
| Debugging                       | Trace IDs across services               | Single log stream                   |
| Refactor to microservices later | Hard — already coupled by network       | Easy — modules have clear contracts |

### The golden rule

**Modules talk to each other through interfaces only — never through direct class imports across module boundaries.**

Each module exposes a public `*Service` interface and its DTOs. Other modules depend on the interface, not the implementation. This is enforced by package structure, not by the build tool (though you can enforce it with ArchUnit tests).

### How this maps to the previous planner

The previous planner had 4 Spring Boot services. This planner collapses them into 4 packages inside one server:

```
user-service      →   com.pockt.user
wallet-service    →   com.pockt.wallet
transfer-service  →   com.pockt.transfer
notification-service → com.pockt.notification
```

Everything else — API gateway logic, JWT filter, rate limiting, exception handling — becomes cross-cutting infrastructure inside the same JAR.

---

## 2. Tech Stack

### Backend (single JAR)

| Layer              | Technology                                                 | Version  |
| ------------------ | ---------------------------------------------------------- | -------- |
| Language           | Java                                                       | 21 (LTS) |
| Framework          | Spring Boot                                                | 3.3.x    |
| Data access        | Spring JDBC (`JdbcTemplate`, `NamedParameterJdbcTemplate`) | —        |
| Connection pool    | HikariCP                                                   | bundled  |
| Migrations         | Flyway                                                     | 10.x     |
| Auth               | Spring Security 6 + jjwt                                   | 0.12.x   |
| Resilience         | Resilience4j                                               | 2.x      |
| Cache              | Spring Data Redis (Lettuce)                                | —        |
| Events             | Spring `ApplicationEventPublisher`                         | —        |
| Push notifications | Firebase Admin SDK                                         | 9.x      |
| Validation         | Jakarta Bean Validation 3                                  | —        |
| API docs           | SpringDoc OpenAPI 3                                        | 2.x      |
| Build              | Gradle 8 (Kotlin DSL)                                      | —        |
| Test               | JUnit 5 + Mockito + Testcontainers                         | —        |
| Arch tests         | ArchUnit                                                   | 1.x      |

### Infrastructure (Docker Compose)

| Service            | Image                | Purpose                          |
| ------------------ | -------------------- | -------------------------------- |
| PostgreSQL         | `postgres:16-alpine` | Primary database                 |
| Redis              | `redis:7-alpine`     | Sessions, rate limiting, OTP TTL |
| (Optional) Mailhog | `mailhog/mailhog`    | Catch-all SMTP in dev            |

No Kafka in MVP. Notifications go through an in-process Spring event + database outbox. Kafka is a phase-2 upgrade when volume justifies it.

---

## 3. Module Structure

```
com.pockt
├── PocktApplication.java          ← @SpringBootApplication entry point
│
├── infrastructure/                ← cross-cutting, no business logic
│   ├── config/                    ApplicationConfig, JacksonConfig
│   ├── security/                  JwtFilter, SecurityConfig, JwtService
│   ├── web/                       GlobalExceptionHandler, ApiResponse, RequestIdFilter
│   ├── ratelimit/                 RateLimitService (Redis token bucket)
│   ├── persistence/               BaseRepository (shared JDBC helpers)
│   └── scheduler/                 OutboxPoller, CleanupScheduler
│
├── user/                          ← USER MODULE
│   ├── api/                       AuthController, UserController
│   ├── service/                   UserService, OtpService          ← public interfaces here
│   ├── internal/                  UserServiceImpl, OtpServiceImpl  ← implementations
│   ├── repository/                UserRepository, OtpRepository
│   ├── domain/                    User (record), OtpVerification (record)
│   └── dto/                       RegisterRequest, LoginRequest, TokenResponse, UserResponse
│
├── wallet/                        ← WALLET MODULE
│   ├── api/                       WalletController
│   ├── service/                   WalletService                    ← public interface
│   ├── internal/                  WalletServiceImpl
│   ├── repository/                WalletRepository
│   ├── domain/                    Wallet (record)
│   └── dto/                       WalletResponse, TopUpRequest
│
├── transfer/                      ← TRANSFER MODULE
│   ├── api/                       TransferController
│   ├── service/                   TransferService                  ← public interface
│   ├── internal/                  TransferServiceImpl, SagaCoordinator
│   ├── repository/                TransactionRepository, AuditRepository, OutboxRepository
│   ├── domain/                    Transaction (record), AuditLog (record)
│   ├── event/                     TransferCompletedEvent
│   └── dto/                       TransferRequest, TransferResponse, TransactionResponse
│
└── notification/                  ← NOTIFICATION MODULE
    ├── service/                   NotificationService              ← public interface
    ├── internal/                  NotificationServiceImpl
    ├── provider/                  FcmProvider, SmsProvider (interface + impls)
    ├── listener/                  TransferEventListener
    └── dto/                       PushPayload
```

### Module dependency rules

```
infrastructure  ←  (no module dependencies; only Spring + libs)
user            ←  infrastructure
wallet          ←  infrastructure, user.service (interface only)
transfer        ←  infrastructure, user.service, wallet.service (interfaces only)
notification    ←  infrastructure, user.service (interface only)
```

Visualised as a directed acyclic graph — no cycles, ever:

```
infrastructure
      ↑
    user ──────────────────────┐
      ↑                        │
   wallet ←── transfer         │
                  ↑            │
            notification ──────┘
```

---

## 4. Package Architecture

### Infrastructure layer (cross-cutting)

#### `JwtService`

- Generates RS256 access tokens (15 min TTL)
- Generates opaque refresh tokens (stored in Redis, 30-day TTL)
- Validates and parses tokens
- Private key loaded from `classpath:keys/private.pem` (env-overridable)

#### `JwtFilter`

- `OncePerRequestFilter` placed before Spring Security
- Extracts Bearer token → validates → sets `SecurityContextHolder`
- Skips public routes (`/api/v1/auth/**`, `/actuator/health`, `/v3/api-docs/**`)

#### `RateLimitService`

- Redis-backed sliding window counter per `userId` + `endpoint`
- Default limits: transfer → 10/min, OTP → 3/10min, login → 5/min
- Returns `RateLimitResult(allowed, retryAfterSeconds)`

#### `GlobalExceptionHandler`

- `@RestControllerAdvice` catches all exceptions
- Maps domain exceptions to HTTP status + error code
- Every error response includes `requestId` from `MDC`

#### `RequestIdFilter`

- Generates `UUID` per request, puts in `MDC` as `requestId`
- Adds `X-Request-Id` response header

#### `OutboxPoller`

- `@Scheduled(fixedDelay = 5000)` — runs every 5 seconds
- Queries `notification_outbox WHERE status = 'PENDING'` using `SKIP LOCKED`
- Calls `NotificationService` per row
- Marks row `DELIVERED` or schedules retry with exponential backoff

---

### User module

#### `UserService` (public interface)

```java
public interface UserService {
    void sendOtp(String phone, OtpPurpose purpose);
    String verifyOtp(String phone, String otp, OtpPurpose purpose); // returns temp token
    UserResponse register(RegisterRequest request, String tempToken);
    TokenResponse login(LoginRequest request);
    TokenResponse refreshToken(String refreshToken);
    void logout(String refreshToken);
    void registerFcmToken(UUID userId, String fcmToken);
    UserResponse getProfile(UUID userId);
    Optional<UserResponse> findByPhone(String phone);
}
```

#### `OtpService` (public interface)

```java
public interface OtpService {
    void send(String phone, OtpPurpose purpose);        // generate, hash, store, dispatch
    boolean verify(String phone, String otp, OtpPurpose purpose);
}
```

#### OTP flow detail

1. Generate 6-digit random OTP
2. BCrypt hash → store in `otp_verifications` with 5-min TTL
3. In `dev` profile → log to console
4. In `prod` profile → send via SMS provider (Twilio/Africa's Talking)
5. On verify → load latest unused OTP for phone+purpose, BCrypt match, mark used
6. After 3 failed attempts → lock phone for 10 minutes (Redis key)

---

### Wallet module

#### `WalletService` (public interface)

```java
public interface WalletService {
    WalletResponse createWallet(UUID userId, String currency);
    WalletResponse getWallet(UUID walletId, UUID requestingUserId);
    List<WalletResponse> getUserWallets(UUID userId);
    WalletResponse topUp(UUID walletId, long amountCents, UUID requestingUserId); // MVP only
    Wallet getWalletForUpdate(UUID walletId);          // internal use by transfer module
    void debit(UUID walletId, long amountCents);
    void credit(UUID walletId, long amountCents);
}
```

#### Balance locking — how it works

```sql
-- WalletRepository.findByIdForUpdate()
SELECT id, user_id, currency, balance, is_active
FROM wallets
WHERE id = :walletId
FOR UPDATE;
-- PostgreSQL holds a row-level lock until the transaction commits or rolls back.
-- Any other transaction trying to touch the same wallet row will wait.
```

---

### Transfer module

#### `TransferService` (public interface)

```java
public interface TransferService {
    TransferResponse send(TransferRequest request, UUID senderUserId);
    PageResponse<TransactionResponse> getHistory(UUID userId, String cursor, int limit);
    TransactionResponse getById(UUID transactionId, UUID requestingUserId);
}
```

#### Saga — step by step inside one `@Transactional`

```
1. Validate sender ≠ receiver
2. Check idempotency key → return cached if exists
3. Resolve receiver phone → user → wallet
4. Lock wallets (lower UUID first to prevent deadlock)
5. Assert sender.balance >= amount
6. Debit sender wallet
7. Credit receiver wallet
8. INSERT transaction (status=COMPLETED)
9. INSERT audit_log rows (DEBITED, CREDITED)
10. INSERT notification_outbox rows (TRANSFER_SENT, TRANSFER_RECEIVED)
11. COMMIT
--- after commit ---
12. Publish TransferCompletedEvent (Spring in-process event)
13. OutboxPoller picks up rows → sends FCM push
```

Steps 1–11 are atomic. If anything in steps 1–10 throws, the whole transaction rolls back. No money moves, no audit record, nothing. Steps 12–13 are fire-and-forget; the outbox guarantees delivery even if the app crashes between step 11 and 13.

---

### Notification module

#### `NotificationService` (public interface)

```java
public interface NotificationService {
    void sendTransferSent(UUID senderUserId, long amountCents, String currency, String receiverName);
    void sendTransferReceived(UUID receiverUserId, long amountCents, String currency, String senderName);
    void sendOtp(String phone, String otp);
    void processOutboxEntry(OutboxEntry entry);
}
```

#### Provider abstraction

```java
// Swap providers without touching business logic
public interface PushProvider {
    void send(String fcmToken, String title, String body, Map<String, String> data);
}

// Implementations:
// FcmPushProvider   — Firebase Admin SDK (prod profile)
// LogPushProvider   — prints to console (dev profile)

public interface SmsProvider {
    void send(String phone, String message);
}

// Implementations:
// TwilioSmsProvider       — Twilio REST API (prod profile)
// AfricasTalkingSmsProvider — Africa's Talking (alt provider)
// LogSmsProvider          — prints to console (dev profile)
```

---

## 5. Database Schema

All migrations live in `src/main/resources/db/migration/` as Flyway versioned scripts.

### V1\_\_create_users.sql

```sql
CREATE TABLE users (
    id          UUID         PRIMARY KEY,
    phone       VARCHAR(20)  NOT NULL UNIQUE,
    full_name   VARCHAR(120) NOT NULL,
    pin_hash    VARCHAR(255) NOT NULL,
    fcm_token   VARCHAR(512),
    kyc_status  VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_phone ON users (phone);
```

### V2\_\_create_otp_verifications.sql

```sql
CREATE TABLE otp_verifications (
    id          UUID         PRIMARY KEY,
    phone       VARCHAR(20)  NOT NULL,
    otp_hash    VARCHAR(255) NOT NULL,
    purpose     VARCHAR(30)  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    used        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_otp_phone_purpose ON otp_verifications (phone, purpose, used);
```

### V3\_\_create_wallets.sql

```sql
CREATE TABLE wallets (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users(id),
    currency    CHAR(3)     NOT NULL DEFAULT 'USD',
    balance     BIGINT      NOT NULL DEFAULT 0 CHECK (balance >= 0),
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, currency)
);

CREATE INDEX idx_wallets_user_id ON wallets (user_id);
```

### V4\_\_create_transactions.sql

```sql
CREATE TABLE transactions (
    id                  UUID         PRIMARY KEY,
    idempotency_key     VARCHAR(64)  NOT NULL UNIQUE,
    sender_wallet_id    UUID         NOT NULL REFERENCES wallets(id),
    receiver_wallet_id  UUID         NOT NULL REFERENCES wallets(id),
    amount              BIGINT       NOT NULL CHECK (amount > 0),
    currency            CHAR(3)      NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    description         VARCHAR(255),
    failure_reason      VARCHAR(500),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tx_sender   ON transactions (sender_wallet_id,   created_at DESC);
CREATE INDEX idx_tx_receiver ON transactions (receiver_wallet_id, created_at DESC);
```

### V5\_\_create_audit_log.sql

```sql
CREATE TABLE audit_log (
    id           UUID        PRIMARY KEY,
    entity_type  VARCHAR(50) NOT NULL,
    entity_id    UUID        NOT NULL,
    action       VARCHAR(50) NOT NULL,
    actor_id     UUID,
    old_value    JSONB,
    new_value    JSONB,
    ip_address   INET,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Audit log is INSERT-only. Enforce at DB level:
REVOKE UPDATE, DELETE ON audit_log FROM pockt_app;

CREATE INDEX idx_audit_entity ON audit_log (entity_type, entity_id, created_at DESC);
```

### V6\_\_create_notification_outbox.sql

```sql
CREATE TABLE notification_outbox (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users(id),
    type        VARCHAR(50) NOT NULL,
    payload     JSONB       NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts    INT         NOT NULL DEFAULT 0,
    next_retry  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_outbox_pending ON notification_outbox (status, next_retry)
    WHERE status = 'PENDING';
```

---

## 6. Internal Module Contracts

Modules never call each other's repositories directly. They call each other's public service interfaces. This table defines exactly what is allowed:

| Caller                  | Callee                | Allowed calls                           |
| ----------------------- | --------------------- | --------------------------------------- |
| `transfer.internal`     | `WalletService`       | `getWalletForUpdate`, `debit`, `credit` |
| `transfer.internal`     | `UserService`         | `findByPhone`                           |
| `wallet.internal`       | `UserService`         | `getProfile` (for authorization check)  |
| `notification.listener` | `UserService`         | `getProfile` (to get FCM token)         |
| `notification.listener` | `NotificationService` | `processOutboxEntry`                    |

All of this is validated by ArchUnit tests (see section 10).

---

## 7. API Reference

### Base URL

```
Local:  http://localhost:8080/api/v1
Docker: http://<host>:8080/api/v1
```

### Response envelope

```json
{
  "success": true,
  "data": {},
  "error": null,
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-09-26T10:00:00Z"
}
```

### Error response

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "INSUFFICIENT_BALANCE",
    "message": "Your wallet balance is too low for this transfer.",
    "field": null
  },
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-09-26T10:00:00Z"
}
```

---

### Auth endpoints (public — no JWT required)

#### `POST /auth/otp/send`

```json
// Request
{ "phone": "+2348012345678", "purpose": "REGISTRATION" }

// Response 200
{ "success": true, "data": { "expiresInSeconds": 300 } }
```

#### `POST /auth/otp/verify`

```json
// Request
{ "phone": "+2348012345678", "otp": "847291", "purpose": "REGISTRATION" }

// Response 200
{ "success": true, "data": { "tempToken": "<short-lived JWT, 10 min>" } }
```

#### `POST /auth/register`

```json
// Request — Authorization: Bearer <tempToken>
{ "fullName": "Ade Bello", "pin": "123456" }

// Response 201
{
  "data": {
    "userId": "uuid",
    "accessToken": "...",
    "refreshToken": "...",
    "expiresIn": 900
  }
}
```

#### `POST /auth/login`

```json
// Request
{ "phone": "+2348012345678", "pin": "123456" }

// Response 200
{
  "data": {
    "userId": "uuid",
    "accessToken": "...",
    "refreshToken": "...",
    "expiresIn": 900
  }
}
```

#### `POST /auth/refresh`

```json
// Request
{ "refreshToken": "opaque-token" }

// Response 200 — new token pair, old refresh token invalidated
```

#### `POST /auth/logout`

```json
// Request — Authorization: Bearer <accessToken>
{ "refreshToken": "opaque-token" }
// Response 204 No Content
```

---

### User endpoints (JWT required)

#### `GET /users/me`

```json
{
  "data": {
    "id": "uuid",
    "phone": "+2348012345678",
    "fullName": "Ade Bello",
    "kycStatus": "VERIFIED",
    "createdAt": "2026-09-26T10:00:00Z"
  }
}
```

#### `GET /users/search?phone=+2348099999999`

```json
{
  "data": {
    "id": "uuid",
    "fullName": "Kemi Lagos",
    "phone": "+2348099999999"
  }
}
// Used in Flutter send screen to preview recipient before confirming
```

#### `PATCH /users/me`

```json
// Request
{ "fullName": "Ade B." }
// Response 200 — updated UserResponse
```

#### `POST /users/me/fcm-token`

```json
// Request
{ "fcmToken": "firebase-device-token" }
// Response 204 No Content
```

---

### Wallet endpoints (JWT required)

#### `GET /wallets`

```json
{
  "data": [
    {
      "id": "uuid",
      "currency": "USD",
      "balance": 45000,
      "formattedBalance": "$450.00"
    }
  ]
}
```

#### `GET /wallets/{id}`

```json
{
  "data": {
    "id": "uuid",
    "currency": "USD",
    "balance": 45000,
    "formattedBalance": "$450.00",
    "isActive": true,
    "createdAt": "2026-09-26T10:00:00Z"
  }
}
```

#### `POST /wallets/{id}/topup` _(MVP only)_

```json
// Request
{ "amount": 10000 } // $100.00 in cents
// Response 200 — updated WalletResponse
```

---

### Transfer endpoints (JWT required)

#### `POST /transfers`

```
Headers:
  Authorization: Bearer <token>
  X-Idempotency-Key: <client-generated-uuid>
```

```json
// Request
{
  "receiverPhone": "+2348099999999",
  "amount": 5000,
  "currency": "USD",
  "description": "Lunch split"
}

// Response 200
{
  "data": {
    "transactionId": "uuid",
    "status": "COMPLETED",
    "amount": 5000,
    "currency": "USD",
    "formattedAmount": "$50.00",
    "receiverName": "Kemi Lagos",
    "senderBalanceAfter": 40000,
    "createdAt": "2026-09-26T10:00:00Z"
  }
}
```

#### `GET /transfers?cursor=&limit=20`

```json
{
  "data": {
    "items": [
      {
        "id": "uuid",
        "type": "DEBIT",
        "amount": 5000,
        "currency": "USD",
        "formattedAmount": "-$50.00",
        "counterpartyName": "Kemi Lagos",
        "description": "Lunch split",
        "status": "COMPLETED",
        "createdAt": "2026-09-26T10:00:00Z"
      }
    ],
    "nextCursor": "2026-09-25T10:00:00Z_uuid",
    "hasMore": true
  }
}
```

#### `GET /transfers/{id}`

Full transaction detail including both wallet states at time of transfer.

---

### Error codes

| Code                       | HTTP | When                              |
| -------------------------- | ---- | --------------------------------- |
| `INVALID_OTP`              | 400  | OTP is wrong                      |
| `OTP_EXPIRED`              | 400  | OTP TTL passed                    |
| `OTP_MAX_ATTEMPTS`         | 429  | 3 failed attempts                 |
| `PHONE_ALREADY_REGISTERED` | 409  | Registration with existing phone  |
| `USER_NOT_FOUND`           | 404  | Phone not registered              |
| `RECEIVER_NOT_FOUND`       | 404  | Receiver phone not registered     |
| `WALLET_NOT_FOUND`         | 404  | Wallet ID doesn't exist           |
| `WALLET_INACTIVE`          | 422  | Wallet has been disabled          |
| `INSUFFICIENT_BALANCE`     | 422  | Balance too low                   |
| `SELF_TRANSFER`            | 422  | Sending to yourself               |
| `DUPLICATE_TRANSFER`       | 409  | Idempotency key already used      |
| `INVALID_AMOUNT`           | 400  | Amount ≤ 0                        |
| `RATE_LIMIT_EXCEEDED`      | 429  | Too many requests                 |
| `TOKEN_EXPIRED`            | 401  | JWT expired                       |
| `TOKEN_INVALID`            | 401  | JWT malformed or tampered         |
| `FORBIDDEN`                | 403  | Accessing another user's resource |
| `INTERNAL_ERROR`           | 500  | Unexpected server error           |

---

## 8. Configuration & Profiles

### Spring profiles

| Profile | When              | Database       | Redis       | Push           | SMS            |
| ------- | ----------------- | -------------- | ----------- | -------------- | -------------- |
| `dev`   | Local development | Local Postgres | Local Redis | Log to console | Log to console |
| `test`  | Running tests     | Testcontainers | Embedded    | Mock           | Mock           |
| `prod`  | Docker / server   | Prod Postgres  | Prod Redis  | Firebase FCM   | Twilio         |

### `application.yml` (base)

```yaml
spring:
  application:
    name: pockt
  threads:
    virtual:
      enabled: true # Java 21 virtual threads — one line, huge win

  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/pockt}
    username: ${DB_USER:pockt}
    password: ${DB_PASSWORD:pockt}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 5000
      idle-timeout: 300000
      max-lifetime: 1200000

  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}

  flyway:
    enabled: true
    locations: classpath:db/migration

server:
  port: ${PORT:8080}
  error:
    include-message: never # never leak internal errors to clients
    include-stacktrace: never

management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus
  endpoint:
    health:
      show-details: when-authorized

pockt:
  jwt:
    private-key-path: ${JWT_PRIVATE_KEY_PATH:classpath:keys/private.pem}
    public-key-path: ${JWT_PUBLIC_KEY_PATH:classpath:keys/public.pem}
    access-token-ttl-seconds: 900 # 15 minutes
    refresh-token-ttl-seconds: 2592000 # 30 days
  otp:
    ttl-seconds: 300 # 5 minutes
    max-attempts: 3
    lockout-seconds: 600 # 10 minutes
  rate-limit:
    transfer-per-minute: 10
    login-per-minute: 5
    otp-per-10-minutes: 3
```

### `application-dev.yml`

```yaml
logging:
  level:
    com.pockt: DEBUG
    org.springframework.jdbc: DEBUG # shows every SQL query

pockt:
  notification:
    push-provider: log # prints to console
    sms-provider: log
```

### `application-prod.yml`

```yaml
logging:
  level:
    com.pockt: INFO
  pattern:
    console: '{"timestamp":"%d","level":"%p","logger":"%c","msg":"%m","requestId":"%X{requestId}"}%n'

pockt:
  notification:
    push-provider: fcm
    sms-provider: twilio
  firebase:
    credentials-path: ${FIREBASE_CREDENTIALS_PATH}
  twilio:
    account-sid: ${TWILIO_ACCOUNT_SID}
    auth-token: ${TWILIO_AUTH_TOKEN}
    from-number: ${TWILIO_FROM_NUMBER}
```

### Environment variables (`.env` for Docker)

```env
DB_URL=jdbc:postgresql://postgres:5432/pockt
DB_USER=pockt
DB_PASSWORD=supersecret

REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=

JWT_PRIVATE_KEY_PATH=/run/secrets/jwt_private.pem
JWT_PUBLIC_KEY_PATH=/run/secrets/jwt_public.pem

FIREBASE_CREDENTIALS_PATH=/run/secrets/firebase.json

TWILIO_ACCOUNT_SID=ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
TWILIO_AUTH_TOKEN=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
TWILIO_FROM_NUMBER=+15005550006

SPRING_PROFILES_ACTIVE=prod
PORT=8080
```

---

## 9. Code Principles

### Money is always `long` (cents)

```java
// CORRECT — store and compute in cents
long amountCents = 5000;   // = $50.00

// Format only for display
String display = MoneyUtils.format(amountCents, "USD"); // → "$50.00"

// WRONG — never do this
double amount = 50.00;       // floating point will bite you
BigDecimal bd = new BigDecimal("50.00"); // OK for display, never for storage
```

### Records for domain objects and DTOs

```java
// Immutable, no boilerplate, no accidental mutation
public record Wallet(
    UUID id,
    UUID userId,
    String currency,
    long balance,
    boolean isActive,
    Instant createdAt
) {}

public record TransferRequest(
    @NotBlank String receiverPhone,
    @Positive long amount,
    @Size(min = 3, max = 3) String currency,
    @Size(max = 255) String description
) {}
```

### Explicit SQL — no surprises

```java
// Every query is visible, reviewable, and tunable
@Repository
public class WalletRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public Wallet findByIdForUpdate(UUID walletId) {
        String sql = """
            SELECT id, user_id, currency, balance, is_active, created_at
            FROM wallets
            WHERE id = :walletId AND is_active = TRUE
            FOR UPDATE
            """;
        return jdbc.queryForObject(sql, Map.of("walletId", walletId), WALLET_MAPPER);
    }

    public void debit(UUID walletId, long amountCents) {
        String sql = """
            UPDATE wallets
            SET balance = balance - :amount, updated_at = NOW()
            WHERE id = :walletId
            """;
        int rows = jdbc.update(sql, Map.of("walletId", walletId, "amount", amountCents));
        if (rows == 0) throw new WalletNotFoundException(walletId);
        // balance CHECK constraint handles negative balance at DB level
    }
}
```

### Deadlock prevention — always lock in UUID order

```java
@Transactional(isolation = Isolation.READ_COMMITTED)
public TransferResponse send(TransferRequest req, UUID senderUserId) {
    // Always acquire locks in consistent order (lower UUID first)
    // to prevent two concurrent transfers deadlocking each other
    Wallet senderWallet   = getSenderWallet(senderUserId, req.currency());
    UUID receiverWalletId = resolveReceiverWallet(req.receiverPhone(), req.currency());

    UUID first  = senderWallet.id().compareTo(receiverWalletId) < 0
                    ? senderWallet.id() : receiverWalletId;
    UUID second = senderWallet.id().compareTo(receiverWalletId) < 0
                    ? receiverWalletId : senderWallet.id();

    Wallet lockedFirst  = walletRepository.findByIdForUpdate(first);
    Wallet lockedSecond = walletRepository.findByIdForUpdate(second);

    Wallet sender   = lockedFirst.id().equals(senderWallet.id()) ? lockedFirst : lockedSecond;
    Wallet receiver = lockedFirst.id().equals(senderWallet.id()) ? lockedSecond : lockedFirst;
    // ...
}
```

### Custom exception hierarchy

```java
// Base — all domain exceptions extend this
public abstract class PocktException extends RuntimeException {
    private final ErrorCode code;
    private final HttpStatus status;
    // constructor, getters
}

// Concrete
public class InsufficientBalanceException extends PocktException {
    public InsufficientBalanceException() {
        super(ErrorCode.INSUFFICIENT_BALANCE, HttpStatus.UNPROCESSABLE_ENTITY,
              "Your wallet balance is too low for this transfer.");
    }
}
```

### Pagination — cursor-based (not offset)

```java
// Offset pagination breaks on high-volume tables (OFFSET 10000 scans 10000 rows)
// Cursor pagination is O(log n) using an index

// Cursor = "createdAt_uuid" encoded as base64
// Query:
String sql = """
    SELECT * FROM transactions
    WHERE (sender_wallet_id = :walletId OR receiver_wallet_id = :walletId)
      AND (created_at, id) < (:cursorTime, :cursorId)
    ORDER BY created_at DESC, id DESC
    LIMIT :limit
    """;
```

---

## 10. Test Strategy

### Testing pyramid

```
           ┌───────────────────┐
           │    E2E / Manual    │  5%
           └────────┬──────────┘
          ┌─────────┴──────────┐
          │  Integration tests  │  25%  ← Testcontainers + @SpringBootTest
          └─────────┬───────────┘
         ┌──────────┴───────────┐
         │     Unit tests        │  60%  ← JUnit 5 + Mockito
         └──────────────────────┘
        ┌───────────────────────┐
        │  Architecture tests   │  10%  ← ArchUnit
        └───────────────────────┘
```

### Unit tests — service layer

```java
// Key test cases for TransferServiceImpl

// ✅ Happy path — valid transfer succeeds
void transfer_happyPath_debitsAndCredits()

// ✅ Self-transfer rejected before any DB call
void transfer_selfTransfer_throwsSelfTransferException()

// ✅ Insufficient balance rejected atomically
void transfer_insufficientBalance_throwsException_noDebitOccurs()

// ✅ Idempotency — duplicate key returns cached result
void transfer_duplicateIdempotencyKey_returnsCachedResult()

// ✅ Receiver not found
void transfer_receiverPhoneNotFound_throwsReceiverNotFoundException()

// ✅ Amount zero rejected at validation
void transfer_zeroAmount_failsBeanValidation()

// ✅ Negative amount rejected
void transfer_negativeAmount_failsBeanValidation()

// ✅ Outbox entry created within same transaction
void transfer_success_insertsOutboxEntry()
```

### Integration tests — Testcontainers

```java
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class TransferIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis =
        new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    // Test cases:
    // ✅ Full registration → wallet creation → top-up → transfer flow
    // ✅ Balance is correct after transfer
    // ✅ Outbox row exists after transfer
    // ✅ Concurrent transfers do not overdraft
    // ✅ Idempotency key deduplication at DB level
}
```

### Concurrency test — the most important test

```java
@Test
void concurrentTransfers_neverOverdraft() throws Exception {
    UUID senderWalletId = createAndFundWallet(10000L); // $100
    UUID receiverWalletId = createEmptyWallet();
    int threadCount = 20;
    long each = 1000L; // $10 each — only 10 can succeed

    var latch = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(threadCount);
    var results = new ConcurrentLinkedQueue<Boolean>();

    for (int i = 0; i < threadCount; i++) {
        String key = "key-" + i;
        executor.submit(() -> {
            latch.await();
            try {
                transferService.send(new TransferRequest(
                    receiverPhone, each, "USD", "test", key), senderUserId);
                results.add(true);
            } catch (InsufficientBalanceException e) {
                results.add(false);
            }
            return null;
        });
    }

    latch.countDown(); // fire all threads at once
    executor.awaitTermination(15, TimeUnit.SECONDS);

    long succeeded = results.stream().filter(r -> r).count();
    long finalBalance = walletRepository.getBalance(senderWalletId);

    assertThat(succeeded).isEqualTo(10);          // exactly 10 transferred
    assertThat(finalBalance).isEqualTo(0L);       // drained to zero, not negative
    assertThat(finalBalance).isGreaterThanOrEqualTo(0L); // never negative
}
```

### Architecture tests — ArchUnit

```java
@AnalyzeClasses(packages = "com.pockt")
class ArchitectureTest {

    @ArchTest
    static final ArchRule no_module_cross_imports =
        noClasses()
            .that().resideInAPackage("com.pockt.transfer..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.pockt.wallet.internal..");

    @ArchTest
    static final ArchRule repositories_only_in_repository_packages =
        classes()
            .that().haveNameMatching(".*Repository")
            .should().resideInAPackage("..repository..");

    @ArchTest
    static final ArchRule no_jdbc_in_service_layer =
        noClasses()
            .that().resideInAPackage("..service..")
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate");

    @ArchTest
    static final ArchRule controllers_only_depend_on_service_interfaces =
        classes()
            .that().resideInAPackage("..api..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("..api..", "..service..", "..dto..",
                                "com.pockt.infrastructure..", "java..", "jakarta..",
                                "org.springframework..");
}
```

### Flutter tests

```dart
// Unit test — transfer state
group('TransferNotifier', () {
  test('emits TransferSuccess on valid send', () async { ... });
  test('emits TransferError on insufficient balance', () async { ... });
  test('emits TransferError on network failure', () async { ... });
  test('clears state on reset', () async { ... });
});

// Widget test — send screen
group('SendScreen', () {
  testWidgets('shows recipient name after phone lookup', ...);
  testWidgets('disables confirm button when amount is zero', ...);
  testWidgets('shows loading indicator during transfer', ...);
  testWidgets('navigates to success screen on completion', ...);
});
```

---

## 11. Docker & Deployment

### `docker-compose.yml` (development + CI)

```yaml
version: "3.9"

services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: pockt
      POSTGRES_USER: pockt
      POSTGRES_PASSWORD: pockt
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U pockt"]
      interval: 5s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      retries: 3

  pockt:
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_URL: jdbc:postgresql://postgres:5432/pockt
      DB_USER: pockt
      DB_PASSWORD: pockt
      REDIS_HOST: redis
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 10s
      retries: 5

volumes:
  postgres_data:
```

### `Dockerfile` (multi-stage)

```dockerfile
# ── Stage 1: build ──────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN ./gradlew dependencies --no-daemon --quiet

COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# ── Stage 2: run ─────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

RUN addgroup -S pockt && adduser -S pockt -G pockt
USER pockt

COPY --from=builder /app/build/libs/pockt-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseZGC", \
  "-XX:+ZGenerational", \
  "-Xmx512m", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
```

### Build and run commands

```bash
# Build the JAR locally
./gradlew bootJar

# Run locally (dev profile)
java -jar build/libs/pockt-0.1.0.jar --spring.profiles.active=dev

# Build Docker image
docker build -t pockt:latest .

# Run with Docker Compose (full stack)
docker-compose up --build

# Run tests
./gradlew test

# Run only architecture tests
./gradlew test --tests "com.pockt.ArchitectureTest"

# View API docs
open http://localhost:8080/swagger-ui.html
```

### GitHub Actions CI (`.github/workflows/ci.yml`)

```yaml
name: CI

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: "21"
          distribution: "temurin"
      - name: Cache Gradle
        uses: actions/cache@v3
        with:
          path: ~/.gradle/caches
          key: gradle-${{ hashFiles('**/*.gradle.kts') }}
      - name: Run tests
        run: ./gradlew test --no-daemon
      - name: Build JAR
        run: ./gradlew bootJar --no-daemon -x test
      - name: Build Docker image
        run: docker build -t pockt:${{ github.sha }} .
```

---

## 12. Phase Plan

### Phase 1 — Core MVP (Weeks 1–6)

**Deliverable:** Two users can register, hold a wallet balance, and transfer money end-to-end on Flutter.

| Week | Backend                                                                   | Flutter                                             |
| ---- | ------------------------------------------------------------------------- | --------------------------------------------------- |
| 1    | Project setup · Gradle · Docker Compose · Flyway migrations · Base config | Project setup · GoRouter · Dio client · theme       |
| 2    | User module: OTP · registration · login · JWT · Redis session             | Auth screens: phone · OTP · PIN setup · login       |
| 3    | Wallet module: create on registration · balance · top-up                  | Wallet home screen · balance display · top-up       |
| 4    | Transfer module: saga · locking · idempotency · audit log                 | Send screen · confirm screen · success screen       |
| 5    | Notification module: outbox poller · FCM push                             | Transaction history · detail screen · push handling |
| 6    | Integration testing · concurrency tests · ArchUnit                        | Widget tests · end-to-end on device                 |

**Exit criteria for Phase 1:**

- Concurrent transfer test passes (no overdraft under 20 threads)
- All unit + integration tests green
- API docs auto-generated and accessible at `/swagger-ui.html`
- Flutter app runs on both iOS and Android simulators

---

### Phase 2 — Hardening (Weeks 7–10)

| Week | Deliverable                                                                      |
| ---- | -------------------------------------------------------------------------------- |
| 7    | Biometric login (Flutter) · PIN change · account deactivation                    |
| 8    | Cursor-based pagination · transaction search · filters by date                   |
| 9    | Transfer reversal / refund flow · failed transaction retry                       |
| 10   | Load testing (Gatling, 500 concurrent users) · DB index review · HikariCP tuning |

---

### Phase 3 — Production Ready (Weeks 11–14)

| Week | Deliverable                                                           |
| ---- | --------------------------------------------------------------------- |
| 11   | Real payment gateway (Stripe / Paystack) · replace mock top-up        |
| 12   | KYC integration · daily transfer limits · AML flag system             |
| 13   | Prometheus + Grafana dashboards · structured JSON logging · alerting  |
| 14   | Penetration test · security audit · App Store + Play Store submission |

---

## 13. Folder Structure

```
pockt-server/
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
├── Dockerfile
├── docker-compose.yml
├── .env.example
├── .github/
│   └── workflows/
│       └── ci.yml
│
└── src/
    ├── main/
    │   ├── java/com/pockt/
    │   │   ├── PocktApplication.java
    │   │   │
    │   │   ├── infrastructure/
    │   │   │   ├── config/
    │   │   │   │   ├── ApplicationConfig.java
    │   │   │   │   └── JacksonConfig.java
    │   │   │   ├── security/
    │   │   │   │   ├── SecurityConfig.java
    │   │   │   │   ├── JwtFilter.java
    │   │   │   │   └── JwtService.java
    │   │   │   ├── web/
    │   │   │   │   ├── ApiResponse.java
    │   │   │   │   ├── PageResponse.java
    │   │   │   │   ├── GlobalExceptionHandler.java
    │   │   │   │   └── RequestIdFilter.java
    │   │   │   ├── ratelimit/
    │   │   │   │   └── RateLimitService.java
    │   │   │   ├── persistence/
    │   │   │   │   └── BaseRepository.java
    │   │   │   └── scheduler/
    │   │   │       ├── OutboxPoller.java
    │   │   │       └── OtpCleanupScheduler.java
    │   │   │
    │   │   ├── user/
    │   │   │   ├── api/
    │   │   │   │   ├── AuthController.java
    │   │   │   │   └── UserController.java
    │   │   │   ├── service/
    │   │   │   │   ├── UserService.java          (interface)
    │   │   │   │   └── OtpService.java           (interface)
    │   │   │   ├── internal/
    │   │   │   │   ├── UserServiceImpl.java
    │   │   │   │   └── OtpServiceImpl.java
    │   │   │   ├── repository/
    │   │   │   │   ├── UserRepository.java
    │   │   │   │   └── OtpRepository.java
    │   │   │   ├── domain/
    │   │   │   │   ├── User.java                 (record)
    │   │   │   │   └── OtpVerification.java      (record)
    │   │   │   └── dto/
    │   │   │       ├── RegisterRequest.java
    │   │   │       ├── LoginRequest.java
    │   │   │       ├── OtpSendRequest.java
    │   │   │       ├── OtpVerifyRequest.java
    │   │   │       ├── TokenResponse.java
    │   │   │       └── UserResponse.java
    │   │   │
    │   │   ├── wallet/
    │   │   │   ├── api/
    │   │   │   │   └── WalletController.java
    │   │   │   ├── service/
    │   │   │   │   └── WalletService.java        (interface)
    │   │   │   ├── internal/
    │   │   │   │   └── WalletServiceImpl.java
    │   │   │   ├── repository/
    │   │   │   │   └── WalletRepository.java
    │   │   │   ├── domain/
    │   │   │   │   └── Wallet.java               (record)
    │   │   │   └── dto/
    │   │   │       ├── WalletResponse.java
    │   │   │       └── TopUpRequest.java
    │   │   │
    │   │   ├── transfer/
    │   │   │   ├── api/
    │   │   │   │   └── TransferController.java
    │   │   │   ├── service/
    │   │   │   │   └── TransferService.java      (interface)
    │   │   │   ├── internal/
    │   │   │   │   └── TransferServiceImpl.java
    │   │   │   ├── repository/
    │   │   │   │   ├── TransactionRepository.java
    │   │   │   │   ├── AuditRepository.java
    │   │   │   │   └── OutboxRepository.java
    │   │   │   ├── domain/
    │   │   │   │   ├── Transaction.java          (record)
    │   │   │   │   └── AuditLog.java             (record)
    │   │   │   ├── event/
    │   │   │   │   └── TransferCompletedEvent.java
    │   │   │   └── dto/
    │   │   │       ├── TransferRequest.java
    │   │   │       ├── TransferResponse.java
    │   │   │       └── TransactionResponse.java
    │   │   │
    │   │   └── notification/
    │   │       ├── service/
    │   │       │   └── NotificationService.java  (interface)
    │   │       ├── internal/
    │   │       │   └── NotificationServiceImpl.java
    │   │       ├── provider/
    │   │       │   ├── PushProvider.java         (interface)
    │   │       │   ├── FcmPushProvider.java
    │   │       │   ├── LogPushProvider.java
    │   │       │   ├── SmsProvider.java          (interface)
    │   │       │   ├── TwilioSmsProvider.java
    │   │       │   └── LogSmsProvider.java
    │   │       └── listener/
    │   │           └── TransferEventListener.java
    │   │
    │   └── resources/
    │       ├── application.yml
    │       ├── application-dev.yml
    │       ├── application-prod.yml
    │       ├── keys/
    │       │   ├── private.pem               (gitignored in prod)
    │       │   └── public.pem
    │       └── db/
    │           └── migration/
    │               ├── V1__create_users.sql
    │               ├── V2__create_otp_verifications.sql
    │               ├── V3__create_wallets.sql
    │               ├── V4__create_transactions.sql
    │               ├── V5__create_audit_log.sql
    │               └── V6__create_notification_outbox.sql
    │
    └── test/
        └── java/com/pockt/
            ├── ArchitectureTest.java
            ├── user/
            │   ├── UserServiceTest.java
            │   ├── OtpServiceTest.java
            │   └── AuthControllerTest.java
            ├── wallet/
            │   ├── WalletServiceTest.java
            │   └── WalletRepositoryTest.java
            ├── transfer/
            │   ├── TransferServiceTest.java
            │   ├── TransferConcurrencyTest.java
            │   └── TransferControllerTest.java
            └── integration/
                └── FullFlowIntegrationTest.java
```

---

## 14. Quick Start

```bash
# 1. Clone and enter project
git clone https://github.com/yourname/pockt-server.git
cd pockt-server

# 2. Start infrastructure (Postgres + Redis)
docker-compose up -d postgres redis

# 3. Generate RS256 key pair for JWT
mkdir -p src/main/resources/keys
openssl genrsa -out src/main/resources/keys/private.pem 2048
openssl rsa -in src/main/resources/keys/private.pem \
            -pubout -out src/main/resources/keys/public.pem

# 4. Run the server (dev profile)
./gradlew bootRun --args='--spring.profiles.active=dev'

# 5. Verify it is healthy
curl http://localhost:8080/actuator/health

# 6. Open API docs
open http://localhost:8080/swagger-ui.html

# 7. Run all tests
./gradlew test

# ── Docker (full stack) ───────────────────────────────────
# Build image and start everything
docker-compose up --build

# View logs
docker-compose logs -f pockt

# Stop everything
docker-compose down
```

---

_Pockt standalone server planner — last updated 2026-09-26 — Phase 1 MVP_

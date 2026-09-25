# Pouch Wallet (Pockt) 👛

> A resilient, high-throughput peer-to-peer digital wallet application.  
> Engineered with **Java 21 Virtual Threads**, **Spring Boot 3.3**, **PostgreSQL**, and **Redis** for guaranteed consistency and zero overdraft under concurrent load. Mobile client in **Flutter 3.x**.

---

## 🌟 Application Feature Highlights

- **🔒 Concurrency Resilience & Zero Overdraft**
  - Uses pessimistic row-level locking (`SELECT ... FOR UPDATE`) inside atomic database transactions (`@Transactional(isolation = Isolation.READ_COMMITTED)`).
  - Enforces **deterministic UUID locking order** (`min(senderWalletId, receiverWalletId)` first) to strictly eliminate database deadlocks under high concurrency.
  - Verified by an automated **20-thread concurrency test** draining funds to zero with 0 overdraft and 0 failed transactions unhandled.

- **⚡ End-to-End Idempotency Guarantee**
  - Clients supply a unique `X-Idempotency-Key` (UUID) with every transfer.
  - Duplicate requests automatically detect cached transaction records and return identical responses without re-debiting.

- **🔑 Asymmetric RS256 JWT Authentication & Phone OTP**
  - Phone verification with 6-digit random OTP (5-minute TTL, lockout after 3 failed attempts).
  - Short-lived temporary tokens for registration and 15-minute RS256 signed access tokens.
  - Opaque refresh tokens stored in Redis with 30-day TTL, rotated on use and revocable on logout.

- **📦 Transactional Outbox Pattern for Notifications**
  - Writes transfer events (`TRANSFER_SENT`, `TRANSFER_RECEIVED`) directly into the `notification_outbox` table in the same DB transaction as the money transfer.
  - Background `OutboxPoller` executes `@Scheduled` jobs using `SELECT ... FOR UPDATE SKIP LOCKED` with exponential backoff retry.

- **📝 Append-Only Audit Logging**
  - Every financial balance alteration produces immutable audit records in `audit_log` with JSON snapshots of balances before and after.

- **🚦 Redis Sliding-Window Rate Limiting**
  - Throttles sensitive endpoints: Transfers (10/min), Logins (5/min), and OTP dispatches (3/10min).

- **📖 Interactive API Documentation**
  - Auto-generated OpenAPI 3 specification with Swagger UI including preconfigured Bearer JWT authentication.

---

## 📂 Repository & Folder Structure

```
pouch-wallet/
├── README.md                           # Root documentation & architecture overview
├── planner.md                          # Full system blueprint & technical specifications
├── .gitignore                          # Workspace ignore rules
│
├── backend/                            # Standalone Spring Boot modular monolith
│   ├── build.gradle.kts                # Build & dependency declarations
│   ├── settings.gradle.kts
│   ├── gradlew                         # Gradle wrapper executable
│   │
│   ├── src/main/
│   │   ├── java/com/pockt/
│   │   │   ├── PocktApplication.java  # Main application entry point
│   │   │   │
│   │   │   ├── infrastructure/         # Cross-cutting concerns (no business logic)
│   │   │   │   ├── config/             # ApplicationConfig, JacksonConfig, OpenApiConfig
│   │   │   │   ├── exception/          # PocktException hierarchy & ErrorCode definitions
│   │   │   │   ├── persistence/        # BaseRepository (shared JDBC helpers)
│   │   │   │   ├── ratelimit/          # RateLimitService (Redis sliding window)
│   │   │   │   ├── scheduler/          # OutboxPoller (@Scheduled SKIP LOCKED)
│   │   │   │   ├── security/           # JwtFilter, SecurityConfig, JwtService, UserPrincipal
│   │   │   │   ├── util/               # MoneyUtils (cents formatting & currency display)
│   │   │   │   └── web/                # ApiResponse envelope, ApiError, GlobalExceptionHandler
│   │   │   │
│   │   │   ├── user/                   # USER MODULE
│   │   │   │   ├── api/                # AuthController, UserController
│   │   │   │   ├── domain/             # User, OtpVerification records, OtpPurpose
│   │   │   │   ├── dto/                # RegisterRequest, LoginRequest, TokenResponse, UserResponse
│   │   │   │   ├── event/              # UserRegisteredEvent
│   │   │   │   ├── internal/           # UserServiceImpl, OtpServiceImpl
│   │   │   │   ├── repository/         # UserRepository, OtpRepository (Spring JDBC)
│   │   │   │   └── service/            # UserService, OtpService (public interfaces)
│   │   │   │
│   │   │   ├── wallet/                 # WALLET MODULE
│   │   │   │   ├── api/                # WalletController
│   │   │   │   ├── domain/             # Wallet record
│   │   │   │   ├── dto/                # WalletResponse, TopUpRequest
│   │   │   │   ├── internal/           # WalletServiceImpl (auto-creates wallet on registration)
│   │   │   │   ├── repository/         # WalletRepository (SELECT ... FOR UPDATE)
│   │   │   │   └── service/            # WalletService (public interface)
│   │   │   │
│   │   │   ├── transfer/               # TRANSFER MODULE
│   │   │   │   ├── api/                # TransferController
│   │   │   │   ├── domain/             # Transaction, AuditLog, NotificationOutbox records
│   │   │   │   ├── dto/                # TransferRequest, TransferResponse, TransactionResponse
│   │   │   │   ├── event/              # TransferCompletedEvent
│   │   │   │   ├── internal/           # TransferServiceImpl (saga coordinator)
│   │   │   │   ├── repository/         # TransactionRepository, AuditRepository, OutboxRepository
│   │   │   │   └── service/            # TransferService (public interface)
│   │   │   │
│   │   │   └── notification/           # NOTIFICATION MODULE
│   │   │       ├── internal/           # NotificationServiceImpl
│   │   │       ├── listener/           # TransferEventListener
│   │   │       ├── provider/           # PushProvider (Log/FCM), SmsProvider (Log/Twilio)
│   │   │       └── service/            # NotificationService (public interface)
│   │   │
│   │   └── resources/
│   │       ├── application.yml         # Base configuration (HikariCP, Redis, Flyway)
│   │       ├── application-dev.yml     # Local dev profile configuration
│   │       ├── keys/                   # RS256 RSA keypair (private.pem, public.pem)
│   │       └── db/migration/           # Flyway SQL migrations (V1 to V6)
│   │
│   └── src/test/java/com/pockt/        # Comprehensive test suite
│       ├── ArchitectureTest.java       # ArchUnit package boundary enforcement
│       ├── integration/                # FullFlowIntegrationTest (MockMvc end-to-end)
│       ├── transfer/                   # TransferConcurrencyTest (20 threads), TransferServiceTest
│       ├── user/                       # UserServiceTest, OtpServiceTest
│       └── wallet/                     # WalletServiceTest
│
└── mobile/                             # Flutter mobile application (Coming next)
```

---

## 🗄️ Database Schema & Entities

Migrations are managed with **Flyway** in `backend/src/main/resources/db/migration/`:

| Version | Migration | Key Design Rules |
| :--- | :--- | :--- |
| `V1` | `create_users` | Unique `phone`, BCrypt `pin_hash`, `kyc_status`, `fcm_token` |
| `V2` | `create_otp_verifications` | Hashed OTP, `purpose`, 5-min `expires_at`, `used` boolean flag |
| `V3` | `create_wallets` | Strict DB constraint: `CHECK (balance >= 0)`, `UNIQUE(user_id, currency)` |
| `V4` | `create_transactions` | Unique `idempotency_key`, foreign keys to wallets, indexed timestamps |
| `V5` | `create_audit_log` | Append-only table storing `old_value` and `new_value` as `JSONB` |
| `V6` | `create_notification_outbox` | Status `PENDING` with retry backoff, consumed with `SKIP LOCKED` |

---

## 🔌 API Reference Summary

All responses follow the unified envelope:
```json
{
  "success": true,
  "data": {},
  "error": null,
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-09-26T10:00:00Z"
}
```

### Authentication & Users
- `POST /api/v1/auth/otp/send` — Request OTP to phone
- `POST /api/v1/auth/otp/verify` — Verify OTP & receive temporary token
- `POST /api/v1/auth/register` — Complete registration with name & 6-digit PIN
- `POST /api/v1/auth/login` — Authenticate with phone + PIN
- `POST /api/v1/auth/refresh` — Rotate refresh token
- `POST /api/v1/auth/logout` — Revoke active session
- `GET  /api/v1/users/me` — Authenticated profile details
- `PATCH /api/v1/users/me` — Update user profile
- `GET  /api/v1/users/search?phone=` — Lookup recipient before confirming transfer
- `POST /api/v1/users/me/fcm-token` — Save push notification device token

### Wallets
- `GET  /api/v1/wallets` — List user's wallets
- `GET  /api/v1/wallets/{id}` — Get single wallet & balance
- `POST /api/v1/wallets/{id}/topup` — Mock wallet funding (MVP)

### Transfers
- `POST /api/v1/transfers` — Initiate P2P transfer (accepts `X-Idempotency-Key` header)
- `GET  /api/v1/transfers?cursor=&limit=20` — Cursor-paginated transfer history
- `GET  /api/v1/transfers/{id}` — Detailed transaction receipt

---

## 🚀 Quick Start (Running Locally)

### 1. Prerequisites
- **Java 21+** (`openjdk 21`)
- **PostgreSQL** running locally (`localhost:5432`, database: `pockt`, user: `pockt`, password: `pockt`)
- **Redis** running locally (`localhost:6379`)

### 2. Run Test Suite
```bash
cd backend
./gradlew test
```
Executes all unit tests, ArchUnit architectural rule validations, and the 20-thread concurrency test.

### 3. Run Dev Server
```bash
cd backend
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### 4. Interactive Documentation & Health
- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI 3 JSON**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- **Health Check**: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

# Pouch Wallet (Pockt) 👛

> A resilient, high-throughput peer-to-peer digital wallet and banking platform inspired by Paytm/PhonePe.  
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

- **🏦 Simulated Bank Server & Core Banking Simulator**
  - Link external bank accounts (IFSC + Account Number verification).
  - **Add Money (Bank ➔ Wallet)**: Move funds seamlessly from bank into wallet with PIN authentication.
  - **Withdraw Money (Wallet ➔ Bank)**: Transfer wallet balances back to bank account.
  - Primary account designation and complete bank transaction history.

- **⚡ UPI & Virtual Private Address (VPA) Engine**
  - Automatic provision of default `@pockt` handles (`<phone>@pockt`).
  - Create custom handles (e.g. `alex@pockt`).
  - Real-time VPA verification before sending money.
  - Instant UPI payments secured with 4-to-6 digit wallet PIN.

- **📷 QR Code Payment Ecosystem**
  - **Static Personal QR**: Standard UPI payment URI (`upi://pay?pa=...`) for instant incoming payments.
  - **Dynamic Merchant QR**: Generate QR codes encoded with specific payment amounts and notes.
  - **QR Code Scanner**: Parses raw scanned data or UPI URIs, validates recipient on Pockt, and resolves payment data.

- **💳 Digital Cards & Credit Engine (Pockt Postpaid)**
  - **Virtual Debit Card**: Linked directly to wallet balance with Luhn-compliant 16-digit card number and card network selection (RuPay, Visa, Mastercard).
  - **Virtual Credit Card / Pockt Postpaid**: Instant digital credit line with revolving limit, billing ledger, and repayment from wallet.
  - **PIN-Secured CVV Reveal**: Sensitive card numbers and CVV are masked by default and only revealed upon PIN authentication.
  - **Merchant Payment Simulator**: Endpoint simulating online merchant checkout, daily spending limits, and freeze/unfreeze controls.

- **🛡️ Admin Back-Office & Business Intelligence Suite**
  - **User Directory**: Search by phone or name, filter by KYC status and account state with pagination.
  - **Complete User Dossier**: 360-degree view of user wallets, bank accounts, UPI handles, cards, and credit accounts.
  - **Time-Filtered Financial Metrics**: Calculate total spent, total received, net flow, bank deposits, and withdrawals within any custom date range (`from` ➔ `to`).
  - **Executive System Overview Report**: Real-time reporting on system liquidity, transaction counts, transfer volumes, bank flows, and total credit outstanding.
  - **Account & Wallet Controls**: Freeze or unfreeze specific wallets or entire user accounts.

- **🔑 Asymmetric RS256 JWT Authentication & Security**
  - Phone verification with 6-digit random OTP (5-minute TTL, lockout after 3 failed attempts).
  - Passwordless login with OTP, authenticated change PIN, and forgot PIN recovery via OTP.
  - Role-based authorization (`USER`, `ADMIN`) backed by RS256 tokens and `@PreAuthorize`.
  - Opaque refresh tokens stored in Redis with 30-day TTL, rotated on use and revocable on logout.

- **📦 Transactional Outbox Pattern for Notifications**
  - Writes transfer events (`TRANSFER_SENT`, `TRANSFER_RECEIVED`) directly into `notification_outbox` in the same DB transaction as money transfers.
  - Background `OutboxPoller` executes `@Scheduled` jobs using `SELECT ... FOR UPDATE SKIP LOCKED` with exponential backoff retry.

- **📝 Append-Only Audit Logging**
  - Every financial balance alteration produces immutable audit records in `audit_log` with JSON snapshots of balances before and after.

- **🚦 Redis Sliding-Window Rate Limiting**
  - Throttles sensitive endpoints: Transfers (10/min), Logins (5/min), and OTP dispatches (3/10min).

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
│   │   │   ├── user/                   # USER & AUTH MODULE
│   │   │   │   ├── api/                # AuthController, UserController
│   │   │   │   ├── domain/             # User, OtpVerification records, OtpPurpose
│   │   │   │   ├── dto/                # RegisterRequest, LoginRequest, OtpLoginRequest, ResetPinRequest, TokenResponse, UserResponse
│   │   │   │   ├── event/              # UserRegisteredEvent
│   │   │   │   ├── internal/           # UserServiceImpl, OtpServiceImpl
│   │   │   │   ├── repository/         # UserRepository, OtpRepository (Spring JDBC)
│   │   │   │   └── service/            # UserService, OtpService
│   │   │   │
│   │   │   ├── wallet/                 # WALLET MODULE
│   │   │   │   ├── api/                # WalletController
│   │   │   │   ├── domain/             # Wallet record
│   │   │   │   ├── dto/                # WalletResponse, TopUpRequest
│   │   │   │   ├── internal/           # WalletServiceImpl
│   │   │   │   ├── repository/         # WalletRepository (SELECT ... FOR UPDATE)
│   │   │   │   └── service/            # WalletService
│   │   │   │
│   │   │   ├── transfer/               # TRANSFER MODULE
│   │   │   │   ├── api/                # TransferController
│   │   │   │   ├── domain/             # Transaction, AuditLog, NotificationOutbox records
│   │   │   │   ├── dto/                # TransferRequest, TransferResponse, TransactionResponse
│   │   │   │   ├── event/              # TransferCompletedEvent
│   │   │   │   ├── internal/           # TransferServiceImpl (deadlock-free saga coordinator)
│   │   │   │   ├── repository/         # TransactionRepository, AuditRepository, OutboxRepository
│   │   │   │   └── service/            # TransferService
│   │   │   │
│   │   │   ├── bank/                   # BANK SIMULATOR MODULE
│   │   │   │   ├── api/                # BankController (link, add-money, withdraw, history)
│   │   │   │   ├── domain/             # BankAccount, BankTransaction records
│   │   │   │   ├── dto/                # LinkBankAccountRequest, AddMoneyFromBankRequest, WithdrawToBankRequest, BankAccountResponse
│   │   │   │   ├── internal/           # BankServiceImpl
│   │   │   │   ├── repository/         # BankAccountRepository, BankTransactionRepository
│   │   │   │   └── service/            # BankService
│   │   │   │
│   │   │   ├── upi/                    # UPI ENGINE MODULE
│   │   │   │   ├── api/                # UpiController (handles, verify, pay)
│   │   │   │   ├── domain/             # UpiHandle record
│   │   │   │   ├── dto/                # CreateUpiHandleRequest, UpiPaymentRequest, UpiPaymentResponse, VerifyVpaResponse
│   │   │   │   ├── internal/           # UpiServiceImpl
│   │   │   │   ├── repository/         # UpiRepository
│   │   │   │   └── service/            # UpiService
│   │   │   │
│   │   │   ├── qr/                     # QR CODE ECOSYSTEM MODULE
│   │   │   │   ├── api/                # QrController (my-qr, generate dynamic, scan)
│   │   │   │   ├── dto/                # GenerateDynamicQrRequest, QrDetailsResponse, ScanQrRequest, ScanQrResultResponse
│   │   │   │   ├── internal/           # QrServiceImpl
│   │   │   │   └── service/            # QrService
│   │   │   │
│   │   │   ├── card/                   # DIGITAL CARDS & CREDIT MODULE
│   │   │   │   ├── api/                # CardController (debit, credit, reveal, settings, charge, repay)
│   │   │   │   ├── domain/             # Card, CreditAccount records
│   │   │   │   ├── dto/                # IssueDebitCardRequest, ApplyCreditCardRequest, RevealCardRequest, CardChargeRequest, RepayCreditRequest
│   │   │   │   ├── internal/           # CardServiceImpl
│   │   │   │   ├── repository/         # CardRepository, CreditAccountRepository
│   │   │   │   └── service/            # CardService
│   │   │   │
│   │   │   ├── admin/                  # ADMIN BACK-OFFICE MODULE
│   │   │   │   ├── api/                # AdminController (users, dossier, financials, overview report, freeze)
│   │   │   │   ├── dto/                # AdminUserFinancialsResponse, AdminUserDossierResponse, AdminOverviewReportResponse
│   │   │   │   ├── internal/           # AdminServiceImpl
│   │   │   │   ├── repository/         # AdminRepository (SQL aggregations with time filter)
│   │   │   │   └── service/            # AdminService
│   │   │   │
│   │   │   └── notification/           # NOTIFICATION MODULE
│   │   │       ├── internal/           # NotificationServiceImpl
│   │   │       ├── listener/           # TransferEventListener
│   │   │       ├── provider/           # PushProvider (Log/FCM), SmsProvider (Log/Twilio)
│   │   │       └── service/            # NotificationService
│   │   │
│   │   └── resources/
│   │       ├── application.yml         # Base configuration (HikariCP, Redis, Flyway)
│   │       ├── application-dev.yml     # Local dev profile configuration
│   │       ├── keys/                   # RS256 RSA keypair (private.pem, public.pem)
│   │       └── db/migration/           # Flyway SQL migrations (V1 to V10)
│   │
│   └── src/test/java/com/pockt/        # Comprehensive test suite
│       ├── ArchitectureTest.java       # ArchUnit package boundary enforcement
│       ├── integration/                # FullFlowIntegrationTest, PaytmEcosystemIntegrationTest
│       ├── bank/                       # BankServiceTest
│       ├── upi/                        # UpiServiceTest
│       ├── qr/                         # QrServiceTest
│       ├── card/                       # CardServiceTest
│       ├── admin/                      # AdminServiceTest
│       ├── transfer/                   # TransferConcurrencyTest (20 threads), TransferServiceTest
│       ├── user/                       # UserServiceTest, OtpServiceTest
│       └── wallet/                     # WalletServiceTest
│
└── mobile/                             # Flutter mobile application
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
| `V7` | `create_bank_accounts` | `bank_accounts` with simulated balance & `bank_transactions` ledger |
| `V8` | `create_upi_handles` | Unique `vpa` handles linked to wallets, indexed lookup |
| `V9` | `create_cards_and_credit` | `cards` (masked + full PAN, CVV, expiry, daily limits) & `credit_accounts` |
| `V10` | `add_user_roles` | Adds `role` (`USER`, `ADMIN`) to users table for back-office security |

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

### 1. Authentication & Users (`/api/v1/auth`, `/api/v1/users`)
- `POST /api/v1/auth/otp/send` — Request OTP to phone (Registration, Login, Reset PIN)
- `POST /api/v1/auth/otp/verify` — Verify OTP & receive temporary JWT token
- `POST /api/v1/auth/register` — Complete registration with name & 6-digit PIN
- `POST /api/v1/auth/login` — Authenticate with phone + PIN
- `POST /api/v1/auth/login/otp` — Passwordless login with phone + OTP
- `POST /api/v1/auth/pin/reset` — Reset forgotten PIN using verified OTP temporary token
- `POST /api/v1/auth/pin/change` — Authenticated PIN change with old & new PIN
- `POST /api/v1/auth/refresh` — Rotate refresh token
- `POST /api/v1/auth/logout` — Revoke active session
- `GET  /api/v1/users/me` — Authenticated profile details
- `PATCH /api/v1/users/me` — Update user profile
- `GET  /api/v1/users/search?phone=` — Lookup recipient before confirming transfer
- `POST /api/v1/users/me/fcm-token` — Save push notification device token

### 2. Wallets (`/api/v1/wallets`)
- `GET  /api/v1/wallets` — List user's wallets & current balances
- `GET  /api/v1/wallets/{id}` — Get single wallet details
- `POST /api/v1/wallets/{id}/topup` — Direct wallet top-up

### 3. P2P Transfers (`/api/v1/transfers`)
- `POST /api/v1/transfers` — Initiate P2P transfer (accepts `X-Idempotency-Key` header)
- `GET  /api/v1/transfers?cursor=&limit=20` — Cursor-paginated transfer history
- `GET  /api/v1/transfers/{id}` — Detailed transaction receipt

### 4. Bank Account Simulator (`/api/v1/banks`)
- `POST /api/v1/banks/link` — Link an external bank account
- `GET  /api/v1/banks` — List all linked bank accounts
- `GET  /api/v1/banks/{id}` — Get bank account details & simulated balance
- `PATCH /api/v1/banks/{id}/primary` — Set account as primary
- `POST /api/v1/banks/add-money` — Deposit funds from bank to wallet (requires PIN)
- `POST /api/v1/banks/withdraw` — Withdraw funds from wallet to bank (requires PIN)
- `GET  /api/v1/banks/{id}/transactions` — Bank transaction history

### 5. Instant UPI & VPA Engine (`/api/v1/upi`)
- `POST /api/v1/upi/handles` — Create custom UPI handle (e.g. `alex@pockt`)
- `GET  /api/v1/upi/handles` — List UPI handles (auto-provisions default `<phone>@pockt`)
- `GET  /api/v1/upi/verify?vpa=` — Verify recipient UPI ID before paying
- `POST /api/v1/upi/pay` — Instant UPI transfer via VPA and PIN

### 6. QR Code Ecosystem (`/api/v1/qr`)
- `GET  /api/v1/qr/my-qr` — Get personal static UPI QR code payload
- `POST /api/v1/qr/generate` — Generate dynamic QR code with pre-filled amount & note
- `POST /api/v1/qr/scan` — Scan, parse, and validate raw QR payload or UPI URI

### 7. Digital Cards & Credit (`/api/v1/cards`)
- `POST /api/v1/cards/debit` — Issue virtual debit card linked to wallet
- `POST /api/v1/cards/credit/apply` — Apply for digital credit card / Pockt Postpaid limit
- `GET  /api/v1/cards` — List user's cards (masked)
- `GET  /api/v1/cards/{id}` — Get single card details
- `POST /api/v1/cards/{id}/reveal` — Reveal full 16-digit PAN and CVV with wallet PIN
- `PATCH /api/v1/cards/{id}/settings` — Toggle online payments and daily spending limit
- `POST /api/v1/cards/{id}/freeze` — Toggle freeze / unfreeze card
- `POST /api/v1/cards/{id}/charge` — Simulate online merchant transaction
- `GET  /api/v1/cards/credit/account` — View credit account balance and statement
- `POST /api/v1/cards/credit/repay` — Repay outstanding credit bill from wallet balance

### 8. Admin Back-Office (`/api/v1/admin`) *(Requires `ROLE_ADMIN`)*
- `GET   /api/v1/admin/users` — Directory search with KYC & active status filters
- `GET   /api/v1/admin/users/{userId}` — Comprehensive 360-degree user dossier
- `GET   /api/v1/admin/users/{userId}/financials?from=&to=` — Financial volume metrics (spent, received, net flow, bank activity) with time filter
- `PATCH /api/v1/admin/users/{userId}/status` — Activate or freeze user account
- `PATCH /api/v1/admin/wallets/{walletId}/freeze` — Freeze or unfreeze specific wallet
- `GET   /api/v1/admin/reports/overview?from=&to=` — Platform executive liquidity & transfer volume report with time filter

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
Executes all unit tests, ArchUnit architectural rule validations, and both the 20-thread concurrency test and full Paytm ecosystem integration test.

### 3. Run Dev Server
```bash
cd backend
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### 4. Interactive Documentation & Health
- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI 3 JSON**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- **Health Check**: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

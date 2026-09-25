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

- **👥 Saved Beneficiaries & Favorite Payees**
  - Save frequent payees by Phone Number, UPI VPA (`user@pockt`), or Bank Account + IFSC.
  - Quick 1-tap transfer access, nickname aliases, and toggleable favorites list.

- **💡 Utility Bill Payments & Mobile Recharge Simulator**
  - 6 major utility categories: Mobile Recharge, Electricity, Water, Broadband & Fiber, DTH / Cable TV, and Domestic Gas.
  - Integrated biller catalog (Airtel, Jio, BESCOM, Tata Power, BWSSB, ACT Fibernet, Tata Play, Indane).
  - Simulated bill fetcher computing outstanding amounts, customer names, and due dates.
  - Atomic wallet balance payment with unique reference generation (`BPAY-XXXXX`) and transactional outbox notifications.

- **🤝 Payment Requests & Multi-Person Split Bill Engine**
  - **1-on-1 Requests**: Request money from any contact via Phone or VPA with custom notes and configurable expiry.
  - **Split Bill**: Group expense splitting with shared `split_group_id` across multiple participants.
  - **Atomic Settle**: Payers accept incoming requests directly from the app; transfers execute instantly via the core deadlock-free P2P transfer engine.

- **📜 KYC Verification & Tiered Limits**
  - Identity document submission (Passport, National ID, PAN, Driving License).
  - Two-tier compliance architecture:
    - **Tier 0 (Unverified)**: Max balance $1,000.00; Max single transfer $250.00.
    - **Tier 1 (Verified)**: Max balance $100,000.00; Max single transfer $25,000.00.
  - Admin approval workflow with automatic status transitions and real-time limit upgrades.

- **📊 Spending Analytics & Transaction Categorization**
  - Automatic categorization across `FOOD_AND_DINING`, `SHOPPING`, `UTILITIES_AND_BILLS`, `TRANSFER`, `ENTERTAINMENT`, `TRAVEL`, and `OTHER`.
  - Monthly breakdown of total debits, total credits, net cash flow, and percentage share by category.

- **🎁 Cashback & Scratch Card Rewards Engine**
  - Automatic scratch card generation upon completing qualifying transfers ($10+) and bill payments.
  - Google Pay / Paytm style scratch-to-reveal mechanism where reward values stay hidden until scratched.
  - Unlocked cashback immediately credits into the user's wallet with outbox push notification.

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
  - Writes transfer events (`TRANSFER_SENT`, `TRANSFER_RECEIVED`, `BILL_PAYMENT_SUCCESS`, `CASHBACK_CREDITED`) directly into `notification_outbox` in the same DB transaction.
  - Background `OutboxPoller` executes `@Scheduled` jobs using `SELECT ... FOR UPDATE SKIP LOCKED` with exponential backoff retry.

- **📝 Append-Only Audit Logging**
  - Every financial balance alteration produces immutable audit records in `audit_log` with JSON snapshots of balances before and after.

- **🚦 Redis Sliding-Window Rate Limiting**
  - Throttles sensitive endpoints: Transfers (10/min), Logins (5/min), and OTP dispatches (3/10min).

- **🧾 Digital Receipt Generator & Public Verification Engine**
  - Instant receipt generation for both P2P transfers and utility bill payments with unique human-readable references.
  - Printable HTML invoices featuring clean CSS typography, status badges, breakdown of amounts, sender/recipient metadata, and SHA-256 digital signature hashes.
  - Public `/api/v1/receipts/verify/{hash}` verification endpoint enabling any third party to validate payment authenticity without requiring authentication.

- **📧 Email Notifications & Outbox Dispatch with Attachments**
  - Outbox notification pipeline extended with rich email support and file attachments.
  - Pluggable `EmailProvider` interface with `LogEmailProvider` and ready integration for SendGrid, AWS SES, or SMTP.
  - Generates and attaches transaction invoices directly to outgoing notification emails.

- **📥 Queue Management & Dead Letter Queue (DLQ)**
  - Resilient asynchronous processing with exponential backoff retries (capped at 5 attempts).
  - Failed messages automatically transition to `DEAD_LETTER` status recording `last_error` and `dead_lettered_at` timestamp.
  - Admin operational controls: live queue health metrics, DLQ inspection, individual message replay, and bulk replay.

- **⚖️ In-Process Transaction Reconciliation & Reversal Engine**
  - Background `@Scheduled` job detects stuck `PENDING` transactions exceeding threshold (5 minutes) and initiates automated refunds.
  - Formal customer dispute registration for suspicious or failed transactions (`POST /api/v1/disputes/{id}`).
  - Admin dispute review and atomic reversal workflow: reverses sender/receiver balances in a single transaction with audit logging and duplicate reversal protection.

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
│   │   │   │   └── web/                # ApiResponse envelope, ApiError, GlobalExceptionHandler, HealthController
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
│   │   │   ├── beneficiary/            # BENEFICIARY & SAVED PAYEES MODULE
│   │   │   │   ├── api/                # BeneficiaryController
│   │   │   │   ├── domain/             # Beneficiary record
│   │   │   │   ├── dto/                # AddBeneficiaryRequest, BeneficiaryResponse
│   │   │   │   ├── internal/           # BeneficiaryServiceImpl
│   │   │   │   ├── repository/         # BeneficiaryRepository
│   │   │   │   └── service/            # BeneficiaryService
│   │   │   │
│   │   │   ├── bill/                   # UTILITY BILLS & RECHARGE MODULE
│   │   │   │   ├── api/                # BillController
│   │   │   │   ├── domain/             # BillPayment record
│   │   │   │   ├── dto/                # FetchBillRequest, PayBillRequest, BillDetailsResponse, BillPaymentResponse
│   │   │   │   ├── internal/           # BillPaymentServiceImpl
│   │   │   │   ├── repository/         # BillPaymentRepository
│   │   │   │   └── service/            # BillPaymentService
│   │   │   │
│   │   │   ├── request/                # PAYMENT REQUESTS & SPLIT BILL MODULE
│   │   │   │   ├── api/                # PaymentRequestController
│   │   │   │   ├── domain/             # PaymentRequest record
│   │   │   │   ├── dto/                # CreatePaymentRequest, SplitBillRequest, PaymentRequestResponse
│   │   │   │   ├── internal/           # PaymentRequestServiceImpl
│   │   │   │   ├── repository/         # PaymentRequestRepository
│   │   │   │   └── service/            # PaymentRequestService
│   │   │   │
│   │   │   ├── kyc/                    # KYC & TIERED LIMITS MODULE
│   │   │   │   ├── api/                # KycController, KycAdminController
│   │   │   │   ├── domain/             # KycVerification record
│   │   │   │   ├── dto/                # SubmitKycRequest, ReviewKycRequest, KycStatusResponse, KycVerificationResponse
│   │   │   │   ├── internal/           # KycServiceImpl
│   │   │   │   ├── repository/         # KycRepository
│   │   │   │   └── service/            # KycService
│   │   │   │
│   │   │   ├── analytics/              # SPENDING ANALYTICS MODULE
│   │   │   │   ├── api/                # AnalyticsController
│   │   │   │   ├── dto/                # SpendingSummaryResponse, CategorySpendingItem
│   │   │   │   ├── internal/           # AnalyticsServiceImpl
│   │   │   │   ├── repository/         # AnalyticsRepository
│   │   │   │   └── service/            # AnalyticsService
│   │   │   │
│   │   │   ├── reward/                 # CASHBACK & REWARDS MODULE
│   │   │   │   ├── api/                # RewardController
│   │   │   │   ├── domain/             # ScratchCard record
│   │   │   │   ├── dto/                # ScratchCardResponse, RewardsSummaryResponse
│   │   │   │   ├── internal/           # RewardServiceImpl, RewardEventListener
│   │   │   │   ├── repository/         # ScratchCardRepository
│   │   │   │   └── service/            # RewardService
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
│   │   │   ├── receipt/                # RECEIPT & INVOICE ENGINE MODULE
│   │   │   │   ├── api/                # ReceiptController (JSON receipt, HTML download, public SHA-256 verification)
│   │   │   │   ├── dto/                # ReceiptResponse
│   │   │   │   ├── internal/           # ReceiptServiceImpl
│   │   │   │   └── service/            # ReceiptService
│   │   │   │
│   │   │   ├── queue/                  # QUEUE & DEAD LETTER QUEUE (DLQ) MODULE
│   │   │   │   ├── api/                # QueueAdminController (metrics, list DLQ, replay single, replay all)
│   │   │   │   ├── dto/                # QueueMetricsResponse, DeadLetterItemResponse
│   │   │   │   ├── internal/           # QueueServiceImpl
│   │   │   │   └── service/            # QueueService
│   │   │   │
│   │   │   ├── reconciliation/         # RECONCILIATION & REVERSAL ENGINE MODULE
│   │   │   │   ├── api/                # DisputeController, ReconciliationAdminController
│   │   │   │   ├── dto/                # DisputeRequest, ReversalResponse, ReconciliationSummaryResponse
│   │   │   │   ├── internal/           # ReconciliationServiceImpl (@Scheduled reconciler, atomic balance reversal)
│   │   │   │   ├── repository/         # ReconciliationRepository
│   │   │   │   └── service/            # ReconciliationService
│   │   │   │
│   │   │   └── notification/           # NOTIFICATION MODULE
│   │   │       ├── internal/           # NotificationServiceImpl
│   │   │       ├── listener/           # TransferEventListener
│   │   │       ├── provider/           # PushProvider (Log/FCM), SmsProvider (Log/Twilio), EmailProvider (Log/SMTP/Attachments)
│   │   │       └── service/            # NotificationService
│   │   │
│   │   └── resources/
│   │       ├── application.yml         # Base configuration (HikariCP, Redis, Flyway)
│   │       ├── application-dev.yml     # Local dev profile configuration
│   │       ├── keys/                   # RS256 RSA keypair (private.pem, public.pem)
│   │       └── db/migration/           # Flyway SQL migrations (V1 to V16)
│   │
│   └── src/test/java/com/pockt/        # Comprehensive test suite
│       ├── ArchitectureTest.java       # ArchUnit package boundary enforcement
│       ├── HealthEndpointTest.java     # Health & database probe validation
│       ├── integration/                # FullFlowIntegrationTest, PaytmEcosystemIntegrationTest, EnterpriseOperationsIntegrationTest
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
| `V11` | `create_beneficiaries` | `beneficiaries` with phone, VPA, bank details, and favorite flag |
| `V12` | `create_bill_payments` | `bill_payments` with biller ID, category, consumer number, and reference |
| `V13` | `create_payment_requests` | `payment_requests` with status, expiry, and split group tracking |
| `V14` | `create_kyc_verifications` | `kyc_verifications` with document details, review status, and `kyc_tier` |
| `V15` | `create_rewards_and_analytics` | `scratch_cards` table and `category` column added to `transactions` |
| `V16` | `enhance_notification_outbox_and_dlq` | Adds `last_error`, `dead_lettered_at` to outbox; adds `disputed`, `dispute_reason`, `reversed_at` to transactions |

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

### 4. Saved Beneficiaries (`/api/v1/beneficiaries`)
- `POST /api/v1/beneficiaries` — Save a new payee (phone, VPA, or bank account)
- `GET  /api/v1/beneficiaries?favoritesOnly=` — List saved beneficiaries
- `GET  /api/v1/beneficiaries/{id}` — Get beneficiary details
- `DELETE /api/v1/beneficiaries/{id}` — Delete a saved beneficiary
- `PATCH /api/v1/beneficiaries/{id}/favorite?favorite=` — Toggle favorite status

### 5. Utility Bills & Recharge (`/api/v1/bills`)
- `GET  /api/v1/bills/categories` — List supported bill categories (Mobile, Power, Water, etc.)
- `GET  /api/v1/bills/billers?category=` — List billers for a category
- `POST /api/v1/bills/fetch` — Fetch simulated bill details by consumer number
- `POST /api/v1/bills/pay` — Pay bill using wallet balance
- `GET  /api/v1/bills/history` — User bill payment history

### 6. Payment Requests & Split Bill (`/api/v1/payment-requests`)
- `POST /api/v1/payment-requests` — Request money from a contact
- `POST /api/v1/payment-requests/split` — Create multi-person split bill
- `GET  /api/v1/payment-requests/incoming` — View pending incoming requests
- `GET  /api/v1/payment-requests/outgoing` — View outgoing requests created by user
- `GET  /api/v1/payment-requests/{id}` — View payment request details
- `POST /api/v1/payment-requests/{id}/accept` — Accept request and settle instantly via atomic wallet transfer
- `POST /api/v1/payment-requests/{id}/decline` — Decline incoming payment request
- `POST /api/v1/payment-requests/{id}/cancel` — Cancel pending outgoing request

### 7. KYC & Verification (`/api/v1/kyc`, `/api/v1/admin/kyc`)
- `POST /api/v1/kyc/submit` — Submit identity documents for verification
- `GET  /api/v1/kyc/status` — View current KYC tier and transaction/balance limits
- `GET  /api/v1/kyc/history` — View past KYC submissions
- `GET  /api/v1/admin/kyc/pending` — [Admin] List pending KYC submissions
- `POST /api/v1/admin/kyc/{id}/review` — [Admin] Approve or reject KYC documents

### 8. Spending Analytics (`/api/v1/analytics`)
- `GET  /api/v1/analytics/spending?month=&year=` — Monthly spending summary, category breakdown, percentages, and net cash flow

### 9. Rewards & Cashback (`/api/v1/rewards`)
- `GET  /api/v1/rewards/scratch-cards?unscratchedOnly=` — List scratch cards
- `GET  /api/v1/rewards/summary` — Overview of total cashback won and card counts
- `POST /api/v1/rewards/scratch-cards/{id}/scratch` — Scratch card and claim cashback directly into wallet

### 10. Bank Account Simulator (`/api/v1/banks`)
- `POST /api/v1/banks/link` — Link an external bank account
- `GET  /api/v1/banks` — List all linked bank accounts
- `GET  /api/v1/banks/{id}` — Get bank account details & simulated balance
- `PATCH /api/v1/banks/{id}/primary` — Set account as primary
- `POST /api/v1/banks/add-money` — Deposit funds from bank to wallet (requires PIN)
- `POST /api/v1/banks/withdraw` — Withdraw funds from wallet to bank (requires PIN)
- `GET  /api/v1/banks/{id}/transactions` — Bank transaction history

### 11. UPI & VPA Engine (`/api/v1/upi`)
- `GET  /api/v1/upi/handles` — List all registered UPI handles
- `POST /api/v1/upi/handles` — Register custom UPI handle (`name@pockt`)
- `GET  /api/v1/upi/verify?vpa=` — Real-time recipient lookup & validation
- `POST /api/v1/upi/pay` — PIN-authorized instant UPI transfer

### 12. QR Code Ecosystem (`/api/v1/qr`)
- `GET  /api/v1/qr/my-qr` — Fetch personal static UPI QR code payload
- `POST /api/v1/qr/dynamic` — Generate dynamic merchant QR code with amount & note
- `POST /api/v1/qr/scan` — Scan and resolve QR code payload before payment

### 13. Digital Cards & Credit (`/api/v1/cards`)
- `POST  /api/v1/cards/debit` — Instant issuance of virtual debit card linked to wallet
- `POST  /api/v1/cards/credit` — Apply for virtual credit card with revolving credit line
- `GET   /api/v1/cards` — List all active digital cards (masked)
- `POST  /api/v1/cards/{id}/reveal` — PIN-secured CVV and full PAN reveal
- `PATCH /api/v1/cards/{id}/settings` — Toggle online usage and daily limit
- `PATCH /api/v1/cards/{id}/freeze` — Freeze/unfreeze card instantly
- `POST  /api/v1/cards/{id}/charge` — Merchant charge simulator
- `GET   /api/v1/cards/credit-account` — View credit balance, limit, and bill amount
- `POST  /api/v1/cards/credit-account/repay` — Repay credit bill from wallet balance

### 14. Admin Back-Office (`/api/v1/admin`)
- `GET   /api/v1/admin/users` — Paginated user directory with search and KYC filters
- `GET   /api/v1/admin/users/{id}/dossier` — 360-degree comprehensive user dossier
- `GET   /api/v1/admin/users/{id}/financials?from=&to=` — Time-filtered user financial audit
- `GET   /api/v1/admin/reports/overview` — Executive real-time liquidity and volume report
- `PATCH /api/v1/admin/users/{id}/status` — Account suspension and activation
- `PATCH /api/v1/admin/wallets/{id}/freeze` — Freeze or unfreeze specific wallets

### 15. Receipt Generation & Verification (`/api/v1/receipts`)
- `GET  /api/v1/receipts/{reference}` — Structured JSON receipt for any transfer or bill payment
- `GET  /api/v1/receipts/{reference}/download` — Downloadable print-ready HTML invoice receipt
- `GET  /api/v1/receipts/verify/{hash}` — Public SHA-256 digital receipt integrity verification (no auth required)

### 16. Queue & Dead Letter Queue (DLQ) Management (`/api/v1/admin/queue`)
- `GET  /api/v1/admin/queue/metrics` — [Admin] Real-time outbox & DLQ metrics (counts by status)
- `GET  /api/v1/admin/queue/dlq` — [Admin] Inspect dead-lettered messages and failure reasons
- `POST /api/v1/admin/queue/dlq/{id}/replay` — [Admin] Replay individual dead-lettered message
- `POST /api/v1/admin/queue/dlq/replay-all` — [Admin] Bulk replay all dead-lettered tasks

### 17. Reconciliation, Disputes & Reversals (`/api/v1/disputes`, `/api/v1/admin/reconciliation`)
- `POST /api/v1/disputes/{transactionId}` — Register a dispute on a suspicious/failed transaction
- `GET  /api/v1/disputes/my-disputes` — List active disputes filed by user
- `POST /api/v1/admin/reconciliation/run` — [Admin] Manually trigger stuck transaction reconciliation
- `POST /api/v1/admin/reconciliation/transactions/{id}/reverse` — [Admin] Reverse transaction & refund balances atomically
- `GET  /api/v1/admin/reconciliation/summary` — [Admin] Summary report of pending, disputed, and reversed transactions

### 18. System Health (`/health`, `/api/v1/health`, `/actuator/health`)
- Dynamic health status probing live PostgreSQL connection and Redis cluster connectivity with ISO-8601 UTC timestamp.

---

## 🚀 Getting Started

### Prerequisites
- **Java 21** (JDK 21+)
- **PostgreSQL 15+** running locally on port `5432` (`pockt / pockt`)
- **Redis 7+** running locally on port `6379`

### Run Backend
```bash
cd backend
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### Run All Tests
```bash
cd backend
./gradlew test
```

### Swagger OpenAPI UI
Once started, explore the interactive documentation:
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI Spec: `http://localhost:8080/v3/api-docs`
- Health Probe: `http://localhost:8080/health`

package com.pockt.receipt.internal;

import com.pockt.bill.domain.BillPayment;
import com.pockt.bill.repository.BillPaymentRepository;
import com.pockt.infrastructure.exception.ErrorCode;
import com.pockt.infrastructure.exception.PocktException;
import com.pockt.infrastructure.util.MoneyUtils;
import com.pockt.receipt.dto.ReceiptResponse;
import com.pockt.receipt.service.ReceiptService;
import com.pockt.transfer.domain.Transaction;
import com.pockt.transfer.repository.TransactionRepository;
import com.pockt.user.domain.User;
import com.pockt.user.repository.UserRepository;
import com.pockt.wallet.domain.Wallet;
import com.pockt.wallet.repository.WalletRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class ReceiptServiceImpl implements ReceiptService {

    private final TransactionRepository transactionRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final WalletRepository walletRepository;
    private final UserRepository userRepository;

    public ReceiptServiceImpl(
            TransactionRepository transactionRepository,
            BillPaymentRepository billPaymentRepository,
            WalletRepository walletRepository,
            UserRepository userRepository
    ) {
        this.transactionRepository = transactionRepository;
        this.billPaymentRepository = billPaymentRepository;
        this.walletRepository = walletRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public ReceiptResponse generateTransferReceipt(UUID requestingUserId, UUID transactionId) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new PocktException(ErrorCode.VALIDATION_ERROR, "Transaction not found", HttpStatus.NOT_FOUND));

        Wallet senderWallet = walletRepository.findById(tx.senderWalletId())
                .orElseThrow(() -> new PocktException(ErrorCode.WALLET_NOT_FOUND, "Sender wallet not found", HttpStatus.NOT_FOUND));
        Wallet receiverWallet = walletRepository.findById(tx.receiverWalletId())
                .orElseThrow(() -> new PocktException(ErrorCode.WALLET_NOT_FOUND, "Receiver wallet not found", HttpStatus.NOT_FOUND));

        boolean isParty = senderWallet.userId().equals(requestingUserId) || receiverWallet.userId().equals(requestingUserId);
        if (!isParty) {
            throw new PocktException(ErrorCode.FORBIDDEN, "Not authorized to view receipt for this transaction", HttpStatus.FORBIDDEN);
        }

        User sender = userRepository.findByIdAdmin(senderWallet.userId()).orElse(null);
        User receiver = userRepository.findByIdAdmin(receiverWallet.userId()).orElse(null);

        String receiptId = "REC-TX-" + tx.id().toString().substring(0, 8).toUpperCase();
        String formatted = MoneyUtils.format(tx.amount(), tx.currency());
        String hash = computeHash(tx.id().toString() + ":" + tx.amount() + ":" + tx.createdAt());

        ReceiptResponse base = new ReceiptResponse(
                receiptId,
                tx.idempotencyKey(),
                tx.id(),
                "P2P_TRANSFER",
                tx.createdAt(),
                tx.amount(),
                formatted,
                tx.currency(),
                tx.status().name(),
                sender != null ? sender.fullName() : "Pockt User",
                sender != null ? maskPhone(sender.phone()) : "***",
                receiver != null ? receiver.fullName() : "Recipient",
                receiver != null ? maskPhone(receiver.phone()) : "***",
                tx.category() != null ? tx.category() : "TRANSFER",
                tx.description(),
                hash,
                null
        );

        String html = renderHtmlReceipt(base);
        return new ReceiptResponse(
                base.receiptId(),
                base.referenceNumber(),
                base.transactionId(),
                base.receiptType(),
                base.timestamp(),
                base.amountCents(),
                base.formattedAmount(),
                base.currency(),
                base.status(),
                base.senderName(),
                base.senderIdentifier(),
                base.receiverName(),
                base.receiverIdentifier(),
                base.category(),
                base.notes(),
                base.verificationHash(),
                html
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ReceiptResponse generateBillReceipt(UUID requestingUserId, UUID billPaymentId) {
        BillPayment bp = billPaymentRepository.findById(billPaymentId)
                .orElseThrow(() -> new PocktException(ErrorCode.VALIDATION_ERROR, "Bill payment record not found", HttpStatus.NOT_FOUND));

        if (!bp.userId().equals(requestingUserId)) {
            throw new PocktException(ErrorCode.FORBIDDEN, "Not authorized to view this bill receipt", HttpStatus.FORBIDDEN);
        }

        User user = userRepository.findByIdAdmin(bp.userId()).orElse(null);
        String receiptId = "REC-BILL-" + bp.id().toString().substring(0, 8).toUpperCase();
        String formatted = MoneyUtils.format(bp.amountCents(), "USD");
        String hash = computeHash(bp.referenceNumber() + ":" + bp.amountCents() + ":" + bp.createdAt());

        ReceiptResponse base = new ReceiptResponse(
                receiptId,
                bp.referenceNumber(),
                bp.id(),
                "BILL_PAYMENT",
                bp.createdAt(),
                bp.amountCents(),
                formatted,
                "USD",
                bp.status(),
                user != null ? user.fullName() : "Subscriber",
                user != null ? maskPhone(user.phone()) : "***",
                bp.billerName(),
                "Consumer ID: " + bp.consumerNumber(),
                bp.category(),
                "Utility Bill Payment to " + bp.billerName(),
                hash,
                null
        );

        String html = renderHtmlReceipt(base);
        return new ReceiptResponse(
                base.receiptId(),
                base.referenceNumber(),
                base.transactionId(),
                base.receiptType(),
                base.timestamp(),
                base.amountCents(),
                base.formattedAmount(),
                base.currency(),
                base.status(),
                base.senderName(),
                base.senderIdentifier(),
                base.receiverName(),
                base.receiverIdentifier(),
                base.category(),
                base.notes(),
                base.verificationHash(),
                html
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ReceiptResponse verifyReceipt(String receiptId) {
        if (receiptId == null || !receiptId.startsWith("REC-")) {
            throw new PocktException(ErrorCode.VALIDATION_ERROR, "Invalid receipt ID format", HttpStatus.BAD_REQUEST);
        }

        if (receiptId.startsWith("REC-TX-")) {
            String prefix = receiptId.substring("REC-TX-".length()).toLowerCase();
            var txOpt = transactionRepository.findByIdPrefix(prefix);
            if (txOpt.isPresent()) {
                var tx = txOpt.get();
                var w = walletRepository.findById(tx.senderWalletId()).orElseThrow();
                return generateTransferReceipt(w.userId(), tx.id());
            }
        } else if (receiptId.startsWith("REC-BILL-")) {
            String prefix = receiptId.substring("REC-BILL-".length()).toLowerCase();
            var bpOpt = billPaymentRepository.findByIdPrefix(prefix);
            if (bpOpt.isPresent()) {
                var bp = bpOpt.get();
                return generateBillReceipt(bp.userId(), bp.id());
            }
        }

        throw new PocktException(ErrorCode.VALIDATION_ERROR, "Receipt could not be verified or record was not found", HttpStatus.NOT_FOUND);
    }

    @Override
    public String renderHtmlReceipt(ReceiptResponse r) {
        String statusColor = "COMPLETED".equalsIgnoreCase(r.status()) || "SUCCESS".equalsIgnoreCase(r.status()) ? "#10B981" : "#EF4444";
        String statusBg = "COMPLETED".equalsIgnoreCase(r.status()) || "SUCCESS".equalsIgnoreCase(r.status()) ? "#ECFDF5" : "#FEF2F2";
        String dateStr = DateTimeFormatter.ISO_INSTANT.format(r.timestamp());

        return String.format("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8">
              <title>Official Payment Receipt - %s</title>
              <style>
                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background: #F3F4F6; margin: 0; padding: 32px 16px; }
                .receipt-card { max-width: 480px; margin: 0 auto; background: #FFFFFF; border-radius: 16px; box-shadow: 0 10px 25px rgba(0,0,0,0.06); padding: 32px 28px; border: 1px solid #E5E7EB; }
                .brand { display: flex; align-items: center; justify-content: space-between; border-bottom: 2px dashed #E5E7EB; padding-bottom: 20px; }
                .brand-title { font-size: 20px; font-weight: 800; color: #1E3A8A; letter-spacing: -0.5px; }
                .badge { padding: 4px 10px; border-radius: 9999px; font-size: 11px; font-weight: 700; text-transform: uppercase; background: %s; color: %s; }
                .amount-container { text-align: center; margin: 28px 0; }
                .amount-label { font-size: 12px; font-weight: 600; color: #6B7280; text-transform: uppercase; letter-spacing: 0.5px; }
                .amount { font-size: 34px; font-weight: 800; color: #111827; margin: 6px 0; }
                .data-row { display: flex; justify-content: space-between; font-size: 13px; padding: 10px 0; border-bottom: 1px solid #F3F4F6; }
                .data-label { color: #6B7280; font-weight: 500; }
                .data-value { color: #111827; font-weight: 600; text-align: right; }
                .footer { margin-top: 28px; text-align: center; font-size: 11px; color: #9CA3AF; border-top: 1px solid #F3F4F6; padding-top: 16px; }
                .hash-box { background: #F9FAFB; padding: 8px; border-radius: 8px; font-family: monospace; font-size: 10px; color: #4B5563; word-break: break-all; margin-top: 12px; }
                @media print { body { background: #FFF; padding: 0; } .receipt-card { box-shadow: none; border: none; } }
              </style>
            </head>
            <body>
              <div class="receipt-card">
                <div class="brand">
                  <div class="brand-title">POCKT WALLET 👛</div>
                  <div class="badge">%s</div>
                </div>
                <div class="amount-container">
                  <div class="amount-label">Payment Total</div>
                  <div class="amount">%s</div>
                  <div style="font-size: 11px; color: #9CA3AF;">Receipt ID: %s</div>
                </div>
                <div class="data-row">
                  <span class="data-label">Payment Date & Time</span>
                  <span class="data-value">%s</span>
                </div>
                <div class="data-row">
                  <span class="data-label">Reference Number</span>
                  <span class="data-value">%s</span>
                </div>
                <div class="data-row">
                  <span class="data-label">Sender</span>
                  <span class="data-value">%s (%s)</span>
                </div>
                <div class="data-row">
                  <span class="data-label">Recipient</span>
                  <span class="data-value">%s (%s)</span>
                </div>
                <div class="data-row">
                  <span class="data-label">Category</span>
                  <span class="data-value">%s</span>
                </div>
                <div class="data-row">
                  <span class="data-label">Notes</span>
                  <span class="data-value">%s</span>
                </div>
                <div class="hash-box">
                  Digital Verification Hash:<br>%s
                </div>
                <div class="footer">
                  This is a verified digital payment receipt issued by Pockt Wallet platform.
                </div>
              </div>
            </body>
            </html>
            """,
                r.receiptId(),
                statusBg, statusColor,
                r.status(),
                r.formattedAmount(),
                r.receiptId(),
                dateStr,
                r.referenceNumber(),
                r.senderName(), r.senderIdentifier(),
                r.receiverName(), r.receiverIdentifier(),
                r.category(),
                r.notes() != null ? r.notes() : "N/A",
                r.verificationHash()
        );
    }

    private String computeHash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encoded);
        } catch (NoSuchAlgorithmException e) {
            return UUID.randomUUID().toString();
        }
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 6) return "***";
        return phone.substring(0, 4) + "****" + phone.substring(phone.length() - 2);
    }
}

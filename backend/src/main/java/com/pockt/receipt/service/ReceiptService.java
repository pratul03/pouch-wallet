package com.pockt.receipt.service;

import com.pockt.receipt.dto.ReceiptResponse;

import java.util.UUID;

public interface ReceiptService {
    ReceiptResponse generateTransferReceipt(UUID requestingUserId, UUID transactionId);
    ReceiptResponse generateBillReceipt(UUID requestingUserId, UUID billPaymentId);
    ReceiptResponse verifyReceipt(String receiptId);
    String renderHtmlReceipt(ReceiptResponse receipt);
}

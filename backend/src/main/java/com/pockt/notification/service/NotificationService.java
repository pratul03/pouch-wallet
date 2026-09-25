package com.pockt.notification.service;

import java.util.UUID;

public interface NotificationService {
    void sendTransferSent(UUID senderUserId, long amountCents, String currency, String receiverName);
    void sendTransferReceived(UUID receiverUserId, long amountCents, String currency, String senderName);
    void sendOtp(String phone, String otp);
    void sendEmailReceipt(UUID userId, String recipientEmail, String subject, String htmlContent, String attachmentFilename, byte[] attachmentBytes);
    void processOutboxEntry(UUID outboxId, UUID userId, String type, String payload);
}

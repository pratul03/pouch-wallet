package com.pockt.notification.provider;

public interface EmailProvider {
    void sendEmail(String to, String subject, String htmlContent, String attachmentFilename, byte[] attachmentBytes);
}

package com.pockt.notification.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LogEmailProvider implements EmailProvider {

    private static final Logger log = LoggerFactory.getLogger(LogEmailProvider.class);

    @Override
    public void sendEmail(String to, String subject, String htmlContent, String attachmentFilename, byte[] attachmentBytes) {
        int attachmentSize = attachmentBytes != null ? attachmentBytes.length : 0;
        log.info("[EMAIL DISPATCHED] To='{}', Subject='{}', Attachment='{}' ({} bytes)",
                to, subject, attachmentFilename != null ? attachmentFilename : "NONE", attachmentSize);
        log.debug("[EMAIL BODY PREVIEW] {}", htmlContent != null && htmlContent.length() > 200 ? htmlContent.substring(0, 200) + "..." : htmlContent);
    }
}

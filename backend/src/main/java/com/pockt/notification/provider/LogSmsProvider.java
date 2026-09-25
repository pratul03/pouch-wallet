package com.pockt.notification.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "pockt.notification.sms-provider", havingValue = "log", matchIfMissing = true)
public class LogSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(LogSmsProvider.class);

    @Override
    public void send(String phone, String message) {
        log.info("==================================================");
        log.info("SMS DISPATCH: to={}, message='{}'", phone, message);
        log.info("==================================================");
    }
}

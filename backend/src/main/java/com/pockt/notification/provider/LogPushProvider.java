package com.pockt.notification.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "pockt.notification.push-provider", havingValue = "log", matchIfMissing = true)
public class LogPushProvider implements PushProvider {

    private static final Logger log = LoggerFactory.getLogger(LogPushProvider.class);

    @Override
    public void send(String fcmToken, String title, String body, Map<String, String> data) {
        log.info("==================================================");
        log.info("PUSH NOTIFICATION: token={}, title='{}', body='{}', data={}",
                fcmToken != null ? fcmToken : "<NO_DEVICE_TOKEN>", title, body, data);
        log.info("==================================================");
    }
}

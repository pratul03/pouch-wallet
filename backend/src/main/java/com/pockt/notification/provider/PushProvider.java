package com.pockt.notification.provider;

import java.util.Map;

public interface PushProvider {
    void send(String fcmToken, String title, String body, Map<String, String> data);
}

package com.pockt.notification.provider;

public interface SmsProvider {
    void send(String phone, String message);
}

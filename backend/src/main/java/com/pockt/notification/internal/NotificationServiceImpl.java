package com.pockt.notification.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pockt.infrastructure.util.MoneyUtils;
import com.pockt.notification.provider.PushProvider;
import com.pockt.notification.provider.SmsProvider;
import com.pockt.notification.service.NotificationService;
import com.pockt.user.dto.UserResponse;
import com.pockt.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final PushProvider pushProvider;
    private final SmsProvider smsProvider;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public NotificationServiceImpl(PushProvider pushProvider,
                                   SmsProvider smsProvider,
                                   UserService userService,
                                   ObjectMapper objectMapper) {
        this.pushProvider = pushProvider;
        this.smsProvider = smsProvider;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void sendTransferSent(UUID senderUserId, long amountCents, String currency, String receiverName) {
        try {
            UserResponse sender = userService.getProfile(senderUserId);
            String formatted = MoneyUtils.format(amountCents, currency);
            String title = "Transfer Sent";
            String body = "You sent " + formatted + " to " + receiverName;
            pushProvider.send(null, title, body, Map.of(
                    "type", "TRANSFER_SENT",
                    "amount", String.valueOf(amountCents),
                    "currency", currency
            ));
        } catch (Exception e) {
            log.warn("Failed to dispatch sendTransferSent push: {}", e.getMessage());
        }
    }

    @Override
    public void sendTransferReceived(UUID receiverUserId, long amountCents, String currency, String senderName) {
        try {
            UserResponse receiver = userService.getProfile(receiverUserId);
            String formatted = MoneyUtils.format(amountCents, currency);
            String title = "Transfer Received";
            String body = senderName + " sent you " + formatted;
            pushProvider.send(null, title, body, Map.of(
                    "type", "TRANSFER_RECEIVED",
                    "amount", String.valueOf(amountCents),
                    "currency", currency
            ));
        } catch (Exception e) {
            log.warn("Failed to dispatch sendTransferReceived push: {}", e.getMessage());
        }
    }

    @Override
    public void sendOtp(String phone, String otp) {
        smsProvider.send(phone, "Your Pockt verification code is: " + otp + ". Valid for 5 minutes.");
    }

    @Override
    public void processOutboxEntry(UUID outboxId, UUID userId, String type, String payload) {
        try {
            Map<String, Object> data = objectMapper.readValue(payload, new TypeReference<>() {});
            String title = (String) data.getOrDefault("title", "Wallet Update");
            String body = (String) data.getOrDefault("body", "");

            pushProvider.send(null, title, body, Map.of(
                    "outboxId", outboxId.toString(),
                    "type", type
            ));
        } catch (Exception e) {
            log.error("Failed to process outbox entry {}: {}", outboxId, e.getMessage(), e);
            throw new RuntimeException("Outbox dispatch failure", e);
        }
    }
}

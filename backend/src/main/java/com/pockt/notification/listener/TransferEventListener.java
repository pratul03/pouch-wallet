package com.pockt.notification.listener;

import com.pockt.notification.service.NotificationService;
import com.pockt.transfer.event.TransferCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class TransferEventListener {

    private static final Logger log = LoggerFactory.getLogger(TransferEventListener.class);

    private final NotificationService notificationService;

    public TransferEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @EventListener
    public void onTransferCompleted(TransferCompletedEvent event) {
        log.info("Handling TransferCompletedEvent for tx {}: sender={}, receiver={}",
                event.transactionId(), event.senderUserId(), event.receiverUserId());

        notificationService.sendTransferSent(
                event.senderUserId(),
                event.amountCents(),
                event.currency(),
                event.receiverName()
        );

        notificationService.sendTransferReceived(
                event.receiverUserId(),
                event.amountCents(),
                event.currency(),
                event.senderName()
        );
    }
}

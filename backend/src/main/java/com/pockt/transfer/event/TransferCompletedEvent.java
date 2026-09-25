package com.pockt.transfer.event;

import java.util.UUID;

public record TransferCompletedEvent(
    UUID transactionId,
    UUID senderUserId,
    UUID receiverUserId,
    String senderName,
    String receiverName,
    long amountCents,
    String currency
) {}

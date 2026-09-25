package com.pockt.transfer.service;

import com.pockt.infrastructure.web.PageResponse;
import com.pockt.transfer.dto.TransactionResponse;
import com.pockt.transfer.dto.TransferRequest;
import com.pockt.transfer.dto.TransferResponse;

import java.util.UUID;

public interface TransferService {
    TransferResponse send(TransferRequest request, UUID senderUserId);
    PageResponse<TransactionResponse> getHistory(UUID userId, String cursor, int limit);
    TransactionResponse getById(UUID transactionId, UUID requestingUserId);
}

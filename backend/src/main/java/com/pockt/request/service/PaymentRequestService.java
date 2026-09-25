package com.pockt.request.service;

import com.pockt.request.dto.CreatePaymentRequest;
import com.pockt.request.dto.PaymentRequestResponse;
import com.pockt.request.dto.SplitBillRequest;

import java.util.List;
import java.util.UUID;

public interface PaymentRequestService {
    PaymentRequestResponse createRequest(UUID requesterUserId, CreatePaymentRequest request);
    List<PaymentRequestResponse> createSplitBill(UUID requesterUserId, SplitBillRequest request);
    List<PaymentRequestResponse> getIncomingRequests(UUID userId);
    List<PaymentRequestResponse> getOutgoingRequests(UUID requesterUserId);
    PaymentRequestResponse getRequestById(UUID userId, UUID requestId);
    PaymentRequestResponse acceptRequest(UUID userId, UUID requestId);
    PaymentRequestResponse declineRequest(UUID userId, UUID requestId);
    PaymentRequestResponse cancelRequest(UUID userId, UUID requestId);
}

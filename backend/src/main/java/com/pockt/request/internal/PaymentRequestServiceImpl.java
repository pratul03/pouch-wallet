package com.pockt.request.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pockt.infrastructure.exception.ErrorCode;
import com.pockt.infrastructure.exception.PocktException;
import com.pockt.request.domain.PaymentRequest;
import com.pockt.request.dto.CreatePaymentRequest;
import com.pockt.request.dto.PaymentRequestResponse;
import com.pockt.request.dto.SplitBillRequest;
import com.pockt.request.repository.PaymentRequestRepository;
import com.pockt.request.service.PaymentRequestService;
import com.pockt.transfer.domain.NotificationOutbox;
import com.pockt.transfer.dto.TransferRequest;
import com.pockt.transfer.dto.TransferResponse;
import com.pockt.transfer.repository.OutboxRepository;
import com.pockt.transfer.service.TransferService;
import com.pockt.upi.repository.UpiRepository;
import com.pockt.user.domain.User;
import com.pockt.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentRequestServiceImpl implements PaymentRequestService {

    private static final Logger log = LoggerFactory.getLogger(PaymentRequestServiceImpl.class);

    private final PaymentRequestRepository paymentRequestRepository;
    private final UserRepository userRepository;
    private final UpiRepository upiRepository;
    private final TransferService transferService;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public PaymentRequestServiceImpl(
            PaymentRequestRepository paymentRequestRepository,
            UserRepository userRepository,
            UpiRepository upiRepository,
            TransferService transferService,
            OutboxRepository outboxRepository,
            ObjectMapper objectMapper
    ) {
        this.paymentRequestRepository = paymentRequestRepository;
        this.userRepository = userRepository;
        this.upiRepository = upiRepository;
        this.transferService = transferService;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public PaymentRequestResponse createRequest(UUID requesterUserId, CreatePaymentRequest request) {
        if (!request.hasTarget()) {
            throw new PocktException(ErrorCode.VALIDATION_ERROR, "Either payer phone or UPI VPA must be provided", HttpStatus.BAD_REQUEST);
        }

        UUID payerUserId = resolvePayerUserId(request.payerPhone(), request.payerVpa());
        int hours = (request.expiryHours() != null && request.expiryHours() > 0) ? request.expiryHours() : 72;
        Instant expiresAt = Instant.now().plus(hours, ChronoUnit.HOURS);

        PaymentRequest pr = new PaymentRequest(
                UUID.randomUUID(),
                requesterUserId,
                payerUserId,
                request.payerPhone() != null ? request.payerPhone().trim() : null,
                request.payerVpa() != null ? request.payerVpa().trim().toLowerCase() : null,
                request.amountCents(),
                request.note() != null ? request.note().trim() : null,
                null,
                "PENDING",
                null,
                expiresAt,
                Instant.now(),
                Instant.now()
        );

        paymentRequestRepository.save(pr);
        notifyPayer(pr);

        log.info("Payment request created: id={}, requester={}, amount={}", pr.id(), requesterUserId, pr.amountCents());
        return PaymentRequestResponse.from(pr);
    }

    @Override
    @Transactional
    public List<PaymentRequestResponse> createSplitBill(UUID requesterUserId, SplitBillRequest request) {
        UUID splitGroupId = UUID.randomUUID();
        int hours = (request.expiryHours() != null && request.expiryHours() > 0) ? request.expiryHours() : 72;
        Instant expiresAt = Instant.now().plus(hours, ChronoUnit.HOURS);

        List<PaymentRequestResponse> responses = new ArrayList<>();

        for (SplitBillRequest.ParticipantSplit split : request.splits()) {
            if (!split.hasTarget()) {
                continue;
            }

            UUID payerUserId = resolvePayerUserId(split.phone(), split.vpa());
            String note = request.title() + (split.note() != null && !split.note().isBlank() ? " (" + split.note() + ")" : "");

            PaymentRequest pr = new PaymentRequest(
                    UUID.randomUUID(),
                    requesterUserId,
                    payerUserId,
                    split.phone() != null ? split.phone().trim() : null,
                    split.vpa() != null ? split.vpa().trim().toLowerCase() : null,
                    split.amountCents(),
                    note,
                    splitGroupId,
                    "PENDING",
                    null,
                    expiresAt,
                    Instant.now(),
                    Instant.now()
            );

            paymentRequestRepository.save(pr);
            notifyPayer(pr);
            responses.add(PaymentRequestResponse.from(pr));
        }

        log.info("Split bill created: group={}, title={}, participants={}", splitGroupId, request.title(), responses.size());
        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentRequestResponse> getIncomingRequests(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new PocktException(ErrorCode.USER_NOT_FOUND, "User not found", HttpStatus.NOT_FOUND));

        List<PaymentRequest> list = paymentRequestRepository.findIncomingRequests(userId, user.phone(), null);
        return list.stream().map(PaymentRequestResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentRequestResponse> getOutgoingRequests(UUID requesterUserId) {
        return paymentRequestRepository.findOutgoingRequests(requesterUserId).stream()
                .map(PaymentRequestResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentRequestResponse getRequestById(UUID userId, UUID requestId) {
        PaymentRequest pr = paymentRequestRepository.findById(requestId)
                .orElseThrow(() -> new PocktException(ErrorCode.PAYMENT_REQUEST_NOT_FOUND, "Payment request not found", HttpStatus.NOT_FOUND));

        User user = userRepository.findById(userId).orElse(null);
        boolean isParty = pr.requesterUserId().equals(userId)
                || (pr.payerUserId() != null && pr.payerUserId().equals(userId))
                || (user != null && pr.payerPhone() != null && pr.payerPhone().equals(user.phone()));

        if (!isParty) {
            throw new PocktException(ErrorCode.FORBIDDEN, "Not authorized to view this payment request", HttpStatus.FORBIDDEN);
        }

        return PaymentRequestResponse.from(pr);
    }

    @Override
    @Transactional
    public PaymentRequestResponse acceptRequest(UUID userId, UUID requestId) {
        PaymentRequest pr = paymentRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new PocktException(ErrorCode.PAYMENT_REQUEST_NOT_FOUND, "Payment request not found", HttpStatus.NOT_FOUND));

        User payer = userRepository.findById(userId)
                .orElseThrow(() -> new PocktException(ErrorCode.USER_NOT_FOUND, "User not found", HttpStatus.NOT_FOUND));

        boolean isPayer = (pr.payerUserId() != null && pr.payerUserId().equals(userId))
                || (pr.payerPhone() != null && pr.payerPhone().equals(payer.phone()));

        if (!isPayer) {
            throw new PocktException(ErrorCode.FORBIDDEN, "You are not the designated payer for this request", HttpStatus.FORBIDDEN);
        }

        if (!"PENDING".equalsIgnoreCase(pr.status())) {
            throw new PocktException(ErrorCode.PAYMENT_REQUEST_ALREADY_SETTLED, "Payment request is already " + pr.status(), HttpStatus.CONFLICT);
        }

        if (Instant.now().isAfter(pr.expiresAt())) {
            paymentRequestRepository.updateStatus(requestId, "EXPIRED", null);
            throw new PocktException(ErrorCode.PAYMENT_REQUEST_EXPIRED, "Payment request has expired", HttpStatus.GONE);
        }

        User requester = userRepository.findById(pr.requesterUserId())
                .orElseThrow(() -> new PocktException(ErrorCode.RECEIVER_NOT_FOUND, "Requester account not found", HttpStatus.NOT_FOUND));

        String idempotencyKey = "REQ-" + pr.id();
        String desc = "Settling request: " + (pr.note() != null ? pr.note() : pr.id().toString());
        TransferRequest transferRequest = new TransferRequest(
                requester.phone(),
                pr.amountCents(),
                "USD",
                desc,
                idempotencyKey
        );

        TransferResponse transferResponse = transferService.send(transferRequest, userId);

        paymentRequestRepository.updateStatus(requestId, "ACCEPTED", transferResponse.transactionId());

        PaymentRequest updated = new PaymentRequest(
                pr.id(),
                pr.requesterUserId(),
                userId,
                pr.payerPhone(),
                pr.payerVpa(),
                pr.amountCents(),
                pr.note(),
                pr.splitGroupId(),
                "ACCEPTED",
                transferResponse.transactionId(),
                pr.expiresAt(),
                pr.createdAt(),
                Instant.now()
        );

        log.info("Payment request {} accepted by user {}, txId={}", requestId, userId, transferResponse.transactionId());
        return PaymentRequestResponse.from(updated);
    }

    @Override
    @Transactional
    public PaymentRequestResponse declineRequest(UUID userId, UUID requestId) {
        PaymentRequest pr = paymentRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new PocktException(ErrorCode.PAYMENT_REQUEST_NOT_FOUND, "Payment request not found", HttpStatus.NOT_FOUND));

        User user = userRepository.findById(userId).orElse(null);
        boolean isPayer = (pr.payerUserId() != null && pr.payerUserId().equals(userId))
                || (user != null && pr.payerPhone() != null && pr.payerPhone().equals(user.phone()));

        if (!isPayer) {
            throw new PocktException(ErrorCode.FORBIDDEN, "Not authorized to decline this payment request", HttpStatus.FORBIDDEN);
        }

        if (!"PENDING".equalsIgnoreCase(pr.status())) {
            throw new PocktException(ErrorCode.PAYMENT_REQUEST_ALREADY_SETTLED, "Payment request is already " + pr.status(), HttpStatus.CONFLICT);
        }

        paymentRequestRepository.updateStatus(requestId, "DECLINED", null);
        return getRequestById(userId, requestId);
    }

    @Override
    @Transactional
    public PaymentRequestResponse cancelRequest(UUID userId, UUID requestId) {
        PaymentRequest pr = paymentRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new PocktException(ErrorCode.PAYMENT_REQUEST_NOT_FOUND, "Payment request not found", HttpStatus.NOT_FOUND));

        if (!pr.requesterUserId().equals(userId)) {
            throw new PocktException(ErrorCode.FORBIDDEN, "Only the requester can cancel this request", HttpStatus.FORBIDDEN);
        }

        if (!"PENDING".equalsIgnoreCase(pr.status())) {
            throw new PocktException(ErrorCode.PAYMENT_REQUEST_ALREADY_SETTLED, "Cannot cancel request in status: " + pr.status(), HttpStatus.CONFLICT);
        }

        paymentRequestRepository.updateStatus(requestId, "CANCELLED", null);
        return getRequestById(userId, requestId);
    }

    private UUID resolvePayerUserId(String phone, String vpa) {
        if (phone != null && !phone.isBlank()) {
            var user = userRepository.findByPhone(phone.trim());
            if (user.isPresent()) {
                return user.get().id();
            }
        }
        if (vpa != null && !vpa.isBlank()) {
            var handle = upiRepository.findByVpa(vpa.trim().toLowerCase());
            if (handle.isPresent()) {
                return handle.get().userId();
            }
        }
        return null;
    }

    private void notifyPayer(PaymentRequest pr) {
        if (pr.payerUserId() == null) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "requestId", pr.id().toString(),
                    "amountCents", pr.amountCents(),
                    "requesterUserId", pr.requesterUserId().toString(),
                    "note", pr.note() != null ? pr.note() : ""
            ));
            outboxRepository.save(new NotificationOutbox(
                    UUID.randomUUID(),
                    pr.payerUserId(),
                    "PAYMENT_REQUEST_RECEIVED",
                    payload,
                    "PENDING",
                    0,
                    null,
                    Instant.now()
            ));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize payment request notification payload", e);
        }
    }
}

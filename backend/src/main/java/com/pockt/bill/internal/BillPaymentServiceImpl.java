package com.pockt.bill.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pockt.bill.domain.BillPayment;
import com.pockt.bill.dto.BillDetailsResponse;
import com.pockt.bill.dto.BillPaymentResponse;
import com.pockt.bill.dto.BillerCategoryResponse;
import com.pockt.bill.dto.BillerResponse;
import com.pockt.bill.dto.FetchBillRequest;
import com.pockt.bill.dto.PayBillRequest;
import com.pockt.bill.repository.BillPaymentRepository;
import com.pockt.bill.service.BillPaymentService;
import com.pockt.infrastructure.exception.ErrorCode;
import com.pockt.infrastructure.exception.PocktException;
import com.pockt.transfer.domain.NotificationOutbox;
import com.pockt.transfer.repository.OutboxRepository;
import com.pockt.wallet.domain.Wallet;
import com.pockt.wallet.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BillPaymentServiceImpl implements BillPaymentService {

    private static final Logger log = LoggerFactory.getLogger(BillPaymentServiceImpl.class);

    private final BillPaymentRepository billPaymentRepository;
    private final WalletService walletService;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    private static final List<BillerCategoryResponse> CATEGORIES = List.of(
            new BillerCategoryResponse("MOBILE_RECHARGE", "Mobile Recharge", "Prepaid mobile top-up and data plans", "smartphone"),
            new BillerCategoryResponse("ELECTRICITY", "Electricity", "Power utility bills across state and city boards", "zap"),
            new BillerCategoryResponse("WATER", "Water Bill", "Municipal and metropolitan water boards", "droplet"),
            new BillerCategoryResponse("BROADBAND", "Broadband & Fiber", "High-speed home fiber and internet bills", "wifi"),
            new BillerCategoryResponse("DTH", "DTH / Cable TV", "Direct-to-home satellite TV subscriptions", "tv"),
            new BillerCategoryResponse("GAS", "Piped Gas & Cylinders", "Domestic LPG cylinders and piped gas utilities", "flame")
    );

    private static final Map<String, List<BillerResponse>> BILLERS = new ConcurrentHashMap<>();

    static {
        BILLERS.put("MOBILE_RECHARGE", List.of(
                new BillerResponse("AIRTEL", "Airtel Prepaid", "MOBILE_RECHARGE", "Mobile Number", "e.g. 9876543210"),
                new BillerResponse("JIO", "Reliance Jio", "MOBILE_RECHARGE", "Mobile Number", "e.g. 9876543210"),
                new BillerResponse("VI", "Vodafone Idea", "MOBILE_RECHARGE", "Mobile Number", "e.g. 9876543210"),
                new BillerResponse("VERIZON", "Verizon Wireless", "MOBILE_RECHARGE", "Mobile Number", "e.g. 5550192834")
        ));
        BILLERS.put("ELECTRICITY", List.of(
                new BillerResponse("BESCOM", "BESCOM - Bangalore Electricity", "ELECTRICITY", "Consumer ID", "e.g. 1029384756"),
                new BillerResponse("TATA_POWER", "Tata Power", "ELECTRICITY", "Consumer Number", "e.g. 9000123456"),
                new BillerResponse("PGE", "Pacific Gas & Electric (PG&E)", "ELECTRICITY", "Account Number", "e.g. 1002345678")
        ));
        BILLERS.put("WATER", List.of(
                new BillerResponse("BWSSB", "BWSSB - Bangalore Water Supply", "WATER", "RR Number", "e.g. WTR-98231"),
                new BillerResponse("MUNICIPAL_WATER", "City Water Authority", "WATER", "Consumer ID", "e.g. CW-123456")
        ));
        BILLERS.put("BROADBAND", List.of(
                new BillerResponse("ACT_FIBER", "ACT Fibernet", "BROADBAND", "Account Number", "e.g. 10092384"),
                new BillerResponse("AIRTEL_XSTREAM", "Airtel Xstream Fiber", "BROADBAND", "Landline/DSL Number", "e.g. 08041234567"),
                new BillerResponse("COMCAST", "Xfinity Internet", "BROADBAND", "Account Number", "e.g. 849512345678")
        ));
        BILLERS.put("DTH", List.of(
                new BillerResponse("TATA_PLAY", "Tata Play (Tata Sky)", "DTH", "Subscriber ID", "e.g. 1092837465"),
                new BillerResponse("AIRTEL_DTH", "Airtel Digital TV", "DTH", "Customer ID", "e.g. 3001234567")
        ));
        BILLERS.put("GAS", List.of(
                new BillerResponse("INDANE", "Indane LPG Gas", "GAS", "Consumer Number", "e.g. 7500123456"),
                new BillerResponse("MAHANAGAR_GAS", "Mahanagar Gas Limited", "GAS", "CA Number", "e.g. 2001928374")
        ));
    }

    public BillPaymentServiceImpl(
            BillPaymentRepository billPaymentRepository,
            WalletService walletService,
            OutboxRepository outboxRepository,
            ObjectMapper objectMapper
    ) {
        this.billPaymentRepository = billPaymentRepository;
        this.walletService = walletService;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<BillerCategoryResponse> getCategories() {
        return CATEGORIES;
    }

    @Override
    public List<BillerResponse> getBillers(String category) {
        if (category == null || category.isBlank()) {
            List<BillerResponse> all = new ArrayList<>();
            BILLERS.values().forEach(all::addAll);
            return all;
        }
        return BILLERS.getOrDefault(category.toUpperCase().trim(), List.of());
    }

    private BillerResponse findBiller(String billerId) {
        for (List<BillerResponse> list : BILLERS.values()) {
            for (BillerResponse b : list) {
                if (b.id().equalsIgnoreCase(billerId)) {
                    return b;
                }
            }
        }
        throw new PocktException(ErrorCode.BILLER_NOT_FOUND, "Biller with ID '" + billerId + "' not supported", HttpStatus.NOT_FOUND);
    }

    @Override
    public BillDetailsResponse fetchBill(FetchBillRequest request) {
        BillerResponse biller = findBiller(request.billerId());

        // Deterministic simulated bill amount between $15.00 and $165.00 (in cents: 1500 to 16500)
        int hash = Math.abs((request.billerId() + ":" + request.consumerNumber()).hashCode());
        long amountCents = 1500L + (hash % 15000L);

        LocalDate today = LocalDate.now();
        LocalDate dueDate = today.plusDays(7 + (hash % 14));
        LocalDate billDate = today.minusDays(5 + (hash % 7));

        String customerName = "Subscriber #" + (hash % 9000 + 1000);

        return new BillDetailsResponse(
                biller.id(),
                biller.name(),
                biller.category(),
                request.consumerNumber(),
                customerName,
                amountCents,
                "USD",
                billDate,
                dueDate
        );
    }

    @Override
    @Transactional
    public BillPaymentResponse payBill(UUID userId, PayBillRequest request) {
        if (request.amountCents() <= 0) {
            throw new PocktException(ErrorCode.INVALID_AMOUNT, "Payment amount must be greater than zero", HttpStatus.BAD_REQUEST);
        }

        BillerResponse biller = findBiller(request.billerId());
        Wallet wallet = walletService.getOrCreateWallet(userId, "USD");

        // Debit wallet
        walletService.debit(wallet.id(), request.amountCents());

        UUID txId = UUID.randomUUID();
        String refNumber = "BPAY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();

        BillPayment payment = new BillPayment(
                UUID.randomUUID(),
                userId,
                wallet.id(),
                biller.category(),
                biller.id(),
                biller.name(),
                request.consumerNumber().trim(),
                request.amountCents(),
                "SUCCESS",
                refNumber,
                null,
                Instant.now(),
                Instant.now()
        );

        billPaymentRepository.save(payment);

        // Queue notification outbox
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "billerName", biller.name(),
                    "category", biller.category(),
                    "amountCents", request.amountCents(),
                    "referenceNumber", refNumber,
                    "consumerNumber", request.consumerNumber()
            ));
            outboxRepository.save(new NotificationOutbox(
                    UUID.randomUUID(),
                    userId,
                    "BILL_PAYMENT_SUCCESS",
                    payload,
                    "PENDING",
                    0,
                    null,
                    Instant.now()
            ));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize bill payment notification payload", e);
        }

        log.info("Bill payment processed: user={}, biller={}, amount={}, ref={}",
                userId, biller.name(), request.amountCents(), refNumber);

        return BillPaymentResponse.from(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BillPaymentResponse> getUserBillPayments(UUID userId) {
        return billPaymentRepository.findByUserId(userId).stream()
                .map(BillPaymentResponse::from)
                .toList();
    }
}

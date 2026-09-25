package com.pockt.upi.internal;

import com.pockt.infrastructure.exception.ErrorCode;
import com.pockt.infrastructure.exception.PocktException;
import com.pockt.infrastructure.exception.SelfTransferException;
import com.pockt.infrastructure.exception.UpiHandleNotFoundException;
import com.pockt.infrastructure.util.MoneyUtils;
import com.pockt.transfer.dto.TransferRequest;
import com.pockt.transfer.dto.TransferResponse;
import com.pockt.transfer.service.TransferService;
import com.pockt.upi.domain.UpiHandle;
import com.pockt.upi.dto.CreateUpiHandleRequest;
import com.pockt.upi.dto.UpiHandleResponse;
import com.pockt.upi.dto.UpiPaymentRequest;
import com.pockt.upi.dto.UpiPaymentResponse;
import com.pockt.upi.dto.VerifyVpaResponse;
import com.pockt.upi.repository.UpiRepository;
import com.pockt.upi.service.UpiService;
import com.pockt.user.dto.UserResponse;
import com.pockt.user.service.UserService;
import com.pockt.wallet.dto.WalletResponse;
import com.pockt.wallet.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UpiServiceImpl implements UpiService {

    private static final Logger log = LoggerFactory.getLogger(UpiServiceImpl.class);

    private final UpiRepository upiRepository;
    private final UserService userService;
    private final WalletService walletService;
    private final TransferService transferService;

    public UpiServiceImpl(
            UpiRepository upiRepository,
            UserService userService,
            WalletService walletService,
            TransferService transferService
    ) {
        this.upiRepository = upiRepository;
        this.userService = userService;
        this.walletService = walletService;
        this.transferService = transferService;
    }

    @Override
    @Transactional
    public UpiHandleResponse createHandle(UUID userId, CreateUpiHandleRequest request) {
        String vpa = request.vpa().toLowerCase().trim();
        if (upiRepository.existsByVpa(vpa)) {
            throw new com.pockt.infrastructure.exception.UpiHandleAlreadyExistsException(vpa);
        }

        UUID walletId = request.linkedWalletId();
        if (walletId == null) {
            walletId = walletService.getOrCreateWallet(userId, "USD").id();
        }

        List<UpiHandle> existing = upiRepository.findByUserId(userId);
        boolean isDefault = existing.isEmpty();

        UpiHandle handle = new UpiHandle(
                UUID.randomUUID(),
                userId,
                vpa,
                walletId,
                isDefault,
                Instant.now()
        );

        upiRepository.create(handle);
        log.info("Created UPI handle {} for user {}", vpa, userId);
        return UpiHandleResponse.fromDomain(handle);
    }

    @Override
    @Transactional
    public UpiHandleResponse getOrCreateDefaultHandle(UUID userId) {
        Optional<UpiHandle> defaultOpt = upiRepository.findDefaultByUserId(userId);
        if (defaultOpt.isPresent()) {
            return UpiHandleResponse.fromDomain(defaultOpt.get());
        }

        UserResponse user = userService.getProfile(userId);
        String cleanPhone = user.phone().replaceAll("[^0-9]", "");
        String defaultVpa = cleanPhone + "@pockt";

        Optional<UpiHandle> existingVpa = upiRepository.findByVpa(defaultVpa);
        if (existingVpa.isPresent()) {
            return UpiHandleResponse.fromDomain(existingVpa.get());
        }

        WalletResponse wallet = walletService.getUserWallets(userId).stream()
                .findFirst()
                .orElseGet(() -> walletService.createWallet(userId, "USD"));

        UpiHandle handle = new UpiHandle(
                UUID.randomUUID(),
                userId,
                defaultVpa,
                wallet.id(),
                true,
                Instant.now()
        );

        upiRepository.create(handle);
        log.info("Auto-provisioned default UPI handle {} for user {}", defaultVpa, userId);
        return UpiHandleResponse.fromDomain(handle);
    }

    @Override
    public List<UpiHandleResponse> getUserHandles(UUID userId) {
        return upiRepository.findByUserId(userId).stream()
                .map(UpiHandleResponse::fromDomain)
                .toList();
    }

    @Override
    public VerifyVpaResponse verifyVpa(String vpa) {
        String cleanVpa = vpa != null ? vpa.trim().toLowerCase() : "";
        Optional<UpiHandle> handleOpt = upiRepository.findByVpa(cleanVpa);
        if (handleOpt.isEmpty()) {
            return new VerifyVpaResponse(cleanVpa, null, false);
        }

        UserResponse user = userService.getProfile(handleOpt.get().userId());
        return new VerifyVpaResponse(cleanVpa, user.fullName(), true);
    }

    @Override
    @Transactional
    public UpiPaymentResponse payViaUpi(UUID senderUserId, UpiPaymentRequest request) {
        // 1. Verify PIN
        userService.verifyPin(senderUserId, request.pin());

        // 2. Resolve receiver VPA
        String targetVpa = request.receiverVpa().trim().toLowerCase();
        UpiHandle receiverHandle = upiRepository.findByVpa(targetVpa)
                .orElseThrow(() -> new UpiHandleNotFoundException(targetVpa));

        if (receiverHandle.userId().equals(senderUserId)) {
            throw new SelfTransferException();
        }

        UserResponse receiverUser = userService.getProfile(receiverHandle.userId());
        UpiHandleResponse senderHandle = getOrCreateDefaultHandle(senderUserId);

        // 3. Execute atomic transfer via TransferService
        String description = request.note() != null && !request.note().isBlank()
                ? "UPI: " + request.note()
                : "UPI payment to " + targetVpa;

        TransferRequest transferRequest = new TransferRequest(
                receiverUser.phone(),
                request.amount(),
                "USD",
                description,
                request.idempotencyKey()
        );

        TransferResponse transferResponse = transferService.send(transferRequest, senderUserId);

        return new UpiPaymentResponse(
                transferResponse.transactionId(),
                senderHandle.vpa(),
                targetVpa,
                receiverUser.fullName(),
                request.amount(),
                MoneyUtils.format(request.amount(), "USD"),
                "USD",
                transferResponse.status(),
                request.note(),
                transferResponse.createdAt()
        );
    }
}

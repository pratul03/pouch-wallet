package com.pockt.qr.internal;

import com.pockt.infrastructure.util.MoneyUtils;
import com.pockt.qr.dto.GenerateDynamicQrRequest;
import com.pockt.qr.dto.QrDetailsResponse;
import com.pockt.qr.dto.ScanQrRequest;
import com.pockt.qr.dto.ScanQrResultResponse;
import com.pockt.qr.service.QrService;
import com.pockt.upi.dto.UpiHandleResponse;
import com.pockt.upi.dto.VerifyVpaResponse;
import com.pockt.upi.service.UpiService;
import com.pockt.user.dto.UserResponse;
import com.pockt.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class QrServiceImpl implements QrService {

    private static final Logger log = LoggerFactory.getLogger(QrServiceImpl.class);

    private final UpiService upiService;
    private final UserService userService;

    public QrServiceImpl(UpiService upiService, UserService userService) {
        this.upiService = upiService;
        this.userService = userService;
    }

    @Override
    public QrDetailsResponse getMyQr(UUID userId) {
        UpiHandleResponse handle = upiService.getOrCreateDefaultHandle(userId);
        UserResponse user = userService.getProfile(userId);

        String encodedName = URLEncoder.encode(user.fullName(), StandardCharsets.UTF_8);
        String payload = String.format("upi://pay?pa=%s&pn=%s&cu=USD", handle.vpa(), encodedName);

        return new QrDetailsResponse(
                payload,
                handle.vpa(),
                user.fullName(),
                null,
                null,
                null
        );
    }

    @Override
    public QrDetailsResponse generateDynamicQr(UUID userId, GenerateDynamicQrRequest request) {
        UpiHandleResponse handle = upiService.getOrCreateDefaultHandle(userId);
        UserResponse user = userService.getProfile(userId);

        double decimalAmount = request.amount() / 100.0;
        String formattedAmount = String.format("%.2f", decimalAmount);
        String encodedName = URLEncoder.encode(user.fullName(), StandardCharsets.UTF_8);
        String encodedNote = request.note() != null ? URLEncoder.encode(request.note(), StandardCharsets.UTF_8) : "";

        String payload = String.format("upi://pay?pa=%s&pn=%s&am=%s&cu=USD%s",
                handle.vpa(),
                encodedName,
                formattedAmount,
                encodedNote.isEmpty() ? "" : "&tn=" + encodedNote
        );

        return new QrDetailsResponse(
                payload,
                handle.vpa(),
                user.fullName(),
                request.amount(),
                MoneyUtils.format(request.amount(), "USD"),
                request.note()
        );
    }

    @Override
    public ScanQrResultResponse scanAndResolve(ScanQrRequest request) {
        String raw = request.qrData() != null ? request.qrData().trim() : "";
        if (raw.isEmpty()) {
            return new ScanQrResultResponse(null, null, null, null, null, false, "Empty QR code data");
        }

        String targetVpa = null;
        Long amountCents = null;
        String note = null;

        if (raw.startsWith("upi://pay")) {
            Map<String, String> queryParams = parseQueryParams(raw);
            targetVpa = queryParams.get("pa");
            note = queryParams.get("tn");
            String amStr = queryParams.get("am");
            if (amStr != null && !amStr.isBlank()) {
                try {
                    double am = Double.parseDouble(amStr);
                    amountCents = Math.round(am * 100);
                } catch (NumberFormatException ignored) {}
            }
        } else if (raw.contains("@")) {
            targetVpa = raw;
        } else {
            return new ScanQrResultResponse(null, null, null, null, null, false, "Unsupported QR format. Expecting UPI QR");
        }

        if (targetVpa == null || targetVpa.isBlank()) {
            return new ScanQrResultResponse(null, null, null, null, null, false, "Missing VPA in QR code");
        }

        VerifyVpaResponse verification = upiService.verifyVpa(targetVpa);
        if (!verification.isValid()) {
            return new ScanQrResultResponse(targetVpa, null, null, null, null, false, "Receiver VPA not found on Pockt network");
        }

        String formattedAmount = amountCents != null ? MoneyUtils.format(amountCents, "USD") : null;
        return new ScanQrResultResponse(
                targetVpa,
                verification.accountHolderName(),
                amountCents,
                formattedAmount,
                note,
                true,
                "QR code verified successfully"
        );
    }

    private Map<String, String> parseQueryParams(String uri) {
        Map<String, String> params = new HashMap<>();
        int qIdx = uri.indexOf('?');
        if (qIdx == -1 || qIdx == uri.length() - 1) {
            return params;
        }

        String query = uri.substring(qIdx + 1);
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int eqIdx = pair.indexOf('=');
            if (eqIdx > 0) {
                String key = URLDecoder.decode(pair.substring(0, eqIdx), StandardCharsets.UTF_8);
                String val = URLDecoder.decode(pair.substring(eqIdx + 1), StandardCharsets.UTF_8);
                params.put(key, val);
            }
        }
        return params;
    }
}

package com.pockt.qr.service;

import com.pockt.qr.dto.GenerateDynamicQrRequest;
import com.pockt.qr.dto.QrDetailsResponse;
import com.pockt.qr.dto.ScanQrRequest;
import com.pockt.qr.dto.ScanQrResultResponse;

import java.util.UUID;

public interface QrService {
    QrDetailsResponse getMyQr(UUID userId);
    QrDetailsResponse generateDynamicQr(UUID userId, GenerateDynamicQrRequest request);
    ScanQrResultResponse scanAndResolve(ScanQrRequest request);
}

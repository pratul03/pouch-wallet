package com.pockt.upi.service;

import com.pockt.upi.dto.CreateUpiHandleRequest;
import com.pockt.upi.dto.UpiHandleResponse;
import com.pockt.upi.dto.UpiPaymentRequest;
import com.pockt.upi.dto.UpiPaymentResponse;
import com.pockt.upi.dto.VerifyVpaResponse;

import java.util.List;
import java.util.UUID;

public interface UpiService {
    UpiHandleResponse createHandle(UUID userId, CreateUpiHandleRequest request);
    UpiHandleResponse getOrCreateDefaultHandle(UUID userId);
    List<UpiHandleResponse> getUserHandles(UUID userId);
    VerifyVpaResponse verifyVpa(String vpa);
    UpiPaymentResponse payViaUpi(UUID senderUserId, UpiPaymentRequest request);
}

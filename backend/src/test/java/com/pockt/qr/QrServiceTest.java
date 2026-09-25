package com.pockt.qr;

import com.pockt.qr.dto.GenerateDynamicQrRequest;
import com.pockt.qr.dto.QrDetailsResponse;
import com.pockt.qr.dto.ScanQrRequest;
import com.pockt.qr.dto.ScanQrResultResponse;
import com.pockt.qr.internal.QrServiceImpl;
import com.pockt.upi.dto.UpiHandleResponse;
import com.pockt.upi.dto.VerifyVpaResponse;
import com.pockt.upi.service.UpiService;
import com.pockt.user.dto.UserResponse;
import com.pockt.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QrServiceTest {

    @Mock private UpiService upiService;
    @Mock private UserService userService;

    private QrServiceImpl qrService;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        qrService = new QrServiceImpl(upiService, userService);
    }

    @Test
    void getMyQr_shouldReturnStaticQrPayload() {
        UpiHandleResponse handle = new UpiHandleResponse(UUID.randomUUID(), userId, "1234567890@pockt", UUID.randomUUID(), true, Instant.now());
        UserResponse user = new UserResponse(userId, "+1234567890", "Ade Bello", "VERIFIED", Instant.now());

        when(upiService.getOrCreateDefaultHandle(userId)).thenReturn(handle);
        when(userService.getProfile(userId)).thenReturn(user);

        QrDetailsResponse response = qrService.getMyQr(userId);

        assertThat(response).isNotNull();
        assertThat(response.vpa()).isEqualTo("1234567890@pockt");
        assertThat(response.qrPayload()).contains("upi://pay?pa=1234567890@pockt");
    }

    @Test
    void generateDynamicQr_shouldIncludeAmountAndNote() {
        UpiHandleResponse handle = new UpiHandleResponse(UUID.randomUUID(), userId, "1234567890@pockt", UUID.randomUUID(), true, Instant.now());
        UserResponse user = new UserResponse(userId, "+1234567890", "Ade Bello", "VERIFIED", Instant.now());

        when(upiService.getOrCreateDefaultHandle(userId)).thenReturn(handle);
        when(userService.getProfile(userId)).thenReturn(user);

        GenerateDynamicQrRequest request = new GenerateDynamicQrRequest(1550L, "Coffee");
        QrDetailsResponse response = qrService.generateDynamicQr(userId, request);

        assertThat(response.qrPayload()).contains("am=15.50");
        assertThat(response.qrPayload()).contains("tn=Coffee");
        assertThat(response.amount()).isEqualTo(1550L);
    }

    @Test
    void scanAndResolve_shouldParseUpiUriCorrectly() {
        when(upiService.verifyVpa("bob@pockt")).thenReturn(new VerifyVpaResponse("bob@pockt", "Bob Jones", true));

        ScanQrRequest request = new ScanQrRequest("upi://pay?pa=bob@pockt&pn=Bob+Jones&am=20.00&tn=Dinner");
        ScanQrResultResponse response = qrService.scanAndResolve(request);

        assertThat(response.isValid()).isTrue();
        assertThat(response.vpa()).isEqualTo("bob@pockt");
        assertThat(response.name()).isEqualTo("Bob Jones");
        assertThat(response.amount()).isEqualTo(2000L);
    }
}

package com.pockt.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pockt.infrastructure.web.ApiResponse;
import com.pockt.infrastructure.web.PageResponse;
import com.pockt.transfer.dto.TransactionResponse;
import com.pockt.transfer.dto.TransferRequest;
import com.pockt.transfer.dto.TransferResponse;
import com.pockt.user.domain.OtpPurpose;
import com.pockt.user.domain.OtpVerification;
import com.pockt.user.dto.LoginRequest;
import com.pockt.user.dto.OtpSendRequest;
import com.pockt.user.dto.RefreshTokenRequest;
import com.pockt.user.dto.RegisterRequest;
import com.pockt.user.dto.TokenResponse;
import com.pockt.user.dto.UserResponse;
import com.pockt.user.repository.OtpRepository;
import com.pockt.wallet.dto.TopUpRequest;
import com.pockt.wallet.dto.WalletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FullFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OtpRepository otpRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void fullEndToEndFlow_userRegistrationToTransferAndHistory() throws Exception {
        // Step 1: User 1 (Sender) sends OTP
        String senderPhone = "+2348" + (10000000 + (int) (Math.random() * 89999999));
        OtpSendRequest otpSendReq = new OtpSendRequest(senderPhone, OtpPurpose.REGISTRATION);

        mockMvc.perform(post("/api/v1/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(otpSendReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Directly store a known OTP for verification in test
        String plainOtp = "123456";
        otpRepository.save(new OtpVerification(
                UUID.randomUUID(),
                senderPhone,
                passwordEncoder.encode(plainOtp),
                OtpPurpose.REGISTRATION,
                Instant.now().plusSeconds(300),
                false,
                Instant.now()
        ));

        // Step 2: Verify OTP -> get temp token
        MvcResult otpVerifyResult = mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", senderPhone,
                                "otp", plainOtp,
                                "purpose", "REGISTRATION"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<Map<String, String>> verifyResp = objectMapper.readValue(
                otpVerifyResult.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );
        String tempToken = verifyResp.data().get("tempToken");
        assertThat(tempToken).isNotBlank();

        // Step 3: Register User 1
        RegisterRequest registerReq = new RegisterRequest("Alice Sender", "123456");
        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .header("Authorization", "Bearer " + tempToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andReturn();

        ApiResponse<TokenResponse> tokenResp = objectMapper.readValue(
                registerResult.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );
        String aliceToken = tokenResp.data().accessToken();
        String aliceRefreshToken = tokenResp.data().refreshToken();
        UUID aliceId = tokenResp.data().userId();

        // Step 4: Register User 2 (Receiver) directly
        String receiverPhone = "+2349" + (10000000 + (int) (Math.random() * 89999999));
        otpRepository.save(new OtpVerification(
                UUID.randomUUID(),
                receiverPhone,
                passwordEncoder.encode("654321"),
                OtpPurpose.REGISTRATION,
                Instant.now().plusSeconds(300),
                false,
                Instant.now()
        ));

        MvcResult bobVerifyResult = mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", receiverPhone,
                                "otp", "654321",
                                "purpose", "REGISTRATION"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<Map<String, String>> bobVerifyResp = objectMapper.readValue(
                bobVerifyResult.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );
        String bobTempToken = bobVerifyResp.data().get("tempToken");

        mockMvc.perform(post("/api/v1/auth/register")
                        .header("Authorization", "Bearer " + bobTempToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("Bob Receiver", "654321"))))
                .andExpect(status().isCreated());

        // Step 5: Check Alice's default wallet was created automatically
        MvcResult walletsResult = mockMvc.perform(get("/api/v1/wallets")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<List<WalletResponse>> walletsResp = objectMapper.readValue(
                walletsResult.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );
        assertThat(walletsResp.data()).hasSize(1);
        UUID aliceWalletId = walletsResp.data().getFirst().id();
        assertThat(walletsResp.data().getFirst().balance()).isEqualTo(0L);

        // Step 6: Top-up Alice's wallet with $50.00 (5000 cents)
        mockMvc.perform(post("/api/v1/wallets/" + aliceWalletId + "/topup")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TopUpRequest(5000L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(5000L));

        // Step 7: Search Bob by phone
        mockMvc.perform(get("/api/v1/users/search?phone=" + receiverPhone)
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Bob Receiver"));

        // Step 8: Transfer $20.00 (2000 cents) to Bob
        String idempotencyKey = UUID.randomUUID().toString();
        TransferRequest transferReq = new TransferRequest(receiverPhone, 2000L, "USD", "Dinner split", idempotencyKey);

        MvcResult transferResult = mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + aliceToken)
                        .header("X-Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.amount").value(2000L))
                .andExpect(jsonPath("$.data.senderBalanceAfter").value(3000L))
                .andReturn();

        ApiResponse<TransferResponse> txResp = objectMapper.readValue(
                transferResult.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );
        UUID txId = txResp.data().transactionId();

        // Step 9: Duplicate transfer with same idempotency key returns cached response
        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + aliceToken)
                        .header("X-Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactionId").value(txId.toString()))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        // Step 10: Check Alice's wallet balance is now $30.00
        mockMvc.perform(get("/api/v1/wallets/" + aliceWalletId)
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(3000L));

        // Step 11: Transaction history contains the debit
        mockMvc.perform(get("/api/v1/transfers?limit=10")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].type").value("DEBIT"))
                .andExpect(jsonPath("$.data.items[0].amount").value(2000L))
                .andExpect(jsonPath("$.data.items[0].counterpartyName").value("Bob Receiver"));

        // Step 12: Rotate refresh token
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(aliceRefreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        ApiResponse<TokenResponse> newTokens = objectMapper.readValue(
                refreshResult.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );
        String rotatedRefreshToken = newTokens.data().refreshToken();

        // Step 13: Logout
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(rotatedRefreshToken))))
                .andExpect(status().isNoContent());
    }
}

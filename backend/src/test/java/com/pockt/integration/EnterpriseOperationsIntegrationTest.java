package com.pockt.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pockt.infrastructure.security.JwtService;
import com.pockt.infrastructure.web.ApiResponse;
import com.pockt.receipt.dto.ReceiptResponse;
import com.pockt.reconciliation.dto.DisputeRequest;
import com.pockt.reconciliation.dto.ReversalResponse;
import com.pockt.transfer.domain.NotificationOutbox;
import com.pockt.transfer.dto.TransferRequest;
import com.pockt.transfer.dto.TransferResponse;
import com.pockt.transfer.repository.OutboxRepository;
import com.pockt.user.domain.User;
import com.pockt.user.dto.RegisterRequest;
import com.pockt.user.dto.TokenResponse;
import com.pockt.user.repository.UserRepository;
import com.pockt.wallet.dto.TopUpRequest;
import com.pockt.wallet.dto.WalletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
class EnterpriseOperationsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private OutboxRepository outboxRepository;
    @Autowired private JwtService jwtService;

    record UserSession(UUID id, String phone, String token) {}

    private UserSession createTestUser(String fullName) throws Exception {
        String phone = "+91" + (9000000000L + (long) (Math.random() * 999999999L));
        String rawPin = "123456";
        String tempToken = jwtService.generateTempToken(phone);

        RegisterRequest regReq = new RegisterRequest(fullName, rawPin);
        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .header("Authorization", "Bearer " + tempToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isCreated())
                .andReturn();

        TokenResponse tokenResp = extractData(regResult, new TypeReference<>() {});
        return new UserSession(tokenResp.userId(), phone, tokenResp.accessToken());
    }

    private String createAdminToken() {
        UUID adminId = UUID.randomUUID();
        User adminUser = new User(
                adminId,
                "+1000" + (1000000 + (int)(Math.random() * 8999999)),
                "System Admin",
                "dummyhash",
                null,
                "VERIFIED",
                1,
                "ADMIN",
                true,
                Instant.now(),
                Instant.now()
        );
        userRepository.create(adminUser);
        return jwtService.generateAccessToken(adminId, adminUser.phone(), "ADMIN");
    }

    private void topUpWallet(UserSession user, long amountCents) throws Exception {
        MvcResult walletsRes = mockMvc.perform(get("/api/v1/wallets")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andReturn();
        List<WalletResponse> wallets = extractData(walletsRes, new TypeReference<>() {});
        UUID walletId = wallets.get(0).id();

        TopUpRequest req = new TopUpRequest(amountCents);
        mockMvc.perform(post("/api/v1/wallets/" + walletId + "/topup")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void testReceiptGenerationAndEmailingWorkflow() throws Exception {
        UserSession sender = createTestUser("Sender Samuel");
        UserSession receiver = createTestUser("Receiver Rachel");
        topUpWallet(sender, 20000L); // $200.00

        // 1. Execute P2P Transfer ($45.00)
        TransferRequest txReq = new TransferRequest(receiver.phone(), 4500L, "USD", "Project payment", UUID.randomUUID().toString());
        MvcResult txRes = mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + sender.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(txReq)))
                .andExpect(status().isOk())
                .andReturn();

        TransferResponse transfer = extractData(txRes, new TypeReference<>() {});
        UUID txId = transfer.transactionId();

        // 2. Generate Official Receipt
        MvcResult receiptRes = mockMvc.perform(get("/api/v1/transfers/" + txId + "/receipt")
                        .header("Authorization", "Bearer " + sender.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receiptId").isNotEmpty())
                .andExpect(jsonPath("$.data.amountCents").value(4500L))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.verificationHash").isNotEmpty())
                .andReturn();

        ReceiptResponse receipt = extractData(receiptRes, new TypeReference<>() {});
        assertThat(receipt.receiptId()).startsWith("REC-TX-");
        assertThat(receipt.htmlReceipt()).contains("Official Payment Receipt");

        // 3. Verify Receipt Publicly
        mockMvc.perform(get("/api/v1/receipts/" + receipt.receiptId() + "/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receiptId").value(receipt.receiptId()))
                .andExpect(jsonPath("$.data.amountCents").value(4500L));

        // 4. Send Receipt via Email with Attachment
        mockMvc.perform(post("/api/v1/transfers/" + txId + "/send-receipt-email?email=samuel@example.com")
                        .header("Authorization", "Bearer " + sender.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISPATCHED"))
                .andExpect(jsonPath("$.data.recipient").value("samuel@example.com"));
    }

    @Test
    void testQueueManagementAndDeadLetterQueueWorkflow() throws Exception {
        String adminToken = createAdminToken();
        UserSession user = createTestUser("Queue Test User");

        // 1. Insert a failed outbox task
        UUID deadLetterId = UUID.randomUUID();
        outboxRepository.save(new NotificationOutbox(
                deadLetterId,
                user.id(),
                "TRANSFER_SENT",
                "{\"bad\":\"payload\"}",
                "DEAD_LETTER",
                5,
                null,
                "Simulated connection timeout to FCM gateway",
                Instant.now(),
                Instant.now()
        ));

        // 2. Admin inspects queue metrics
        mockMvc.perform(get("/api/v1/admin/queue/metrics")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deadLetterCount").isNumber());

        // 3. Inspect DLQ messages
        mockMvc.perform(get("/api/v1/admin/queue/dead-letter")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").isNumber());

        // 4. Retry DLQ message
        mockMvc.perform(post("/api/v1/admin/queue/dead-letter/" + deadLetterId + "/retry")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.retried").value(true));
    }

    @Test
    void testDisputeAndReversalWorkflow() throws Exception {
        UserSession sender = createTestUser("Sender Oliver");
        UserSession receiver = createTestUser("Receiver Peter");
        String adminToken = createAdminToken();

        topUpWallet(sender, 30000L); // $300.00

        // 1. Transfer $50.00
        TransferRequest txReq = new TransferRequest(receiver.phone(), 5000L, "USD", "Erroneous charge", UUID.randomUUID().toString());
        MvcResult txRes = mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + sender.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(txReq)))
                .andExpect(status().isOk())
                .andReturn();

        TransferResponse transfer = extractData(txRes, new TypeReference<>() {});
        UUID txId = transfer.transactionId();

        // 2. Oliver disputes the transaction
        DisputeRequest disputeReq = new DisputeRequest("Sent money to wrong contact by accident");
        mockMvc.perform(post("/api/v1/transfers/" + txId + "/dispute")
                        .header("Authorization", "Bearer " + sender.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(disputeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISPUTED"));

        // 3. Admin investigates and reverses the transaction
        MvcResult revRes = mockMvc.perform(post("/api/v1/admin/transfers/" + txId + "/reverse?reason=Approved reversal following dispute investigation")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newStatus").value("REVERSED"))
                .andExpect(jsonPath("$.data.amountCents").value(5000L))
                .andReturn();

        ReversalResponse reversal = extractData(revRes, new TypeReference<>() {});
        assertThat(reversal.newStatus()).isEqualTo("REVERSED");

        // 4. Verify Sender got refunded
        mockMvc.perform(get("/api/v1/wallets")
                        .header("Authorization", "Bearer " + sender.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].balance").value(30000L)); // full 30,000 cents restored!

        // 5. Test manual trigger of auto-reconciliation job
        mockMvc.perform(post("/api/v1/admin/reconciliation/run?olderThanMinutes=1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timestamp").isNotEmpty());
    }

    private <T> T extractData(MvcResult result, TypeReference<ApiResponse<T>> ref) throws Exception {
        ApiResponse<T> resp = objectMapper.readValue(result.getResponse().getContentAsString(), ref);
        return resp.data();
    }
}

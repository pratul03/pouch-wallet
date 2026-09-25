package com.pockt.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pockt.analytics.dto.SpendingSummaryResponse;
import com.pockt.beneficiary.dto.AddBeneficiaryRequest;
import com.pockt.beneficiary.dto.BeneficiaryResponse;
import com.pockt.bill.dto.BillDetailsResponse;
import com.pockt.bill.dto.BillPaymentResponse;
import com.pockt.bill.dto.BillerCategoryResponse;
import com.pockt.bill.dto.FetchBillRequest;
import com.pockt.bill.dto.PayBillRequest;
import com.pockt.infrastructure.security.JwtService;
import com.pockt.infrastructure.web.ApiResponse;
import com.pockt.kyc.dto.KycStatusResponse;
import com.pockt.kyc.dto.KycVerificationResponse;
import com.pockt.kyc.dto.ReviewKycRequest;
import com.pockt.kyc.dto.SubmitKycRequest;
import com.pockt.request.dto.CreatePaymentRequest;
import com.pockt.request.dto.PaymentRequestResponse;
import com.pockt.request.dto.SplitBillRequest;
import com.pockt.reward.dto.RewardsSummaryResponse;
import com.pockt.reward.dto.ScratchCardResponse;
import com.pockt.reward.service.RewardService;
import com.pockt.user.domain.User;
import com.pockt.user.dto.RegisterRequest;
import com.pockt.user.dto.TokenResponse;
import com.pockt.user.repository.UserRepository;
import com.pockt.wallet.dto.TopUpRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaytmConsumerFeaturesIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;
    @Autowired private RewardService rewardService;

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
                java.time.Instant.now(),
                java.time.Instant.now()
        );
        userRepository.create(adminUser);
        return jwtService.generateAccessToken(adminId, adminUser.phone(), "ADMIN");
    }

    private void topUpWallet(UserSession user, long amountCents) throws Exception {
        MvcResult walletsRes = mockMvc.perform(get("/api/v1/wallets")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andReturn();
        List<com.pockt.wallet.dto.WalletResponse> wallets = extractData(walletsRes, new TypeReference<>() {});
        UUID walletId = wallets.get(0).id();

        TopUpRequest req = new TopUpRequest(amountCents);
        mockMvc.perform(post("/api/v1/wallets/" + walletId + "/topup")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void testBeneficiariesFullWorkflow() throws Exception {
        UserSession user = createTestUser("Alice Beneficiary");

        // 1. Add Beneficiary (Phone)
        AddBeneficiaryRequest addReq = new AddBeneficiaryRequest(
                "Bob Friend",
                "Bobby",
                "+12345678901",
                null,
                null,
                null,
                false
        );

        MvcResult addResult = mockMvc.perform(post("/api/v1/beneficiaries")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Bob Friend"))
                .andExpect(jsonPath("$.data.phone").value("+12345678901"))
                .andReturn();

        BeneficiaryResponse b1 = extractData(addResult, new TypeReference<>() {});
        UUID b1Id = b1.id();

        // 2. Add Beneficiary (UPI VPA) with Favorite = true
        AddBeneficiaryRequest addVpaReq = new AddBeneficiaryRequest(
                "Charlie Merchant",
                "Chaz",
                null,
                "charlie@pockt",
                null,
                null,
                true
        );
        mockMvc.perform(post("/api/v1/beneficiaries")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addVpaReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.isFavorite").value(true));

        // 3. List beneficiaries
        mockMvc.perform(get("/api/v1/beneficiaries")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        // 4. Toggle Favorite
        mockMvc.perform(patch("/api/v1/beneficiaries/" + b1Id + "/favorite?favorite=true")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isFavorite").value(true));

        // 5. Delete Beneficiary
        mockMvc.perform(delete("/api/v1/beneficiaries/" + b1Id)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk());

        // Verify remaining count
        mockMvc.perform(get("/api/v1/beneficiaries")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void testBillPaymentsWorkflow() throws Exception {
        UserSession user = createTestUser("Dave Utility Payer");
        topUpWallet(user, 50000L); // $500.00

        // 1. Get Categories
        mockMvc.perform(get("/api/v1/bills/categories")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(6));

        // 2. Get Billers for ELECTRICITY
        mockMvc.perform(get("/api/v1/bills/billers?category=ELECTRICITY")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("BESCOM"));

        // 3. Fetch Bill
        FetchBillRequest fetchReq = new FetchBillRequest("BESCOM", "1029384756");
        MvcResult fetchRes = mockMvc.perform(post("/api/v1/bills/fetch")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fetchReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.billerId").value("BESCOM"))
                .andReturn();

        BillDetailsResponse billDetails = extractData(fetchRes, new TypeReference<>() {});
        assertThat(billDetails.amountCents()).isGreaterThan(0);

        // 4. Pay Bill
        PayBillRequest payReq = new PayBillRequest("BESCOM", "1029384756", 4500L, "May Electricity Bill");
        MvcResult payRes = mockMvc.perform(post("/api/v1/bills/pay")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.amountCents").value(4500L))
                .andReturn();

        BillPaymentResponse payment = extractData(payRes, new TypeReference<>() {});
        assertThat(payment.referenceNumber()).startsWith("BPAY-");

        // 5. History
        mockMvc.perform(get("/api/v1/bills/history")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].billerId").value("BESCOM"))
                .andExpect(jsonPath("$.data[0].amountCents").value(4500L));
    }

    @Test
    void testPaymentRequestsAndSplitBillWorkflow() throws Exception {
        UserSession requester = createTestUser("Requester Emma");
        UserSession payer1 = createTestUser("Payer Frank");
        UserSession payer2 = createTestUser("Payer Grace");

        topUpWallet(payer1, 20000L); // $200.00
        topUpWallet(payer2, 20000L); // $200.00

        // 1. Single Payment Request: Emma requests $30 (3000 cents) from Frank
        CreatePaymentRequest singleReq = new CreatePaymentRequest(payer1.phone(), null, 3000L, "Dinner share", 48);
        MvcResult singleRes = mockMvc.perform(post("/api/v1/payment-requests")
                        .header("Authorization", "Bearer " + requester.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(singleReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.amountCents").value(3000L))
                .andReturn();

        PaymentRequestResponse pr1 = extractData(singleRes, new TypeReference<>() {});

        // 2. Frank checks incoming requests
        mockMvc.perform(get("/api/v1/payment-requests/incoming")
                        .header("Authorization", "Bearer " + payer1.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(pr1.id().toString()))
                .andExpect(jsonPath("$.data[0].amountCents").value(3000L));

        // 3. Frank accepts request -> instant atomic transfer
        mockMvc.perform(post("/api/v1/payment-requests/" + pr1.id() + "/accept")
                        .header("Authorization", "Bearer " + payer1.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.transactionId").isNotEmpty());

        // 4. Split Bill: Emma splits a $40 dinner bill with Frank and Grace ($20 each)
        SplitBillRequest splitReq = new SplitBillRequest(
                "Weekend BBQ",
                List.of(
                        new SplitBillRequest.ParticipantSplit(payer1.phone(), null, 2000L, "Steak & Drinks"),
                        new SplitBillRequest.ParticipantSplit(payer2.phone(), null, 2000L, "Steak & Drinks")
                ),
                72
        );

        MvcResult splitRes = mockMvc.perform(post("/api/v1/payment-requests/split")
                        .header("Authorization", "Bearer " + requester.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(splitReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andReturn();

        List<PaymentRequestResponse> splitList = extractData(splitRes, new TypeReference<>() {});
        UUID graceReqId = splitList.get(1).id();

        // Grace declines request
        mockMvc.perform(post("/api/v1/payment-requests/" + graceReqId + "/decline")
                        .header("Authorization", "Bearer " + payer2.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DECLINED"));
    }

    @Test
    void testKycSubmissionAndAdminReviewWorkflow() throws Exception {
        UserSession user = createTestUser("Harry KYC Applicant");
        String adminToken = createAdminToken();

        // 1. Initial KYC Status (Tier 0)
        mockMvc.perform(get("/api/v1/kyc/status")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kycTier").value(0))
                .andExpect(jsonPath("$.data.maxSingleTransferCents").value(25000L)); // $250.00

        // 2. Submit KYC Documents
        SubmitKycRequest submitReq = new SubmitKycRequest("PASSPORT", "P12345678", "https://docs.pockt.io/passport/harry.png");
        mockMvc.perform(post("/api/v1/kyc/submit")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.documentNumber").value("P12345678"));

        // 3. Admin lists pending submissions
        MvcResult pendingRes = mockMvc.perform(get("/api/v1/admin/kyc/pending")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        List<KycVerificationResponse> pendingList = extractData(pendingRes, new TypeReference<>() {});
        KycVerificationResponse harryKyc = pendingList.stream()
                .filter(k -> k.userId().equals(user.id()))
                .findFirst()
                .orElseThrow();

        // 4. Admin approves KYC
        ReviewKycRequest reviewReq = new ReviewKycRequest("APPROVED", "All documents verified successfully");
        mockMvc.perform(post("/api/v1/admin/kyc/" + harryKyc.id() + "/review")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        // 5. User checks upgraded KYC status (Tier 1)
        mockMvc.perform(get("/api/v1/kyc/status")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kycStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.data.kycTier").value(1))
                .andExpect(jsonPath("$.data.maxSingleTransferCents").value(2500000L)); // $25,000.00
    }

    @Test
    void testRewardsScratchCardsWorkflow() throws Exception {
        UserSession user = createTestUser("Ian Reward Winner");

        // 1. Issue a Scratch Card with $5.50 (550 cents) cashback
        rewardService.issueReward(user.id(), "Welcome Bonus!", "Thank you for using Pockt", 550L, null);

        // 2. Check rewards summary
        MvcResult summaryRes = mockMvc.perform(get("/api/v1/rewards/summary")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unscratchedCount").value(1))
                .andExpect(jsonPath("$.data.totalCashbackEarnedCents").value(0L)) // 0 until scratched
                .andReturn();

        RewardsSummaryResponse summary = extractData(summaryRes, new TypeReference<>() {});
        ScratchCardResponse card = summary.cards().get(0);
        assertThat(card.rewardAmountCents()).isEqualTo(0L); // hidden before scratching!

        // 3. Scratch the card
        mockMvc.perform(post("/api/v1/rewards/scratch-cards/" + card.id() + "/scratch")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isScratched").value(true))
                .andExpect(jsonPath("$.data.rewardAmountCents").value(550L));

        // 4. Verify wallet balance credited with cashback!
        mockMvc.perform(get("/api/v1/wallets")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].balance").value(550L));

        // 5. Check rewards summary again -> total cashback now 550
        mockMvc.perform(get("/api/v1/rewards/summary")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unscratchedCount").value(0))
                .andExpect(jsonPath("$.data.totalCashbackEarnedCents").value(550L));
    }

    @Test
    void testSpendingAnalyticsWorkflow() throws Exception {
        UserSession user = createTestUser("Julia Analytics User");
        topUpWallet(user, 50000L); // $500.00

        // Pay a bill ($35.00)
        PayBillRequest payReq = new PayBillRequest("BESCOM", "9876543210", 3500L, "Analytics bill test");
        mockMvc.perform(post("/api/v1/bills/pay")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payReq)))
                .andExpect(status().isCreated());

        // Check spending analytics
        mockMvc.perform(get("/api/v1/analytics/spending")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.month").isNumber())
                .andExpect(jsonPath("$.data.year").isNumber());
    }

    private <T> T extractData(MvcResult result, TypeReference<ApiResponse<T>> ref) throws Exception {
        ApiResponse<T> resp = objectMapper.readValue(result.getResponse().getContentAsString(), ref);
        return resp.data();
    }
}

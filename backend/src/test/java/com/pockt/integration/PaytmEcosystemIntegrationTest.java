package com.pockt.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pockt.admin.dto.AdminOverviewReportResponse;
import com.pockt.admin.dto.AdminUserDossierResponse;
import com.pockt.admin.dto.AdminUserFinancialsResponse;
import com.pockt.bank.dto.AddMoneyFromBankRequest;
import com.pockt.bank.dto.BankAccountResponse;
import com.pockt.bank.dto.BankTransactionResponse;
import com.pockt.bank.dto.LinkBankAccountRequest;
import com.pockt.bank.dto.WithdrawToBankRequest;
import com.pockt.card.dto.ApplyCreditCardRequest;
import com.pockt.card.dto.CardChargeRequest;
import com.pockt.card.dto.CardChargeResponse;
import com.pockt.card.dto.CardResponse;
import com.pockt.card.dto.CardRevealResponse;
import com.pockt.card.dto.CreditAccountResponse;
import com.pockt.card.dto.IssueDebitCardRequest;
import com.pockt.card.dto.RevealCardRequest;
import com.pockt.infrastructure.security.JwtService;
import com.pockt.infrastructure.web.ApiResponse;
import com.pockt.qr.dto.GenerateDynamicQrRequest;
import com.pockt.qr.dto.QrDetailsResponse;
import com.pockt.qr.dto.ScanQrRequest;
import com.pockt.qr.dto.ScanQrResultResponse;
import com.pockt.upi.dto.CreateUpiHandleRequest;
import com.pockt.upi.dto.UpiHandleResponse;
import com.pockt.upi.dto.VerifyVpaResponse;
import com.pockt.user.domain.OtpPurpose;
import com.pockt.user.domain.OtpVerification;
import com.pockt.user.dto.RegisterRequest;
import com.pockt.user.dto.TokenResponse;
import com.pockt.user.repository.OtpRepository;
import com.pockt.user.repository.UserRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaytmEcosystemIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OtpRepository otpRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void testFullPaytmEcosystemWorkflow() throws Exception {
        // 1. Register User
        String phone = "+91" + (9000000000L + (long) (Math.random() * 999999999L));
        String rawPin = "123456";

        String tempToken = jwtService.generateTempToken(phone);

        RegisterRequest regReq = new RegisterRequest("Pratul Makar", rawPin);
        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .header("Authorization", "Bearer " + tempToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isCreated())
                .andReturn();

        TokenResponse tokenResp = extractData(regResult, new TypeReference<>() {});
        String userToken = tokenResp.accessToken();
        UUID userId = tokenResp.userId();

        // 2. Link Dummy Bank Account
        LinkBankAccountRequest bankReq = new LinkBankAccountRequest("HDFC Bank", "123456789012", "HDFC0001234", "Pratul Makar");
        MvcResult linkBankResult = mockMvc.perform(post("/api/v1/banks/link")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bankReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.bankName").value("HDFC Bank"))
                .andExpect(jsonPath("$.data.isPrimary").value(true))
                .andReturn();

        BankAccountResponse bankAccount = extractData(linkBankResult, new TypeReference<>() {});
        UUID bankAccountId = bankAccount.id();

        // 3. Deposit money: Bank -> Wallet (Add 5,000 cents = $50.00)
        AddMoneyFromBankRequest addMoneyReq = new AddMoneyFromBankRequest(bankAccountId, null, 5000L, rawPin);
        mockMvc.perform(post("/api/v1/banks/add-money")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addMoneyReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("DEPOSIT_TO_WALLET"))
                .andExpect(jsonPath("$.data.amount").value(5000));

        // Verify wallet balance is 5000
        MvcResult walletResult = mockMvc.perform(get("/api/v1/wallets")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn();
        List<WalletResponse> wallets = extractData(walletResult, new TypeReference<>() {});
        assertThat(wallets.get(0).balance()).isEqualTo(5000L);

        // 4. Withdraw money: Wallet -> Bank (Withdraw 1,000 cents = $10.00)
        WithdrawToBankRequest withdrawReq = new WithdrawToBankRequest(bankAccountId, null, 1000L, rawPin);
        mockMvc.perform(post("/api/v1/banks/withdraw")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withdrawReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("WITHDRAW_TO_BANK"))
                .andExpect(jsonPath("$.data.amount").value(1000));

        // Wallet balance now 4000
        walletResult = mockMvc.perform(get("/api/v1/wallets")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn();
        wallets = extractData(walletResult, new TypeReference<>() {});
        assertThat(wallets.get(0).balance()).isEqualTo(4000L);

        // 5. UPI: Create custom handle & verify VPA
        String customVpa = "user" + (System.currentTimeMillis() % 10000000) + "@pockt";
        CreateUpiHandleRequest upiReq = new CreateUpiHandleRequest(customVpa, null);
        mockMvc.perform(post("/api/v1/upi/handles")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(upiReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.vpa").value(customVpa));

        // Verify VPA
        mockMvc.perform(get("/api/v1/upi/verify?vpa=" + customVpa)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isValid").value(true))
                .andExpect(jsonPath("$.data.accountHolderName").value("Pratul Makar"));

        // 6. QR Code: Get static QR and generate dynamic QR
        mockMvc.perform(get("/api/v1/qr/my-qr")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.qrPayload").isNotEmpty());

        GenerateDynamicQrRequest dynamicQrReq = new GenerateDynamicQrRequest(2500L, "Dinner Payment");
        MvcResult dynQrResult = mockMvc.perform(post("/api/v1/qr/generate")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dynamicQrReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(2500))
                .andReturn();

        QrDetailsResponse dynQr = extractData(dynQrResult, new TypeReference<>() {});

        // Scan & Resolve QR code
        ScanQrRequest scanReq = new ScanQrRequest(dynQr.qrPayload());
        mockMvc.perform(post("/api/v1/qr/scan")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(scanReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isValid").value(true))
                .andExpect(jsonPath("$.data.name").value("Pratul Makar"))
                .andExpect(jsonPath("$.data.amount").value(2500));

        // 7. Cards: Issue Debit Card and Apply for Credit Card
        MvcResult debitCardResult = mockMvc.perform(post("/api/v1/cards/debit")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new IssueDebitCardRequest("RUPAY", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.cardType").value("DEBIT"))
                .andExpect(jsonPath("$.data.cardNumberMasked").isNotEmpty())
                .andReturn();

        CardResponse debitCard = extractData(debitCardResult, new TypeReference<>() {});

        // Apply for Credit Card / Postpaid
        mockMvc.perform(post("/api/v1/cards/credit/apply")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ApplyCreditCardRequest("VISA"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalCreditLimit").value(2500000));

        // 8. Reveal Card CVV and full PAN with wallet PIN
        RevealCardRequest revealReq = new RevealCardRequest(rawPin);
        mockMvc.perform(post("/api/v1/cards/" + debitCard.id() + "/reveal")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(revealReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cardNumberFull").isNotEmpty())
                .andExpect(jsonPath("$.data.cvv").isNotEmpty());

        // 9. Simulate Card Charge on Debit Card (2,000 cents)
        CardChargeRequest chargeReq = new CardChargeRequest(2000L, "Swiggy", "123");
        mockMvc.perform(post("/api/v1/cards/" + debitCard.id() + "/charge")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chargeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.amount").value(2000));

        // Wallet balance should now be 2,000 (was 4,000 - 2,000)
        walletResult = mockMvc.perform(get("/api/v1/wallets")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn();
        wallets = extractData(walletResult, new TypeReference<>() {});
        assertThat(wallets.get(0).balance()).isEqualTo(2000L);

        // 10. Admin Back-Office verification
        // Generate an admin token
        UUID adminId = UUID.randomUUID();
        String adminToken = jwtService.generateAccessToken(adminId, "+919999999999", "ADMIN");

        // Admin: List Users
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());

        // Admin: User Dossier
        mockMvc.perform(get("/api/v1/admin/users/" + userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.fullName").value("Pratul Makar"))
                .andExpect(jsonPath("$.data.bankAccounts").isNotEmpty())
                .andExpect(jsonPath("$.data.upiHandles").isNotEmpty())
                .andExpect(jsonPath("$.data.cards").isNotEmpty());

        // Admin: User Financials with time filter
        Instant from = Instant.now().minusSeconds(86400);
        Instant to = Instant.now().plusSeconds(3600);
        mockMvc.perform(get("/api/v1/admin/users/" + userId + "/financials?from=" + from + "&to=" + to)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalBankDepositsCents").value(5000))
                .andExpect(jsonPath("$.data.totalBankWithdrawalsCents").value(1000));

        // Admin: Executive Overview Report
        mockMvc.perform(get("/api/v1/admin/reports/overview?from=" + from + "&to=" + to)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUsers").isNumber())
                .andExpect(jsonPath("$.data.totalSystemLiquidityCents").isNumber());
    }

    private <T> T extractData(MvcResult result, TypeReference<ApiResponse<T>> typeRef) throws Exception {
        ApiResponse<T> response = objectMapper.readValue(result.getResponse().getContentAsString(), typeRef);
        return response.data();
    }
}

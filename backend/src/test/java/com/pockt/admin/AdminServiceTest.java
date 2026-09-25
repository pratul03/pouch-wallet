package com.pockt.admin;

import com.pockt.admin.dto.AdminOverviewReportResponse;
import com.pockt.admin.dto.AdminUserFinancialsResponse;
import com.pockt.admin.internal.AdminServiceImpl;
import com.pockt.admin.repository.AdminRepository;
import com.pockt.bank.service.BankService;
import com.pockt.card.service.CardService;
import com.pockt.infrastructure.web.PageResponse;
import com.pockt.upi.service.UpiService;
import com.pockt.user.dto.UserResponse;
import com.pockt.user.service.UserService;
import com.pockt.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private AdminRepository adminRepository;
    @Mock private UserService userService;
    @Mock private WalletService walletService;
    @Mock private BankService bankService;
    @Mock private UpiService upiService;
    @Mock private CardService cardService;

    private AdminServiceImpl adminService;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        adminService = new AdminServiceImpl(
                adminRepository, userService, walletService, bankService, upiService, cardService
        );
    }

    @Test
    void listUsers_shouldReturnPaginatedUsers() {
        UserResponse user = new UserResponse(userId, "+1234567890", "Ade Bello", "VERIFIED", Instant.now());
        when(userService.listUsers("Ade", "VERIFIED", true, 10, 0)).thenReturn(List.of(user));
        when(userService.countUsers()).thenReturn(1L);

        PageResponse<UserResponse> response = adminService.listUsers("Ade", "VERIFIED", true, 0, 10);

        assertThat(response.items()).hasSize(1);
        assertThat(response.hasMore()).isFalse();
    }

    @Test
    void getUserFinancials_shouldAggregateVolumesAndCalculateNetFlow() {
        Instant from = Instant.now().minusSeconds(86400);
        Instant to = Instant.now();

        when(userService.getProfile(userId)).thenReturn(new UserResponse(userId, "+1234567890", "Ade Bello", "VERIFIED", Instant.now()));
        when(adminRepository.getOutgoingTransferMetric(userId, from, to)).thenReturn(new AdminRepository.VolumeMetric(5000L, 2));
        when(adminRepository.getIncomingTransferMetric(userId, from, to)).thenReturn(new AdminRepository.VolumeMetric(12000L, 3));
        when(adminRepository.getBankMetric(userId, "DEPOSIT_TO_WALLET", from, to)).thenReturn(new AdminRepository.VolumeMetric(20000L, 1));
        when(adminRepository.getBankMetric(userId, "WITHDRAW_TO_BANK", from, to)).thenReturn(new AdminRepository.VolumeMetric(3000L, 1));

        AdminUserFinancialsResponse response = adminService.getUserFinancials(userId, from, to);

        assertThat(response).isNotNull();
        assertThat(response.totalSpentCents()).isEqualTo(5000L);
        assertThat(response.totalReceivedCents()).isEqualTo(12000L);
        assertThat(response.netTransferFlowCents()).isEqualTo(7000L); // 12000 - 5000
        assertThat(response.totalBankDepositsCents()).isEqualTo(20000L);
        assertThat(response.totalBankWithdrawalsCents()).isEqualTo(3000L);
    }

    @Test
    void getOverviewReport_shouldAggregateSystemMetrics() {
        Instant from = Instant.now().minusSeconds(86400 * 7);
        Instant to = Instant.now();

        when(adminRepository.getTotalUsers()).thenReturn(150L);
        when(adminRepository.getNewUsersInPeriod(from, to)).thenReturn(25L);
        when(adminRepository.getWalletMetrics()).thenReturn(new AdminRepository.WalletAgg(150L, 5000000L));
        when(adminRepository.getTransfersInPeriod(from, to)).thenReturn(new AdminRepository.VolumeMetric(1250000L, 45));
        when(adminRepository.getBankTransactionsInPeriod("DEPOSIT_TO_WALLET", from, to)).thenReturn(new AdminRepository.VolumeMetric(3000000L, 30));
        when(adminRepository.getBankTransactionsInPeriod("WITHDRAW_TO_BANK", from, to)).thenReturn(new AdminRepository.VolumeMetric(1000000L, 10));
        when(adminRepository.getCreditMetrics()).thenReturn(new AdminRepository.CreditAgg(20L, 450000L));

        AdminOverviewReportResponse response = adminService.getOverviewReport(from, to);

        assertThat(response).isNotNull();
        assertThat(response.totalUsers()).isEqualTo(150L);
        assertThat(response.newUsersInPeriod()).isEqualTo(25L);
        assertThat(response.totalSystemLiquidityCents()).isEqualTo(5000000L);
        assertThat(response.totalTransfersVolumeCents()).isEqualTo(1250000L);
        assertThat(response.totalBankDepositsVolumeCents()).isEqualTo(3000000L);
        assertThat(response.totalCreditOutstandingCents()).isEqualTo(450000L);
    }
}

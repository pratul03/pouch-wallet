package com.pockt.admin.internal;

import com.pockt.admin.dto.AdminOverviewReportResponse;
import com.pockt.admin.dto.AdminUserDossierResponse;
import com.pockt.admin.dto.AdminUserFinancialsResponse;
import com.pockt.admin.repository.AdminRepository;
import com.pockt.admin.service.AdminService;
import com.pockt.bank.dto.BankAccountResponse;
import com.pockt.bank.service.BankService;
import com.pockt.card.dto.CardResponse;
import com.pockt.card.dto.CreditAccountResponse;
import com.pockt.card.service.CardService;
import com.pockt.infrastructure.util.MoneyUtils;
import com.pockt.infrastructure.web.PageResponse;
import com.pockt.upi.dto.UpiHandleResponse;
import com.pockt.upi.service.UpiService;
import com.pockt.user.dto.UserResponse;
import com.pockt.user.service.UserService;
import com.pockt.wallet.dto.WalletResponse;
import com.pockt.wallet.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AdminServiceImpl implements AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminServiceImpl.class);

    private final AdminRepository adminRepository;
    private final UserService userService;
    private final WalletService walletService;
    private final BankService bankService;
    private final UpiService upiService;
    private final CardService cardService;

    public AdminServiceImpl(
            AdminRepository adminRepository,
            UserService userService,
            WalletService walletService,
            BankService bankService,
            UpiService upiService,
            CardService cardService
    ) {
        this.adminRepository = adminRepository;
        this.userService = userService;
        this.walletService = walletService;
        this.bankService = bankService;
        this.upiService = upiService;
        this.cardService = cardService;
    }

    @Override
    public PageResponse<UserResponse> listUsers(String search, String kycStatus, Boolean isActive, int page, int size) {
        int offset = Math.max(0, page) * size;
        List<UserResponse> users = userService.listUsers(search, kycStatus, isActive, size, offset);
        long totalElements = userService.countUsers();
        boolean hasMore = offset + users.size() < totalElements;
        String nextCursor = hasMore ? String.valueOf(page + 1) : null;
        return PageResponse.of(users, nextCursor, hasMore);
    }

    @Override
    public AdminUserDossierResponse getUserDossier(UUID userId) {
        UserResponse user = userService.getProfile(userId);
        List<WalletResponse> wallets = walletService.getUserWallets(userId);
        List<BankAccountResponse> banks = bankService.getBankAccounts(userId);
        List<UpiHandleResponse> upis = upiService.getUserHandles(userId);
        List<CardResponse> cards = cardService.getUserCards(userId);

        CreditAccountResponse credit = null;
        try {
            credit = cardService.getCreditAccount(userId);
        } catch (Exception ignored) {}

        return new AdminUserDossierResponse(user, wallets, banks, upis, cards, credit);
    }

    @Override
    public AdminUserFinancialsResponse getUserFinancials(UUID userId, Instant from, Instant to) {
        UserResponse user = userService.getProfile(userId);

        AdminRepository.VolumeMetric outgoing = adminRepository.getOutgoingTransferMetric(userId, from, to);
        AdminRepository.VolumeMetric incoming = adminRepository.getIncomingTransferMetric(userId, from, to);
        AdminRepository.VolumeMetric deposits = adminRepository.getBankMetric(userId, "DEPOSIT_TO_WALLET", from, to);
        AdminRepository.VolumeMetric withdrawals = adminRepository.getBankMetric(userId, "WITHDRAW_TO_BANK", from, to);

        long netFlow = incoming.amount() - outgoing.amount();

        return new AdminUserFinancialsResponse(
                user.id(),
                user.phone(),
                user.fullName(),
                from,
                to,
                outgoing.amount(),
                MoneyUtils.format(outgoing.amount(), "USD"),
                outgoing.count(),
                incoming.amount(),
                MoneyUtils.format(incoming.amount(), "USD"),
                incoming.count(),
                netFlow,
                MoneyUtils.format(netFlow, "USD"),
                deposits.amount(),
                MoneyUtils.format(deposits.amount(), "USD"),
                deposits.count(),
                withdrawals.amount(),
                MoneyUtils.format(withdrawals.amount(), "USD"),
                withdrawals.count()
        );
    }

    @Override
    @Transactional
    public UserResponse updateUserStatus(UUID userId, boolean isActive) {
        log.info("Admin updated user {} active status to {}", userId, isActive);
        return userService.updateStatus(userId, isActive);
    }

    @Override
    @Transactional
    public void setWalletStatus(UUID walletId, boolean isActive) {
        log.info("Admin updated wallet {} active status to {}", walletId, isActive);
        adminRepository.updateWalletActive(walletId, isActive);
    }

    @Override
    public AdminOverviewReportResponse getOverviewReport(Instant from, Instant to) {
        long totalUsers = adminRepository.getTotalUsers();
        long newUsers = adminRepository.getNewUsersInPeriod(from, to);
        AdminRepository.WalletAgg walletAgg = adminRepository.getWalletMetrics();
        AdminRepository.VolumeMetric transfers = adminRepository.getTransfersInPeriod(from, to);
        AdminRepository.VolumeMetric deposits = adminRepository.getBankTransactionsInPeriod("DEPOSIT_TO_WALLET", from, to);
        AdminRepository.VolumeMetric withdrawals = adminRepository.getBankTransactionsInPeriod("WITHDRAW_TO_BANK", from, to);
        AdminRepository.CreditAgg creditAgg = adminRepository.getCreditMetrics();

        return new AdminOverviewReportResponse(
                from,
                to,
                totalUsers,
                newUsers,
                walletAgg.count(),
                walletAgg.liquidity(),
                MoneyUtils.format(walletAgg.liquidity(), "USD"),
                transfers.count(),
                transfers.amount(),
                MoneyUtils.format(transfers.amount(), "USD"),
                deposits.count(),
                deposits.amount(),
                MoneyUtils.format(deposits.amount(), "USD"),
                withdrawals.count(),
                withdrawals.amount(),
                MoneyUtils.format(withdrawals.amount(), "USD"),
                creditAgg.count(),
                creditAgg.totalOutstanding(),
                MoneyUtils.format(creditAgg.totalOutstanding(), "USD")
        );
    }
}

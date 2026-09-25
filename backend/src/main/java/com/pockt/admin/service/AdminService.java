package com.pockt.admin.service;

import com.pockt.admin.dto.AdminOverviewReportResponse;
import com.pockt.admin.dto.AdminUserDossierResponse;
import com.pockt.admin.dto.AdminUserFinancialsResponse;
import com.pockt.infrastructure.web.PageResponse;
import com.pockt.user.dto.UserResponse;

import java.time.Instant;
import java.util.UUID;

public interface AdminService {
    PageResponse<UserResponse> listUsers(String search, String kycStatus, Boolean isActive, int page, int size);
    AdminUserDossierResponse getUserDossier(UUID userId);
    AdminUserFinancialsResponse getUserFinancials(UUID userId, Instant from, Instant to);
    UserResponse updateUserStatus(UUID userId, boolean isActive);
    void setWalletStatus(UUID walletId, boolean isActive);
    AdminOverviewReportResponse getOverviewReport(Instant from, Instant to);
}

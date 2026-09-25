package com.pockt.analytics.api;

import com.pockt.analytics.dto.SpendingSummaryResponse;
import com.pockt.analytics.service.AnalyticsService;
import com.pockt.infrastructure.security.UserPrincipal;
import com.pockt.infrastructure.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
@Tag(name = "Spending Analytics", description = "Personal finance management, categorized monthly spending breakdown, and cash flow analysis")
@SecurityRequirement(name = "BearerAuth")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/spending")
    @Operation(summary = "Get monthly categorized spending analysis, total spent, total received, and net flow")
    public ResponseEntity<ApiResponse<SpendingSummaryResponse>> getSpending(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        SpendingSummaryResponse response = analyticsService.getMonthlySpending(principal.id(), month, year);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}

package com.pockt.reward.api;

import com.pockt.infrastructure.security.UserPrincipal;
import com.pockt.infrastructure.web.ApiResponse;
import com.pockt.reward.dto.RewardsSummaryResponse;
import com.pockt.reward.dto.ScratchCardResponse;
import com.pockt.reward.service.RewardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rewards")
@Tag(name = "Rewards & Cashback", description = "Scratch cards, cashback earnings, and transaction milestone rewards")
@SecurityRequirement(name = "BearerAuth")
public class RewardController {

    private final RewardService rewardService;

    public RewardController(RewardService rewardService) {
        this.rewardService = rewardService;
    }

    @GetMapping("/scratch-cards")
    @Operation(summary = "List all scratch cards for current user (unscratched cards have hidden amounts)")
    public ResponseEntity<ApiResponse<List<ScratchCardResponse>>> getScratchCards(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false, defaultValue = "false") boolean unscratchedOnly) {
        List<ScratchCardResponse> cards = rewardService.getUserCards(principal.id(), unscratchedOnly);
        return ResponseEntity.ok(ApiResponse.ok(cards));
    }

    @GetMapping("/summary")
    @Operation(summary = "Get overall rewards summary with total cashback earned and card counts")
    public ResponseEntity<ApiResponse<RewardsSummaryResponse>> getRewardsSummary(
            @AuthenticationPrincipal UserPrincipal principal) {
        RewardsSummaryResponse summary = rewardService.getRewardsSummary(principal.id());
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }

    @PostMapping("/scratch-cards/{id}/scratch")
    @Operation(summary = "Scratch and reveal a card; instantly credits won cashback to the user's wallet")
    public ResponseEntity<ApiResponse<ScratchCardResponse>> scratchCard(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        ScratchCardResponse response = rewardService.scratchCard(principal.id(), id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}

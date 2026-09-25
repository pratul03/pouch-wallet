package com.pockt.reward.internal;

import com.pockt.reward.service.RewardService;
import com.pockt.transfer.event.TransferCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class RewardEventListener {

    private static final Logger log = LoggerFactory.getLogger(RewardEventListener.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RewardService rewardService;

    public RewardEventListener(RewardService rewardService) {
        this.rewardService = rewardService;
    }

    @EventListener
    public void onTransferCompleted(TransferCompletedEvent event) {
        try {
            // Reward transfers of $10.00 (1000 cents) or more
            if (event.amountCents() >= 1000L) {
                // Cashback between 50 cents ($0.50) and 500 cents ($5.00)
                long rewardAmount = 50L + RANDOM.nextInt(451);
                String title = "Transfer Reward!";
                String description = "Won on transfer of $" + String.format("%.2f", event.amountCents() / 100.0) + " to " + event.receiverName();

                rewardService.issueReward(
                        event.senderUserId(),
                        title,
                        description,
                        rewardAmount,
                        event.transactionId()
                );
                log.info("Issued scratch card reward to user {} for tx {}", event.senderUserId(), event.transactionId());
            }
        } catch (Exception e) {
            log.error("Failed to issue scratch card reward on transfer completion", e);
        }
    }
}

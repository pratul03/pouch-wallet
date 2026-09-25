package com.pockt.reward.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pockt.infrastructure.exception.ErrorCode;
import com.pockt.infrastructure.exception.PocktException;
import com.pockt.reward.domain.ScratchCard;
import com.pockt.reward.dto.RewardsSummaryResponse;
import com.pockt.reward.dto.ScratchCardResponse;
import com.pockt.reward.repository.ScratchCardRepository;
import com.pockt.reward.service.RewardService;
import com.pockt.transfer.domain.NotificationOutbox;
import com.pockt.transfer.repository.OutboxRepository;
import com.pockt.wallet.domain.Wallet;
import com.pockt.wallet.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RewardServiceImpl implements RewardService {

    private static final Logger log = LoggerFactory.getLogger(RewardServiceImpl.class);

    private final ScratchCardRepository scratchCardRepository;
    private final WalletService walletService;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public RewardServiceImpl(
            ScratchCardRepository scratchCardRepository,
            WalletService walletService,
            OutboxRepository outboxRepository,
            ObjectMapper objectMapper
    ) {
        this.scratchCardRepository = scratchCardRepository;
        this.walletService = walletService;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public ScratchCardResponse issueReward(UUID userId, String title, String description, long amountCents, UUID txId) {
        ScratchCard card = new ScratchCard(
                UUID.randomUUID(),
                userId,
                title,
                description,
                amountCents,
                false,
                null,
                txId,
                Instant.now()
        );

        scratchCardRepository.save(card);
        log.info("Scratch card issued: id={}, user={}, title={}", card.id(), userId, title);
        return ScratchCardResponse.from(card);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScratchCardResponse> getUserCards(UUID userId, Boolean unscratchedOnly) {
        List<ScratchCard> list = Boolean.TRUE.equals(unscratchedOnly)
                ? scratchCardRepository.findUnscratchedByUserId(userId)
                : scratchCardRepository.findByUserId(userId);
        return list.stream().map(ScratchCardResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RewardsSummaryResponse getRewardsSummary(UUID userId) {
        List<ScratchCard> allCards = scratchCardRepository.findByUserId(userId);
        long totalCashback = scratchCardRepository.getTotalCashbackByUserId(userId);
        int unscratched = (int) allCards.stream().filter(c -> !c.isScratched()).count();
        List<ScratchCardResponse> cardResponses = allCards.stream().map(ScratchCardResponse::from).toList();

        return new RewardsSummaryResponse(
                totalCashback,
                allCards.size(),
                unscratched,
                cardResponses
        );
    }

    @Override
    @Transactional
    public ScratchCardResponse scratchCard(UUID userId, UUID cardId) {
        ScratchCard card = scratchCardRepository.findByIdForUpdate(cardId)
                .orElseThrow(() -> new PocktException(ErrorCode.SCRATCH_CARD_NOT_FOUND, "Scratch card not found", HttpStatus.NOT_FOUND));

        if (!card.userId().equals(userId)) {
            throw new PocktException(ErrorCode.FORBIDDEN, "This scratch card does not belong to you", HttpStatus.FORBIDDEN);
        }

        if (card.isScratched()) {
            throw new PocktException(ErrorCode.SCRATCH_CARD_ALREADY_REDEEMED, "Scratch card has already been redeemed", HttpStatus.CONFLICT);
        }

        scratchCardRepository.markScratched(cardId);

        // Credit cashback to wallet
        if (card.rewardAmountCents() > 0) {
            Wallet wallet = walletService.getOrCreateWallet(userId, "USD");
            walletService.credit(wallet.id(), card.rewardAmountCents());

            try {
                String payload = objectMapper.writeValueAsString(Map.of(
                        "rewardAmountCents", card.rewardAmountCents(),
                        "cardTitle", card.title(),
                        "walletId", wallet.id().toString()
                ));
                outboxRepository.save(new NotificationOutbox(
                        UUID.randomUUID(),
                        userId,
                        "CASHBACK_CREDITED",
                        payload,
                        "PENDING",
                        0,
                        null,
                        Instant.now()
                ));
            } catch (JsonProcessingException e) {
                log.error("Failed to build cashback notification payload", e);
            }
        }

        ScratchCard revealed = new ScratchCard(
                card.id(),
                card.userId(),
                card.title(),
                card.description(),
                card.rewardAmountCents(),
                true,
                Instant.now(),
                card.transactionId(),
                card.createdAt()
        );

        log.info("User {} scratched card {}: won {} cents", userId, cardId, card.rewardAmountCents());
        return ScratchCardResponse.from(revealed);
    }
}

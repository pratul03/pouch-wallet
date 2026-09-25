package com.pockt.card.service;

import com.pockt.card.dto.ApplyCreditCardRequest;
import com.pockt.card.dto.CardChargeRequest;
import com.pockt.card.dto.CardChargeResponse;
import com.pockt.card.dto.CardResponse;
import com.pockt.card.dto.CardRevealResponse;
import com.pockt.card.dto.CreditAccountResponse;
import com.pockt.card.dto.IssueDebitCardRequest;
import com.pockt.card.dto.RepayCreditRequest;
import com.pockt.card.dto.RevealCardRequest;
import com.pockt.card.dto.UpdateCardSettingsRequest;

import java.util.List;
import java.util.UUID;

public interface CardService {
    CardResponse issueDebitCard(UUID userId, IssueDebitCardRequest request);
    CreditAccountResponse applyCreditCard(UUID userId, ApplyCreditCardRequest request);
    List<CardResponse> getUserCards(UUID userId);
    CardResponse getCard(UUID userId, UUID cardId);
    CardRevealResponse revealCard(UUID userId, UUID cardId, RevealCardRequest request);
    CardResponse updateSettings(UUID userId, UUID cardId, UpdateCardSettingsRequest request);
    CardResponse toggleFreeze(UUID userId, UUID cardId);
    CardChargeResponse simulateCardCharge(UUID cardId, CardChargeRequest request);
    CreditAccountResponse getCreditAccount(UUID userId);
    CreditAccountResponse repayCredit(UUID userId, RepayCreditRequest request);
}

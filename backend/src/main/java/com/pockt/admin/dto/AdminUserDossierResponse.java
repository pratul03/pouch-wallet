package com.pockt.admin.dto;

import com.pockt.bank.dto.BankAccountResponse;
import com.pockt.card.dto.CardResponse;
import com.pockt.card.dto.CreditAccountResponse;
import com.pockt.upi.dto.UpiHandleResponse;
import com.pockt.user.dto.UserResponse;
import com.pockt.wallet.dto.WalletResponse;

import java.util.List;

public record AdminUserDossierResponse(
    UserResponse user,
    List<WalletResponse> wallets,
    List<BankAccountResponse> bankAccounts,
    List<UpiHandleResponse> upiHandles,
    List<CardResponse> cards,
    CreditAccountResponse creditAccount
) {}

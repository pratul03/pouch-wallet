package com.pockt.bank.service;

import com.pockt.bank.dto.AddMoneyFromBankRequest;
import com.pockt.bank.dto.BankAccountResponse;
import com.pockt.bank.dto.BankTransactionResponse;
import com.pockt.bank.dto.LinkBankAccountRequest;
import com.pockt.bank.dto.WithdrawToBankRequest;

import java.util.List;
import java.util.UUID;

public interface BankService {
    BankAccountResponse linkBankAccount(UUID userId, LinkBankAccountRequest request);
    List<BankAccountResponse> getBankAccounts(UUID userId);
    BankAccountResponse getBankAccount(UUID userId, UUID bankAccountId);
    void setPrimaryAccount(UUID userId, UUID bankAccountId);
    BankTransactionResponse addMoneyFromBank(UUID userId, AddMoneyFromBankRequest request);
    BankTransactionResponse withdrawToBank(UUID userId, WithdrawToBankRequest request);
    List<BankTransactionResponse> getBankTransactions(UUID userId, UUID bankAccountId, int limit);
}

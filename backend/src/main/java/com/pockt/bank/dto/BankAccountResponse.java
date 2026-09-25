package com.pockt.bank.dto;

import com.pockt.bank.domain.BankAccount;
import com.pockt.infrastructure.util.MoneyUtils;

import java.util.UUID;

public record BankAccountResponse(
    UUID id,
    String bankName,
    String accountNumberMasked,
    String ifscCode,
    String accountHolderName,
    long simulatedBalance,
    String formattedSimulatedBalance,
    boolean isPrimary,
    String status
) {
    public static BankAccountResponse fromDomain(BankAccount acc) {
        String num = acc.accountNumber();
        String masked = num.length() > 4
                ? "•••• •••• " + num.substring(num.length() - 4)
                : num;

        return new BankAccountResponse(
                acc.id(),
                acc.bankName(),
                masked,
                acc.ifscCode(),
                acc.accountHolderName(),
                acc.simulatedBalance(),
                MoneyUtils.format(acc.simulatedBalance(), "USD"),
                acc.isPrimary(),
                acc.status()
        );
    }
}

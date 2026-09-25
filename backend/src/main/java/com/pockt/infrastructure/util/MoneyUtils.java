package com.pockt.infrastructure.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

public final class MoneyUtils {

    private MoneyUtils() {}

    public static String format(long amountCents, String currencyCode) {
        BigDecimal amount = BigDecimal.valueOf(amountCents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        try {
            Currency currency = Currency.getInstance(currencyCode);
            NumberFormat format = NumberFormat.getCurrencyInstance(Locale.US);
            format.setCurrency(currency);
            return format.format(amount);
        } catch (Exception e) {
            return currencyCode + " " + amount.toPlainString();
        }
    }
}

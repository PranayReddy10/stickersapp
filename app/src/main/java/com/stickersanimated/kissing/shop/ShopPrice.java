package com.stickersanimated.kissing.shop;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Prices, written the way the shop's own pages write them. */
public final class ShopPrice {

    private ShopPrice() {
    }

    /** "₹103", or "₹103.50" when the pennies matter. */
    public static String format(double amount, String currency) {
        final DecimalFormat format = amount == Math.floor(amount)
                ? new DecimalFormat("#,##0")
                : new DecimalFormat("#,##0.00");
        format.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.US));

        return symbol(currency) + format.format(amount);
    }

    /**
     * The sign people recognise, falling back to the code itself. A shop that starts
     * selling in a currency this list has never heard of shows "AED 120" rather than
     * a bare number with no clue what it is.
     */
    private static String symbol(String currency) {
        if (currency == null || currency.trim().isEmpty()) {
            return "";
        }
        final String code = currency.trim().toUpperCase(Locale.US);
        if ("INR".equals(code)) {
            return "₹";
        }
        if ("USD".equals(code)) {
            return "$";
        }
        if ("EUR".equals(code)) {
            return "€";
        }
        if ("GBP".equals(code)) {
            return "£";
        }
        return code + " ";
    }
}

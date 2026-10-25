package nz.co.ksktech.cashbook.core;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One statement line.
 *
 * @param amount NZD, negative means money out
 * @param foreign original-currency amount, or null for NZD charges
 * @param fxFee NZD fee for the conversion or offshore margin, zero when none
 */
public record Transaction(
        SourceRef source,
        String institution,
        AccountType accountType,
        LocalDate transactionDate,
        LocalDate processedDate,
        String description,
        BigDecimal amount,
        ForeignAmount foreign,
        BigDecimal fxFee,
        boolean highlighted) {

    public Transaction {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(institution, "institution");
        Objects.requireNonNull(accountType, "accountType");
        Objects.requireNonNull(transactionDate, "transactionDate");
        Objects.requireNonNull(processedDate, "processedDate");
        Objects.requireNonNull(description, "description");
        amount = Money.normalise(amount);
        fxFee = fxFee == null ? Money.ZERO : Money.normalise(fxFee);
    }
}

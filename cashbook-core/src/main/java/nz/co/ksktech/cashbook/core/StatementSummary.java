package nz.co.ksktech.cashbook.core;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * The figures a statement prints about itself. Balances are null when the layout has none (e.g. a card
 * export with totals only); totals are null when the layout prints none.
 *
 * @param openingBalance for cards, the amount owing (purchases increase it)
 */
public record StatementSummary(
        String file,
        String layout,
        String institution,
        AccountType accountType,
        String accountName,
        String accountNumber,
        LocalDate from,
        LocalDate to,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        BigDecimal printedMoneyIn,
        BigDecimal printedMoneyOut,
        int pages,
        boolean ocr) {

    public StatementSummary {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(accountType, "accountType");
        openingBalance = openingBalance == null ? null : Money.normalise(openingBalance);
        closingBalance = closingBalance == null ? null : Money.normalise(closingBalance);
        printedMoneyIn = printedMoneyIn == null ? null : Money.normalise(printedMoneyIn);
        printedMoneyOut = printedMoneyOut == null ? null : Money.normalise(printedMoneyOut);
    }
}

package nz.co.ksktech.cashbook.eval;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/**
 * One cashbook line as the evaluation sees it, from a golden or an actual cashbook.
 *
 * @param date null when the cashbook has none for the line
 * @param gstCollected GST on income, four places
 * @param gstClaimed GST on expenses, negative, four places
 * @param source where it came from: "file p.3" for a run's line, "row 42" for a spreadsheet row
 * @param ruleDecided every transaction behind it was settled by a rule (a known vendor); false for golden lines
 */
public record EvalLine(YearMonth month, LocalDate date, String code, String detail, BigDecimal amount,
        BigDecimal gstCollected, BigDecimal gstClaimed, String source, boolean ruleDecided) {

    public EvalLine {
        Objects.requireNonNull(month, "month");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(amount, "amount");
        detail = detail == null ? "" : detail;
        source = source == null ? "" : source;
    }
}

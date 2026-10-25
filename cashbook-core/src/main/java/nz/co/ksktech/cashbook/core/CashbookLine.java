package nz.co.ksktech.cashbook.core;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

/** One row of the cashbook. {@code amount} is the signed bank amount (negative = expense). */
public record CashbookLine(
        YearMonth month, String code, String detail, BigDecimal amount, List<SourceRef> sources, GstTreatment gst) {

    public CashbookLine {
        Objects.requireNonNull(month, "month");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(detail, "detail");
        amount = Money.normalise(amount);
        sources = List.copyOf(sources);
        Objects.requireNonNull(gst, "gst");
    }
}

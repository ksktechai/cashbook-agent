package nz.co.ksktech.cashbook.ingest.parse;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import nz.co.ksktech.cashbook.core.ForeignAmount;
import nz.co.ksktech.cashbook.core.Money;

/**
 * Reads amounts as statements print them: "$1,234.56", "- $1,234.56", "CR $12.00", "– $12.00",
 * "+ $12.00", "-$12.34 NZD", "$20.00 USD", "(credit) $25.00", "1,234.56*", "$ 3,073.95".
 */
public final class Amounts {

    private static final Pattern AMOUNT = Pattern.compile(
            "(?<credit>\\(credit\\)\\s*|CR\\s*)?(?<sign>[-–+]\\s*)?\\$?\\s*(?<digits>\\d{1,3}(?:,\\d{3})*\\.\\d{2}|\\d+\\.\\d{2})\\*?"
                    + "(?:\\s+(?<currency>[A-Z]{3}))?");

    private Amounts() {}

    /** The signed amount in a cell: a leading minus or en dash is negative; "CR" and "(credit)" are not. */
    public static Optional<BigDecimal> parse(String text) {
        Matcher m = AMOUNT.matcher(text.strip());
        if (!m.find()) {
            return Optional.empty();
        }
        BigDecimal value = Money.of(m.group("digits").replace(",", ""));
        String sign = m.group("sign");
        if (sign != null && (sign.startsWith("-") || sign.startsWith("–"))) {
            value = value.negate();
        }
        return Optional.of(value);
    }

    public static BigDecimal require(String text) {
        return parse(text).orElseThrow(() -> new IllegalArgumentException("no amount in '" + text + "'"));
    }

    /** True when the cell is a credit ("CR $12.00", "(credit) $25.00"). */
    public static boolean isCredit(String text) {
        Matcher m = AMOUNT.matcher(text.strip());
        return m.find() && m.group("credit") != null;
    }

    /** The currency after the amount ("$20.00 USD"), if any. */
    public static Optional<String> currency(String text) {
        Matcher m = AMOUNT.matcher(text.strip());
        return m.find() ? Optional.ofNullable(m.group("currency")) : Optional.empty();
    }

    /** A signed amount with its currency, when the cell names one. */
    public static Optional<ForeignAmount> foreign(String text) {
        return currency(text).flatMap(c -> parse(text).map(v -> new ForeignAmount(v, c)));
    }

    /** The last amount on a line of text, e.g. the value after a label. */
    public static Optional<BigDecimal> last(String text) {
        Matcher m = AMOUNT.matcher(text);
        BigDecimal found = null;
        int start = 0;
        while (m.find(start)) {
            found = parse(m.group()).orElse(null);
            start = m.end();
        }
        return Optional.ofNullable(found);
    }
}

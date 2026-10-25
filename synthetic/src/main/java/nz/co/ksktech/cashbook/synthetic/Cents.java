package nz.co.ksktech.cashbook.synthetic;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import nz.co.ksktech.cashbook.core.Money;

/** Integer-cent arithmetic for the generator, rounding HALF_UP through BigDecimal. */
public final class Cents {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Cents() {}

    public static long of(String amount) {
        return Money.toCents(Money.of(amount));
    }

    public static BigDecimal toMoney(long cents) {
        return Money.ofCents(cents);
    }

    /** {@code cents * percent / 100}, e.g. percent "2.5". */
    public static long percent(long cents, String percent) {
        return BigDecimal.valueOf(cents).multiply(new BigDecimal(percent))
                .divide(HUNDRED, 0, RoundingMode.HALF_UP).longValueExact();
    }

    /** {@code cents * numerator / denominator}, rounded. */
    public static long ratio(long cents, long numerator, long denominator) {
        return BigDecimal.valueOf(cents).multiply(BigDecimal.valueOf(numerator))
                .divide(BigDecimal.valueOf(denominator), 0, RoundingMode.HALF_UP).longValueExact();
    }

    /** {@code value * rate} where value and rate are decimals, result in cents. */
    public static long times(BigDecimal value, BigDecimal rate) {
        return value.multiply(rate).multiply(HUNDRED).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** Converts a foreign amount to NZD at a printed rate (NZD 1 = rate units of foreign). */
    public static long fromForeign(long foreignCents, String rate) {
        return BigDecimal.valueOf(foreignCents).divide(new BigDecimal(rate), 0, RoundingMode.HALF_UP).longValueExact();
    }

    /** 1234567 -> "12,345.67", always positive. */
    public static String format(long cents) {
        DecimalFormat f = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ROOT));
        return f.format(toMoney(Math.abs(cents)));
    }

    /** 1234567 -> "12345.67", always positive, no grouping. */
    public static String plain(long cents) {
        return toMoney(Math.abs(cents)).toPlainString();
    }
}

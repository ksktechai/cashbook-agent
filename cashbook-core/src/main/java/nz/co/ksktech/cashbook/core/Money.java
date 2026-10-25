package nz.co.ksktech.cashbook.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Money is always NZD-style {@link BigDecimal} with scale 2 and HALF_UP rounding. Never use double. */
public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

    private Money() {}

    public static BigDecimal of(String amount) {
        return normalise(new BigDecimal(Objects.requireNonNull(amount, "amount")));
    }

    public static BigDecimal ofCents(long cents) {
        return BigDecimal.valueOf(cents, SCALE);
    }

    /** Rounds any value to scale 2, HALF_UP. */
    public static BigDecimal normalise(BigDecimal amount) {
        return Objects.requireNonNull(amount, "amount").setScale(SCALE, ROUNDING);
    }

    public static long toCents(BigDecimal amount) {
        return normalise(amount).unscaledValue().longValueExact();
    }
}

package nz.co.ksktech.cashbook.core;

import java.math.BigDecimal;
import java.util.Objects;

/** The original-currency amount of a foreign charge, e.g. USD 20.00. */
public record ForeignAmount(BigDecimal amount, String currency) {

    public ForeignAmount {
        amount = Money.normalise(amount);
        Objects.requireNonNull(currency, "currency");
    }
}

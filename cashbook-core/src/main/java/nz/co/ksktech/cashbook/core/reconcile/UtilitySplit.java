package nz.co.ksktech.cashbook.core.reconcile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One utility card payment split into Power and Internet. Power (725) is {@code power + fee}; the three
 * parts sum exactly to {@code card}.
 *
 * @param footnote explanation when a credit was applied, else null
 */
public record UtilitySplit(LocalDate cardDate, LocalDate invoiceDate, BigDecimal card, BigDecimal power,
        BigDecimal fee, BigDecimal internet, String footnote) {

    public UtilitySplit {
        Objects.requireNonNull(cardDate, "cardDate");
        if (power.add(fee).add(internet).compareTo(card) != 0) {
            throw new IllegalArgumentException("split " + power + " + " + fee + " + " + internet + " != " + card);
        }
    }

    public BigDecimal power725() {
        return power.add(fee);
    }
}

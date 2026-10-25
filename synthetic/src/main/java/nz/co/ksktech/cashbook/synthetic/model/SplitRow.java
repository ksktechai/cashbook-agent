package nz.co.ksktech.cashbook.synthetic.model;

import java.time.LocalDate;

/**
 * One row of the utility split table: a card payment split into Power (725) and Internet (727).
 * Power (725) is {@code powerCents + feeCents}; the three parts always sum to {@code cardCents}.
 *
 * @param footnote explanation when a credit was applied, else null
 */
public record SplitRow(
        LocalDate cardDate,
        LocalDate invoiceDate,
        long cardCents,
        long powerCents,
        long feeCents,
        long internetCents,
        String footnote) {

    public SplitRow {
        if (powerCents + feeCents + internetCents != cardCents) {
            throw new IllegalArgumentException("split does not sum to the card charge");
        }
    }

    public long power725Cents() {
        return powerCents + feeCents;
    }
}

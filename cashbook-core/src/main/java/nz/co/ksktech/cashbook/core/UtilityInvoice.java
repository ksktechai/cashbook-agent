package nz.co.ksktech.cashbook.core;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The figures from a combined power and broadband invoice that the utility split needs. All amounts are
 * GST-inclusive.
 *
 * @param cardFee card payment fee billed on this invoice (for the previous card payment)
 * @param credit credit carried into this invoice, zero when none
 * @param total this invoice's charges (power + broadband + card fee)
 * @param due what was asked for: total minus credit
 */
public record UtilityInvoice(
        String file,
        String invoiceNumber,
        LocalDate invoiceDate,
        LocalDate dueDate,
        BigDecimal powerSubtotal,
        BigDecimal broadbandSubtotal,
        BigDecimal cardFee,
        BigDecimal credit,
        BigDecimal total,
        BigDecimal due,
        boolean ocr) {

    public UtilityInvoice {
        powerSubtotal = Money.normalise(powerSubtotal);
        broadbandSubtotal = Money.normalise(broadbandSubtotal);
        cardFee = Money.normalise(cardFee);
        credit = Money.normalise(credit);
        total = Money.normalise(total);
        due = Money.normalise(due);
    }

    /** True when the printed figures agree with each other. */
    public boolean consistent() {
        return powerSubtotal.add(broadbandSubtotal).add(cardFee).compareTo(total) == 0
                && total.subtract(credit).compareTo(due) == 0;
    }
}

package nz.co.ksktech.cashbook.core.reconcile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import nz.co.ksktech.cashbook.core.Money;
import nz.co.ksktech.cashbook.core.UtilityInvoice;

/**
 * Splits a combined power and broadband card payment. Internet is the broadband subtotal; Power is the power
 * subtotal plus the card surcharge (payment - power - broadband). When a credit was applied to the invoice,
 * the payment is split pro-rata to the invoice's charges. The parts always sum exactly to the payment.
 */
public final class UtilitySplitter {

    private UtilitySplitter() {}

    /** The invoice a payment settles: the latest one dated in the month before the payment, else any earlier one. */
    public static Optional<UtilityInvoice> invoiceFor(LocalDate paymentDate, List<UtilityInvoice> invoices) {
        YearMonth previous = YearMonth.from(paymentDate).minusMonths(1);
        Comparator<UtilityInvoice> latest = Comparator.comparing(UtilityInvoice::invoiceDate);
        return invoices.stream().filter(i -> YearMonth.from(i.invoiceDate()).equals(previous)).max(latest)
                .or(() -> invoices.stream().filter(i -> i.invoiceDate().isBefore(paymentDate)).max(latest));
    }

    /** @param payment the card charge as a positive amount */
    public static UtilitySplit split(BigDecimal payment, LocalDate cardDate, UtilityInvoice invoice) {
        BigDecimal paid = Money.normalise(payment.abs());
        if (invoice.credit().signum() == 0) {
            BigDecimal internet = invoice.broadbandSubtotal();
            BigDecimal power = invoice.powerSubtotal();
            BigDecimal fee = paid.subtract(power).subtract(internet);
            return new UtilitySplit(cardDate, invoice.invoiceDate(), paid, power, fee, internet, null);
        }
        BigDecimal total = invoice.total();
        BigDecimal power = share(paid, invoice.powerSubtotal(), total);
        BigDecimal fee = share(paid, invoice.cardFee(), total);
        BigDecimal internet = paid.subtract(power).subtract(fee);
        String note = String.format(java.util.Locale.ROOT,
                "* %s invoice $%s less $%s credit = $%s due; split pro-rata to the invoice charges.",
                invoice.invoiceDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM")), total.toPlainString(),
                invoice.credit().toPlainString(), invoice.due().toPlainString());
        return new UtilitySplit(cardDate, invoice.invoiceDate(), paid, power, fee, internet, note);
    }

    private static BigDecimal share(BigDecimal paid, BigDecimal part, BigDecimal total) {
        return paid.multiply(part).divide(total, Money.SCALE, Money.ROUNDING);
    }
}

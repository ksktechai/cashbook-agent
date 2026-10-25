package nz.co.ksktech.cashbook.synthetic.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * An Orcon-style combined power and broadband invoice. All amounts are GST-inclusive cents.
 *
 * @param creditCents credit carried into this invoice (shown as an opening "(credit)" balance), 0 when none
 * @param cardFeeCents card payment fee for the previous card payment, billed on this invoice
 */
public record InvoiceSpec(
        String file,
        String accountNumber,
        String invoiceNumber,
        LocalDate invoiceDate,
        LocalDate dueDate,
        LocalDate periodFrom,
        LocalDate periodTo,
        LocalDate previousInvoiceDate,
        long previousInvoiceCents,
        long creditCents,
        String creditReason,
        int days,
        BigDecimal dailyRate,
        long dailyCents,
        BigDecimal kwh,
        BigDecimal usageRateCents,
        long usageCents,
        long powerDiscountCents,
        long broadbandPlanCents,
        long broadbandDiscountCents,
        long cardFeeCents) {

    public long powerSubtotalCents() {
        return dailyCents + usageCents - powerDiscountCents;
    }

    public long broadbandSubtotalCents() {
        return broadbandPlanCents - broadbandDiscountCents;
    }

    public long totalCents() {
        return powerSubtotalCents() + broadbandSubtotalCents() + cardFeeCents;
    }

    public long dueCents() {
        return totalCents() - creditCents;
    }
}

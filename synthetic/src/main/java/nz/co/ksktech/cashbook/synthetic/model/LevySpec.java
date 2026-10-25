package nz.co.ksktech.cashbook.synthetic.model;

import java.time.LocalDate;
import java.util.List;
import nz.co.ksktech.cashbook.synthetic.Cents;

/**
 * A body-corporate levy tax invoice with an early-payment discount. Amounts in cents.
 *
 * @param scanned written as an image-only PDF (no text layer), as if scanned, to exercise OCR
 */
public record LevySpec(
        String file,
        String invoiceNumber,
        LocalDate invoiceDate,
        LocalDate dueDate,
        LocalDate periodFrom,
        LocalDate periodTo,
        List<LevyLine> lines,
        int discountPercent,
        boolean scanned) {

    /** One fund line; {@code annualCents} is the yearly levy the quarter is part of. */
    public record LevyLine(String fund, long annualCents, long netCents, long gstCents) {

        public long totalCents() {
            return netCents + gstCents;
        }
    }

    public LevySpec {
        lines = List.copyOf(lines);
    }

    public long netTotalCents() {
        return lines.stream().mapToLong(LevyLine::netCents).sum();
    }

    public long gstTotalCents() {
        return lines.stream().mapToLong(LevyLine::gstCents).sum();
    }

    public long grossCents() {
        return lines.stream().mapToLong(LevyLine::totalCents).sum();
    }

    public long discountCents() {
        return Cents.percent(grossCents(), Integer.toString(discountPercent));
    }

    /** What is actually paid when the discount is taken. */
    public long netDueCents() {
        return grossCents() - discountCents();
    }
}

package nz.co.ksktech.cashbook.core;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A body-corporate levy tax invoice.
 *
 * @param gross net plus GST, before the early-payment discount
 * @param netDue what is paid when the discount is taken
 * @param ocr true when the figures were read by OCR (send to review)
 */
public record LevyNotice(
        String file,
        String invoiceNumber,
        LocalDate invoiceDate,
        LocalDate dueDate,
        BigDecimal net,
        BigDecimal gst,
        BigDecimal gross,
        BigDecimal discount,
        BigDecimal netDue,
        boolean ocr) {

    public LevyNotice {
        net = Money.normalise(net);
        gst = Money.normalise(gst);
        gross = Money.normalise(gross);
        discount = Money.normalise(discount);
        netDue = Money.normalise(netDue);
    }

    /** True when the printed figures agree with each other. */
    public boolean consistent() {
        return net.add(gst).compareTo(gross) == 0 && gross.subtract(discount).compareTo(netDue) == 0;
    }
}

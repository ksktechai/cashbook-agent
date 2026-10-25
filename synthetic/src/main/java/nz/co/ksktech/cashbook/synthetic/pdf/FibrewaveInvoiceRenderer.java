package nz.co.ksktech.cashbook.synthetic.pdf;

import static nz.co.ksktech.cashbook.synthetic.pdf.PdfDoc.HELVETICA;
import static nz.co.ksktech.cashbook.synthetic.pdf.PdfDoc.HELVETICA_BOLD;

import java.nio.file.Path;
import nz.co.ksktech.cashbook.synthetic.Cents;
import nz.co.ksktech.cashbook.synthetic.Fictional;
import nz.co.ksktech.cashbook.synthetic.model.InvoiceSpec;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

/**
 * Orcon-style four-page tax invoice: a summary, Power and Broadband sections with subtotals and discount
 * credits, a General section carrying the card payment fee, and contact details.
 */
public final class FibrewaveInvoiceRenderer {

    private static final PDRectangle SIZE = new PDRectangle(596, 843);
    private static final float LEFT = 41f;
    private static final float RIGHT = 560f;
    private static final int PAGES = 4;

    private FibrewaveInvoiceRenderer() {}

    public static RenderResult render(InvoiceSpec invoice, Path file) {
        try (PdfDoc doc = new PdfDoc(invoice.file(), SIZE)) {
            // Each page's footer is written while the page is open (a finished page cannot be drawn on).
            summary(page(doc), invoice);
            charges(page(doc), invoice);
            general(page(doc), invoice);
            contact(page(doc));
            doc.save(file);
            return new RenderResult(invoice.file(), doc.pageCount(), java.util.List.of());
        }
    }

    private static PdfPage page(PdfDoc doc) {
        PdfPage p = doc.newPage();
        p.text(HELVETICA, 9, LEFT, 816, "GST Number: " + Fictional.FIBREWAVE_GST);
        p.text(HELVETICA, 9, 541, 816, p.number() + " / " + PAGES);
        p.text(HELVETICA, 5, LEFT, 836, Fictional.SYNTHETIC_FOOTER);
        return p;
    }

    private static void amountRow(PdfPage p, float size, float y, String label, String amount) {
        p.text(HELVETICA, size, LEFT, y, label);
        p.textRight(HELVETICA, size, RIGHT, y, amount);
    }

    private static String credit(long cents) {
        return "(credit) " + Formats.dollars(cents);
    }

    private static void summary(PdfPage p, InvoiceSpec inv) {
        p.text(HELVETICA, 22, LEFT, 55, "Kia ora Alex");
        p.text(HELVETICA_BOLD, 30, LEFT, 85, "Here's your Fibrewave Tax Invoice");
        String[][] right = {
            {"Account number", inv.accountNumber()},
            {"Invoice number", inv.invoiceNumber()},
            {"Invoice date", Formats.date(inv.invoiceDate(), Formats.DAY_MON_YEAR)},
            {"Due date", Formats.date(inv.dueDate(), Formats.DAY_MON_YEAR)}
        };
        String[] left = {"4B/12 Sample Street", Fictional.SUBURB, Fictional.CITY_POSTCODE, Fictional.EMAIL};
        for (int i = 0; i < right.length; i++) {
            float y = 127 + i * 17;
            p.text(HELVETICA, 14, LEFT, y, left[i]);
            p.text(HELVETICA, 14, 384, y, right[i][0]);
            p.textRight(HELVETICA, 14, RIGHT, y, right[i][1]);
        }
        p.text(HELVETICA, 10, 388, 198, "Invoice amounts include 15% GST unless stated otherwise");

        p.text(HELVETICA_BOLD, 24, LEFT, 313, "Previous Charges");
        amountRow(p, 13, 335, "Your last invoice (" + Formats.date(inv.previousInvoiceDate(), Formats.DAY_MON_YEAR) + ")",
                Formats.dollars(inv.previousInvoiceCents()));
        amountRow(p, 13, 351, "Payment received - Thank you", "-" + Formats.dollars(inv.previousInvoiceCents()));
        float y = 367;
        if (inv.creditCents() > 0) {
            amountRow(p, 13, y, inv.creditReason(), "-" + Formats.dollars(inv.creditCents()));
            y += 16;
        }
        String opening = "Opening balance (" + Formats.date(inv.invoiceDate(), Formats.DAY_MON_YEAR) + ")";
        amountRow(p, 13, y, opening, inv.creditCents() > 0 ? credit(inv.creditCents()) : Formats.dollars(0));

        p.text(HELVETICA_BOLD, 24, LEFT, 420, "New Charges");
        amountRow(p, 13, 443, "This invoice (" + Formats.date(inv.invoiceDate(), Formats.DAY_MON_YEAR) + ")",
                Formats.dollars(inv.totalCents()));
        long gst = Cents.ratio(inv.totalCents(), 3, 23);
        p.text(HELVETICA, 13, LEFT, 458, "This invoice includes GST amount of " + Formats.dollars(gst));
        p.text(HELVETICA_BOLD, 20, LEFT, 496, "Total amount due (incl. GST)");
        p.textRight(HELVETICA_BOLD, 20, RIGHT, 496, Formats.dollars(inv.dueCents()));

        p.text(HELVETICA_BOLD, 18, LEFT, 663, "Payment options");
        p.text(HELVETICA, 11, LEFT, 698, "Card payments");
        p.text(HELVETICA, 11, 212, 698, "Online payments");
        p.text(HELVETICA, 11, 384, 698, "Internet banking");
        p.text(HELVETICA, 9, LEFT, 714, "You can pay by Credit Card");
        p.text(HELVETICA, 9, 212, 714, "Make a one-off payment by credit or");
        p.text(HELVETICA, 9, 384, 714, "Fibrewave bank account: 99-0001-0000002-000");
        p.text(HELVETICA, 9, 212, 726, "debit card in My Fibrewave.");
        p.text(HELVETICA, 9, 384, 735, "Use your account number (" + inv.accountNumber() + ")");
        p.text(HELVETICA, 9, 384, 747, "as the reference.");
        p.text(HELVETICA, 9, LEFT, 787, "A card payment fee applies to payments made by credit or debit card.");
    }

    private static void charges(PdfPage p, InvoiceSpec inv) {
        p.text(HELVETICA_BOLD, 26, LEFT, 58, "New Charges");
        p.text(HELVETICA_BOLD, 22, LEFT, 102, "Power");
        p.text(HELVETICA, 16, LEFT, 171, "4B/12 Sample Street, Fakeville, Auckland");
        p.text(HELVETICA, 11, 420, 171, "ICP Number 0000999001FW7A1");
        p.text(HELVETICA, 11, LEFT, 188, "Residential Low User");
        p.text(HELVETICA_BOLD, 14, LEFT, 214, "Meter reads");
        String[] headers = {"Meter Ser.No.", "Reg", "Read Period", "Previous Read", "Current Read", "Read Type",
            "Next Read Date", "Units"};
        float[] xs = {41, 107, 150, 262, 335, 399, 454, 532};
        for (int i = 0; i < xs.length; i++) {
            p.text(HELVETICA_BOLD, 8, xs[i], 234, headers[i]);
        }
        String period = Formats.date(inv.periodFrom(), Formats.DAY_MON_YEAR) + " to "
                + Formats.date(inv.periodTo(), Formats.DAY_MON_YEAR);
        long previousRead = 40_000 + (long) inv.invoiceDate().getMonthValue() * 400;
        String[] values = {"999000123", "1", period, previousRead + ".00",
            inv.kwh().add(java.math.BigDecimal.valueOf(previousRead)).setScale(2, java.math.RoundingMode.HALF_UP)
                    .toPlainString(), "Actual", "", inv.kwh().setScale(2, java.math.RoundingMode.HALF_UP) + " kWh"};
        for (int i = 0; i < xs.length; i++) {
            p.text(HELVETICA, 8, xs[i], 250, values[i]);
        }

        p.text(HELVETICA_BOLD, 14, LEFT, 286, "Power Charges");
        float[] cx = {41, 234, 324, 412};
        String[] ch = {"Charge Type", "Period", "Units", "Rate"};
        for (int i = 0; i < cx.length; i++) {
            p.text(HELVETICA_BOLD, 11, cx[i], 307, ch[i]);
        }
        p.textRight(HELVETICA_BOLD, 11, RIGHT, 307, "Total");
        chargeRow(p, 324, "Low User Daily Charge", inv.days() + " days", "$" + inv.dailyRate().toPlainString(),
                Formats.dollars(inv.dailyCents()));
        chargeRow(p, 341, "GST-Inclusive Usage Charge", inv.kwh().toPlainString() + " kWh",
                inv.usageRateCents().toPlainString() + "¢", Formats.dollars(inv.usageCents()));
        amountRow(p, 11, 358, "10% power discount", credit(inv.powerDiscountCents()));
        amountRow(p, 13, 385, "Subtotal", Formats.dollars(inv.powerSubtotalCents()));
        p.text(HELVETICA_BOLD, 16, LEFT, 418, "Power - Subtotal");
        p.textRight(HELVETICA_BOLD, 16, RIGHT, 418, Formats.dollars(inv.powerSubtotalCents()));

        p.text(HELVETICA_BOLD, 22, LEFT, 524, "Broadband");
        p.text(HELVETICA, 16, LEFT, 590, "UNIT 4B, 12 SAMPLE STREET, FAKEVILLE, AUCKLAND 0999");
        String range = Formats.date(inv.periodTo().plusDays(1), Formats.NUMERIC) + " to "
                + Formats.date(inv.periodTo().plusMonths(1), Formats.NUMERIC);
        amountRow(p, 11, 609, "Fibre Max", Formats.dollars(inv.broadbandPlanCents()));
        p.text(HELVETICA, 9, LEFT, 619, range);
        amountRow(p, 11, 636, "$10 broadband discount", credit(inv.broadbandDiscountCents()));
        p.text(HELVETICA, 9, LEFT, 646, range);
        amountRow(p, 13, 672, "Subtotal", Formats.dollars(inv.broadbandSubtotalCents()));
        p.text(HELVETICA_BOLD, 16, LEFT, 705, "Broadband - Subtotal");
        p.textRight(HELVETICA_BOLD, 16, RIGHT, 705, Formats.dollars(inv.broadbandSubtotalCents()));
    }

    private static void chargeRow(PdfPage p, float y, String type, String units, String rate, String total) {
        p.text(HELVETICA, 11, 41, y, type);
        p.text(HELVETICA, 11, 300, y, units);
        p.text(HELVETICA, 11, 405, y, rate);
        p.textRight(HELVETICA, 11, RIGHT, y, total);
    }

    private static void general(PdfPage p, InvoiceSpec inv) {
        p.text(HELVETICA_BOLD, 24, LEFT, 57, "Other");
        p.text(HELVETICA_BOLD, 20, LEFT, 122, "General");
        amountRow(p, 11, 140, "Credit Card Surcharge: Card payment fee", Formats.dollars(inv.cardFeeCents()));
        p.text(HELVETICA_BOLD, 16, LEFT, 169, "Other - Subtotal");
        p.textRight(HELVETICA_BOLD, 16, RIGHT, 169, Formats.dollars(inv.cardFeeCents()));
        p.text(HELVETICA_BOLD, 22, LEFT, 256, "New charges - Total");
        p.textRight(HELVETICA_BOLD, 22, RIGHT, 256, Formats.dollars(inv.totalCents()));
    }

    private static void contact(PdfPage p) {
        p.text(HELVETICA_BOLD, 26, LEFT, 61, "Contact details");
        p.text(HELVETICA, 10, LEFT, 102, "Our friendly customer team is here to help on 0800 00 00 00.");
        p.text(HELVETICA, 10, LEFT, 130, "Fibrewave is a member of the Utilities Disputes free dispute resolution service.");
        p.text(HELVETICA, 10, LEFT, 203, "Faults/Outages: https://status.fibrewave.example");
        p.text(HELVETICA, 10, LEFT, 213, "T&Cs: https://fibrewave.example/terms");
    }
}

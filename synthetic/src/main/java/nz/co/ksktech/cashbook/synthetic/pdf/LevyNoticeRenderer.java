package nz.co.ksktech.cashbook.synthetic.pdf;

import static nz.co.ksktech.cashbook.synthetic.pdf.PdfDoc.HELVETICA;
import static nz.co.ksktech.cashbook.synthetic.pdf.PdfDoc.HELVETICA_BOLD;

import java.nio.file.Path;
import java.util.List;
import nz.co.ksktech.cashbook.synthetic.Cents;
import nz.co.ksktech.cashbook.synthetic.Fictional;
import nz.co.ksktech.cashbook.synthetic.model.LevySpec;
import nz.co.ksktech.cashbook.synthetic.model.LevySpec.LevyLine;

/**
 * Body-corporate levy tax invoice: fund lines with GST, descriptions wrapping over several lines, and an
 * early-payment discount between the gross and net amounts.
 */
public final class LevyNoticeRenderer {

    private LevyNoticeRenderer() {}

    public static RenderResult render(LevySpec levy, Path file) {
        try (PdfDoc doc = new PdfDoc(levy.file(), PdfDoc.A4)) {
            PdfPage p = doc.newPage();
            p.textCentred(HELVETICA_BOLD, 18, 158, "BODY CORPORATE LEVIES");
            p.textCentred(HELVETICA_BOLD, 18, 181, "Tax Invoice");
            String[] address = {Fictional.DIRECTOR, Fictional.UNIT + ",", Fictional.STREET + ",", Fictional.SUBURB,
                Fictional.CITY_POSTCODE};
            for (int i = 0; i < address.length; i++) {
                p.text(HELVETICA, 10, 94, 201 + i * 12, address[i]);
            }
            p.text(HELVETICA, 10, 373, 201, Fictional.BODY_CORP_MANAGER);
            p.text(HELVETICA, 10, 373, 213, "PO Box 999, " + Fictional.SUBURB + ", Auckland");
            p.text(HELVETICA, 10, 373, 225, "Invoice No: " + levy.invoiceNumber());
            p.text(HELVETICA, 10, 470, 225, "Inv Date:");
            p.textRight(HELVETICA, 10, 555, 225, Formats.date(levy.invoiceDate(), Formats.NUMERIC_SHORT));
            p.text(HELVETICA, 10, 373, 237, "GST Number:");
            p.textRight(HELVETICA, 10, 555, 237, Fictional.BODY_CORP_GST);
            p.text(HELVETICA, 10, 373, 249, "Net Amount Due:");
            p.textRight(HELVETICA, 10, 555, 249, "$ " + Cents.format(levy.netDueCents()));
            p.text(HELVETICA_BOLD, 8, 374, 263, "Due Date:");
            p.textRight(HELVETICA_BOLD, 8, 555, 263, Formats.date(levy.dueDate(), Formats.NUMERIC));

            p.text(HELVETICA_BOLD, 9, 48, 295, "Kowhai Apartments - BC No. " + Fictional.BODY_CORP_NUMBER);
            p.text(HELVETICA_BOLD, 8, 51, 311, "Unit No. U12 4B 01");
            p.text(HELVETICA_BOLD, 8, 373, 311, "Previous Balance:");
            p.textRight(HELVETICA_BOLD, 8, 555, 311, "0.00");
            p.text(HELVETICA_BOLD, 8, 51, 322, "Unit Entitlement: 00125");
            p.text(HELVETICA_BOLD, 8, 373, 322, "Penalty Interest:");
            p.textRight(HELVETICA_BOLD, 8, 555, 322, "0.00");

            float[] hx = {51, 243, 316, 432, 473, 518};
            String[] headers = {"Description", "Fund", "Unit rate UE GST%", "Net", "GST", "Totals"};
            for (int i = 0; i < hx.length; i++) {
                p.text(HELVETICA_BOLD, 8, hx[i], 354, headers[i]);
            }
            float y = 373;
            String period = Formats.date(levy.periodFrom(), Formats.NUMERIC) + " to "
                    + Formats.date(levy.periodTo(), Formats.NUMERIC) + " for total Levy of $";
            for (LevyLine line : levy.lines()) {
                p.text(HELVETICA, 8, 50, y, period);
                p.text(HELVETICA, 8, 50, y + 10, Cents.plain(line.annualCents()));
                p.text(HELVETICA, 8, 50, y + 21, "pa");
                String[] fund = line.fund().split(" - ");
                p.text(HELVETICA, 8, 241, y, fund[0] + "   - " + fund[1].replace(" Fund", ""));
                p.text(HELVETICA, 8, 241, y + 10, "Fund");
                p.textRight(HELVETICA, 8, 355, y, Cents.format(line.netCents()));
                p.text(HELVETICA, 8, 363, y, "1");
                p.text(HELVETICA, 8, 374, y, "15.00");
                p.textRight(HELVETICA, 8, 455, y, Cents.format(line.netCents()) + "*");
                p.textRight(HELVETICA, 8, 497, y, Cents.format(line.gstCents()));
                p.textRight(HELVETICA, 8, 545, y, Cents.format(line.totalCents()));
                y += 33;
            }
            y += 4;
            p.text(HELVETICA_BOLD, 8, 51, y, "Current Invoice Amount");
            p.textRight(HELVETICA_BOLD, 8, 455, y, Cents.format(levy.netTotalCents()));
            p.textRight(HELVETICA_BOLD, 8, 497, y, Cents.format(levy.gstTotalCents()));
            p.textRight(HELVETICA_BOLD, 8, 545, y, Cents.format(levy.grossCents()));
            String due = Formats.date(levy.dueDate(), Formats.NUMERIC);
            List<String[]> totals = List.of(
                    new String[] {"Arrears/Credit at time of printing", "0.00"},
                    new String[] {"Gross Amount", Cents.format(levy.grossCents())},
                    new String[] {"Discount if payment received by due date of " + due,
                        Cents.format(levy.discountCents())});
            y += 23;
            for (String[] t : totals) {
                p.text(HELVETICA_BOLD, 8, 51, y, t[0]);
                p.textRight(HELVETICA_BOLD, 8, 545, y, t[1]);
                y += 12;
            }
            p.text(HELVETICA_BOLD, 12, 51, y + 2, "Net Amount Due");
            p.textRight(HELVETICA_BOLD, 12, 545, y + 2, "$ " + Cents.format(levy.netDueCents()));
            p.text(HELVETICA, 7, 51, y + 15, "* A Discount of " + levy.discountPercent()
                    + ".00% applies to items marked * if paid in full by the Due Date.");

            p.text(HELVETICA_BOLD, 10, 51, 661, "Payment Options");
            p.text(HELVETICA_BOLD, 10, 372, 661, Fictional.BODY_CORP_MANAGER);
            p.text(HELVETICA, 8, 372, 671, "Unit No. U12 4B 01");
            p.text(HELVETICA_BOLD, 8, 372, 693, "Invoice No:");
            p.textRight(HELVETICA_BOLD, 8, 545, 693, levy.invoiceNumber());
            p.text(HELVETICA_BOLD, 8, 372, 715, "Amount Paid Before Due Date:");
            p.textRight(HELVETICA_BOLD, 8, 545, 715, "$ " + Cents.format(levy.netDueCents()));
            p.text(HELVETICA_BOLD, 8, 372, 726, "Due Date:");
            p.textRight(HELVETICA_BOLD, 8, 545, 726, due);
            p.text(HELVETICA_BOLD, 8, 51, 756, "Direct Credit:");
            p.text(HELVETICA_BOLD, 8, 51, 767, "Bank Name: " + Fictional.KAURI);
            p.text(HELVETICA_BOLD, 8, 51, 777, "Account Name: " + Fictional.BODY_CORP_NUMBER + " Kowhai BC Trust");
            p.text(HELVETICA_BOLD, 8, 51, 788, "Bank Account: 99-0001-0000001-000");
            p.text(HELVETICA_BOLD, 8, 51, 798, "Reference: L000" + levy.invoiceNumber());
            p.text(HELVETICA, 5, 51, 836, Fictional.SYNTHETIC_FOOTER);
            doc.save(file);
            return new RenderResult(levy.file(), doc.pageCount(), List.of());
        }
    }
}

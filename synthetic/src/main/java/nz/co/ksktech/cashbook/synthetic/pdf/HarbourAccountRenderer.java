package nz.co.ksktech.cashbook.synthetic.pdf;

import static nz.co.ksktech.cashbook.synthetic.pdf.PdfDoc.HELVETICA;
import static nz.co.ksktech.cashbook.synthetic.pdf.PdfDoc.HELVETICA_BOLD;
import static nz.co.ksktech.cashbook.synthetic.pdf.PdfDoc.TIMES;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import nz.co.ksktech.cashbook.synthetic.Fictional;
import nz.co.ksktech.cashbook.synthetic.model.SpecLine;
import nz.co.ksktech.cashbook.synthetic.model.StatementSpec;

/**
 * ASB-style account statement: "– $" and "+ $" amounts, and two-line descriptions whose lines sit above
 * and below the line carrying the date and amount.
 */
public final class HarbourAccountRenderer {

    private static final float SIZE = 10f;
    private static final float ROW = 19.5f;
    private static final float TWO_LINE_ROW = 25f;
    private static final float OUT_RIGHT = 375f;
    private static final float IN_RIGHT = 480f;
    private static final float BALANCE_RIGHT = 565f;

    private HarbourAccountRenderer() {}

    public static RenderResult render(StatementSpec spec, Path file) {
        List<RenderedLine> rendered = new ArrayList<>();
        try (PdfDoc doc = new PdfDoc(spec.file(), PdfDoc.A4)) {
            Blocks blocks = new Blocks(197, 60, 790);
            blocks.add(ROW, (p, y) -> {
                p.text(HELVETICA, SIZE, 38, y, Formats.date(spec.from(), Formats.DAY_MON_YEAR));
                p.text(HELVETICA, SIZE, 102, y, "Opening Balance");
                p.textRight(HELVETICA, SIZE, BALANCE_RIGHT, y, Formats.dollars(spec.openingCents()));
            });
            long balance = spec.openingCents();
            for (SpecLine line : spec.lines()) {
                balance += line.cents();
                long shown = balance;
                boolean twoLines = line.description().contains("\n");
                blocks.add(twoLines ? TWO_LINE_ROW : ROW, (p, y) -> rendered.add(row(p, twoLines ? y + 6 : y, line, shown)));
            }
            long closing = balance;
            blocks.add(40, (p, y) -> {
                p.text(HELVETICA, SIZE, 38, y, Formats.date(spec.to(), Formats.DAY_MON_YEAR));
                p.text(HELVETICA, SIZE, 102, y, "Closing date");
                p.text(HELVETICA, SIZE, 418, y, "Closing Balance");
                p.textRight(HELVETICA, SIZE, BALANCE_RIGHT, y, Formats.dollars(closing));
                p.text(HELVETICA, SIZE, 415, y + 19, "Account Balance");
                p.textRight(HELVETICA, SIZE, BALANCE_RIGHT, y + 19, Formats.dollars(closing));
            });
            blocks.add(170, (p, y) -> {
                p.text(HELVETICA, 8, 28, y + 12, "** The Offshore Service Margin is 2.50% for a foreign currency "
                        + "transaction and 1.95% for a Visa Debit NZD transaction.");
                p.text(HELVETICA, 8, 28, y + 29, "Transactions processed on a non-business day may appear on your "
                        + "Statement the next business day.");
                p.text(HELVETICA_BOLD, 9, 81, y + 150, "CONTACT US 0800 000 000     " + Fictional.HARBOURSIDE
                        + " Limited, PO Box 99, " + Fictional.SUBURB + ", " + Fictional.CITY_POSTCODE
                        + ", New Zealand     harbourside.example");
            });
            blocks.draw(doc, (p, total) -> furniture(spec, p, total));
            doc.save(file);
            return new RenderResult(spec.file(), doc.pageCount(), rendered);
        }
    }

    private static void furniture(StatementSpec spec, PdfPage p, int total) {
        p.text(TIMES, 10, 24, 23, "5/04/27, 9:20 AM");
        p.text(TIMES, 10, 323, 23, "Harbourside Online");
        p.text(TIMES, 10, 24, 825, "https://online.harbourside.example/service/account/transactions?page=" + p.number());
        p.text(TIMES, 10, 561, 825, p.number() + "/" + total);
        p.text(HELVETICA, 5, 24, 836, Fictional.SYNTHETIC_FOOTER);
        if (p.number() != 1) {
            return;
        }
        p.textRight(HELVETICA_BOLD, 13, 560, 41, "Transactions");
        p.text(HELVETICA, 9, 434, 74, "Account Number: " + spec.accountNumber());
        p.text(HELVETICA_BOLD, 9, 29, 79, "Name:");
        p.text(HELVETICA_BOLD, 9, 58, 79, Fictional.DIRECTOR_INITIALS);
        p.text(HELVETICA, 9, 434, 87, "From Date:");
        p.text(HELVETICA, 9, 489, 87, Formats.date(spec.from(), Formats.ISO));
        p.text(HELVETICA_BOLD, 9, 29, 97, "Address:");
        p.text(HELVETICA, 9, 434, 99, "To Date:");
        p.text(HELVETICA, 9, 489, 99, Formats.date(spec.to(), Formats.ISO));
        p.text(HELVETICA, 9, 58, 107, "4B/12 SAMPLE STREET");
        p.text(HELVETICA, 9, 58, 118, "FAKEVILLE AUCKLAND 0999");
        p.text(HELVETICA_BOLD, 11, 28, 154, "Transaction Details");
        float[] xs = {38, 102, 321, 440, 527};
        String[] headers = {"Date", "Description", "Money out", "Money in", "Balance"};
        for (int i = 0; i < xs.length; i++) {
            p.text(HELVETICA_BOLD, SIZE, xs[i], 176, headers[i]);
        }
    }

    private static RenderedLine row(PdfPage p, float y, SpecLine line, long balance) {
        Row row = new Row(line, p);
        String date = Formats.date(line.date(), Formats.DAY_MON_YEAR);
        row.add(p.text(HELVETICA, SIZE, 38, y, date), date);
        String[] parts = line.description().split("\n");
        if (parts.length == 1) {
            row.add(p.text(HELVETICA, SIZE, 102, y, parts[0]), parts[0]);
        } else {
            row.add(p.text(HELVETICA, SIZE, 102, y - 6, parts[0]), parts[0]);
            row.add(p.text(HELVETICA, SIZE, 102, y + 6, parts[1]), parts[1]);
        }
        String amount = line.cents() < 0 ? "– " + Formats.dollars(line.cents()) : "+ " + Formats.dollars(line.cents());
        row.add(p.textRight(HELVETICA, SIZE, line.cents() < 0 ? OUT_RIGHT : IN_RIGHT, y, amount), amount);
        String shown = balance < 0 ? "– " + Formats.dollars(balance) : Formats.dollars(balance);
        row.add(p.textRight(HELVETICA, SIZE, BALANCE_RIGHT, y, shown), shown);
        return row.rendered();
    }
}

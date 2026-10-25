package nz.co.ksktech.cashbook.synthetic.pdf;

import static nz.co.ksktech.cashbook.synthetic.pdf.PdfDoc.HELVETICA;
import static nz.co.ksktech.cashbook.synthetic.pdf.PdfDoc.HELVETICA_BOLD;
import static nz.co.ksktech.cashbook.synthetic.pdf.PdfDoc.TIMES;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import nz.co.ksktech.cashbook.core.BoundingBox;
import nz.co.ksktech.cashbook.synthetic.Cents;
import nz.co.ksktech.cashbook.synthetic.Fictional;
import nz.co.ksktech.cashbook.synthetic.model.ForeignSpec;
import nz.co.ksktech.cashbook.synthetic.model.SpecLine;
import nz.co.ksktech.cashbook.synthetic.model.StatementSpec;
import nz.co.ksktech.cashbook.synthetic.model.Trap;

/**
 * ASB-style Visa statement printed from the browser: oldest first, 12pt rows, CR credits, a running
 * balance, foreign charges over three lines and offshore margins on their own line.
 */
public final class HarbourVisaRenderer {

    /** The real print reports 12pt but is scaled down by the browser; 10pt matches its widths. */
    private static final float SIZE = 10f;
    private static final float ROW = 20.3f;
    private static final float FOREIGN_ROW = 28f;
    private static final float DESCRIPTION_X = 122f;
    private static final String PRINTED = "5/04/27, 9:14 AM";

    private HarbourVisaRenderer() {}

    public static RenderResult render(StatementSpec spec, Path file) {
        List<RenderedLine> rendered = new ArrayList<>();
        try (PdfDoc doc = new PdfDoc(spec.file(), PdfDoc.A4)) {
            Blocks blocks = new Blocks(251, 60, 805);
            long balance = spec.openingCents();
            for (SpecLine line : spec.lines()) {
                balance -= line.cents();
                long shown = balance;
                boolean foreign = line.foreign() != null;
                if (foreign) {
                    // the merchant line sits above the row, so the row itself starts lower
                    blocks.add(10, (p, y) -> {});
                }
                blocks.add(foreign ? FOREIGN_ROW : ROW, (p, y) -> rendered.add(row(spec, p, y, line, shown)));
            }
            blocks.add(60, (p, y) -> p.text(HELVETICA_BOLD, 9, 80, y + 40, "CONTACT US 0800 000 000     "
                    + Fictional.HARBOURSIDE + " Limited, PO Box 99, " + Fictional.SUBURB + ", "
                    + Fictional.CITY_POSTCODE + ", New Zealand     harbourside.example"));
            blocks.draw(doc, (p, total) -> furniture(spec, p, total));
            doc.save(file);
            return new RenderResult(spec.file(), doc.pageCount(), rendered);
        }
    }

    private static void furniture(StatementSpec spec, PdfPage p, int total) {
        p.text(TIMES, 10, 24, 23, PRINTED);
        p.text(TIMES, 10, 302, 23, "Harbourside NetBank: Statement");
        p.text(TIMES, 10, 24, 825, "https://netbank.harbourside.example/fnc/1/statement/visa/" + p.number());
        p.text(TIMES, 10, 561, 825, p.number() + "/" + total);
        p.text(HELVETICA, 5, 24, 836, Fictional.SYNTHETIC_FOOTER);
        if (p.number() == 1) {
            p.textRight(HELVETICA_BOLD, 20, 575, 59, "Visa Statement");
            p.text(HELVETICA_BOLD, 11, 356, 104, "Card Number:");
            p.text(HELVETICA_BOLD, 11, 434, 104, "XXXX-XXXX-XXXX-" + spec.accountNumber());
            p.text(HELVETICA_BOLD, SIZE, 30, 134, "Name: " + Fictional.DIRECTOR_INITIALS);
            p.text(HELVETICA_BOLD, 16, 28, 200, Formats.date(spec.from(), Formats.DAY_MONTH_YEAR) + " - "
                    + Formats.date(spec.to(), Formats.DAY_MONTH_YEAR));
            float[] xs = {32, 60, 124, 381, 431, 513};
            String[] headers = {"Card", "Date", "Description", "Amount", "Date Processed", "Balance"};
            for (int i = 0; i < xs.length; i++) {
                p.text(HELVETICA_BOLD, SIZE, xs[i], 232, headers[i]);
            }
        }
    }

    private static RenderedLine row(StatementSpec spec, PdfPage p, float y, SpecLine line, long balance) {
        Row row = new Row(line, p);
        ForeignSpec foreign = line.foreign();
        BoundingBox description;
        if (foreign != null) {
            BoundingBox merchant = row.add(p.text(HELVETICA, SIZE, DESCRIPTION_X, y - 10, line.description()),
                    line.description());
            String amount = Cents.plain(foreign.foreignCents()) + " " + foreign.currencyName();
            BoundingBox main = row.add(p.text(HELVETICA, SIZE, DESCRIPTION_X, y, amount), amount);
            String conversion = "at a Conversion Rate* of " + foreign.rate() + " (NZ$" + Cents.format(line.cents()) + ")";
            row.add(p.text(HELVETICA, SIZE, DESCRIPTION_X, y + 11, conversion), conversion);
            description = Row.union(List.of(merchant, main));
        } else {
            description = row.add(p.text(HELVETICA, SIZE, DESCRIPTION_X, y, line.description()), line.description());
        }
        row.add(p.text(HELVETICA, SIZE, 29, y, spec.accountNumber()), spec.accountNumber());
        String date = Formats.date(line.date(), Formats.DAY_MON_YEAR);
        row.add(p.text(HELVETICA, SIZE, 58, y, date), date);
        String amount = line.cents() > 0 ? "CR " + Formats.dollars(line.cents()) : Formats.dollars(line.cents());
        row.add(p.textRight(HELVETICA, SIZE, 425, y, amount), amount);
        String processed = Formats.date(line.processed(), Formats.DAY_MON_YEAR);
        row.add(p.textRight(HELVETICA, SIZE, 505, y, processed), processed);
        String shown = balance < 0 ? "-" + Formats.dollars(balance) : Formats.dollars(balance);
        row.add(p.text(HELVETICA, SIZE, 513, y, shown), shown);
        // Offshore-margin highlights run tall, as a quick highlighter stroke does, and can brush the next row.
        boolean tall = line.traps().contains(Trap.OFFSHORE_MARGIN);
        row.highlight(description, 1f, 1f, tall ? 9f : 1f, false);
        return row.rendered();
    }
}

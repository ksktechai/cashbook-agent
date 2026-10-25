package nz.co.ksktech.cashbook.synthetic.pdf;

import static nz.co.ksktech.cashbook.synthetic.pdf.PdfDoc.HELVETICA;
import static nz.co.ksktech.cashbook.synthetic.pdf.PdfDoc.HELVETICA_BOLD;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import nz.co.ksktech.cashbook.synthetic.Cents;
import nz.co.ksktech.cashbook.synthetic.Fictional;
import nz.co.ksktech.cashbook.synthetic.model.SpecLine;
import nz.co.ksktech.cashbook.synthetic.model.StatementSpec;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

/**
 * Sharesies-style spend account statement: oldest first, timestamps instead of dates, currency exchange
 * and fee columns, and column positions that move after the first page.
 */
public final class SharepotRenderer {

    private static final PDRectangle SIZE = new PDRectangle(596, 843);
    private static final float TEXT = 9f;
    private static final float ROW = 19.5f;

    /** Column left edges for headers, and right edges for amounts; later pages use the second set. */
    private record Columns(float date, float description, float exchange, float fee, float deposit,
            float withdrawal, float balance, float exchangeRight, float feeRight, float depositRight,
            float withdrawalRight, float balanceRight) {}

    private static final Columns FIRST = new Columns(63, 122, 236, 316, 373, 430, 487, 300, 350, 412, 480, 540);
    private static final Columns LATER = new Columns(63, 120, 204, 307, 355, 412, 487, 270, 340, 394, 462, 540);

    private SharepotRenderer() {}

    public static RenderResult render(StatementSpec spec, Path file) {
        List<RenderedLine> rendered = new ArrayList<>();
        try (PdfDoc doc = new PdfDoc(spec.file(), SIZE)) {
            Blocks blocks = new Blocks(296, 117, 780);
            blocks.add(ROW, (p, y) -> {
                Columns c = columns(p);
                p.text(HELVETICA, TEXT, c.date() + 1, y, Formats.date(spec.from(), Formats.DAY_MON));
                p.text(HELVETICA, TEXT, c.description() + 1, y, "Opening balance");
                p.textRight(HELVETICA, TEXT, c.balanceRight(), y, Formats.dollars(spec.openingCents()));
            });
            long balance = spec.openingCents();
            for (SpecLine line : spec.lines()) {
                balance += line.cents();
                long shown = balance;
                blocks.add(ROW, (p, y) -> rendered.add(row(p, y, line, shown)));
            }
            long closing = balance;
            blocks.add(ROW, (p, y) -> {
                Columns c = columns(p);
                p.text(HELVETICA, TEXT, c.date() + 1, y, Formats.date(spec.to(), Formats.DAY_MON));
                p.text(HELVETICA, TEXT, c.description() + 1, y, "Closing balance");
                p.textRight(HELVETICA, TEXT, c.balanceRight(), y, Formats.dollars(closing));
            });
            blocks.draw(doc, (p, total) -> furniture(spec, p));
            doc.save(file);
            return new RenderResult(spec.file(), doc.pageCount(), rendered);
        }
    }

    private static Columns columns(PdfPage p) {
        return p.number() == 1 ? FIRST : LATER;
    }

    private static void furniture(StatementSpec spec, PdfPage p) {
        String period = Formats.date(spec.from(), Formats.DAY_MONTH_YEAR) + "–"
                + Formats.date(spec.to(), Formats.DAY_MONTH_YEAR);
        p.text(HELVETICA, TEXT, 57, 56, "Account statement for the period " + period);
        p.text(HELVETICA, TEXT, 41, 804, Fictional.SHAREPOT + ", Level 9, 99 Example Street, " + Fictional.SUBURB + ".");
        p.text(HELVETICA, TEXT, 280, 804, "PO Box 99999, " + Fictional.SUBURB + " 9999, New Zealand");
        p.text(HELVETICA, TEXT, 508, 804, "sharepot.example");
        p.text(HELVETICA, 5, 41, 836, Fictional.SYNTHETIC_FOOTER);
        float header = 98;
        if (p.number() == 1) {
            p.text(HELVETICA_BOLD, 26, 57, 133, spec.accountName());
            p.text(HELVETICA, 16, 57, 152, "Account statement for the period");
            p.text(HELVETICA, 16, 57, 167, period);
            String[][] details = {
                {"Name", Fictional.DIRECTOR},
                {"Address", "4B/12, Sample Street, Fakeville, Auckland, 0999, New Zealand"},
                {"Spend account number", spec.accountNumber()},
                {"Account opened", "12 November 2023"},
                {"Statement generated at", "2 April 2027 09:14am"}
            };
            float y = 193;
            for (String[] d : details) {
                p.text(HELVETICA, 13, 57, y, d[0]);
                p.text(HELVETICA, 13, 57 + PdfPage.width(HELVETICA, 13, d[0]) + 10, y, d[1]);
                y += 13;
            }
            header = 278;
        }
        Columns c = columns(p);
        float[] xs = {c.date(), c.description(), c.exchange(), c.fee(), c.deposit(), c.withdrawal(), c.balance()};
        String[] headers = {"Date", "Description", "Currency exchange", "Fee", "Deposit", "Withdrawal", "Balance (NZD)"};
        for (int i = 0; i < xs.length; i++) {
            p.text(HELVETICA_BOLD, 8, xs[i], header, headers[i]);
        }
    }

    private static RenderedLine row(PdfPage p, float y, SpecLine line, long balance) {
        Columns c = columns(p);
        Row row = new Row(line, p);
        String when = Formats.date(line.date(), Formats.DAY_MON) + " " + time(line.id());
        row.add(p.text(HELVETICA, TEXT, c.date() + 1, y, when), when);
        row.highlight(row.add(p.text(HELVETICA, TEXT, c.description() + 1, y, line.description()), line.description()),
                1f, 1.5f, 1.5f, false);
        if (line.foreign() != null && line.cents() != 0) {
            String bought = "$" + Cents.format(line.foreign().foreignCents()) + " " + line.foreign().currency();
            row.add(p.textRight(HELVETICA, TEXT, c.exchangeRight(), y, bought), bought);
            String fee = Formats.dollars(line.fxFeeCents());
            row.add(p.textRight(HELVETICA, TEXT, c.feeRight(), y, fee), fee);
        }
        String amount;
        float right;
        if (line.cents() == 0 && line.foreign() != null) {
            amount = "-$" + Cents.format(line.foreign().foreignCents()) + " " + line.foreign().currency();
            right = c.withdrawalRight();
        } else if (line.cents() < 0) {
            amount = "-" + Formats.dollars(line.cents()) + " NZD";
            right = c.withdrawalRight();
        } else {
            amount = Formats.dollars(line.cents()) + " NZD";
            right = c.depositRight();
        }
        row.add(p.textRight(HELVETICA, TEXT, right, y, amount), amount);
        String shown = Formats.dollars(balance);
        row.add(p.textRight(HELVETICA, TEXT, c.balanceRight(), y, shown), shown);
        return row.rendered();
    }

    /** A stable hh:mm for a line, derived from its id. */
    private static String time(String id) {
        int h = Math.floorMod(id.hashCode(), 14) + 7;
        int m = Math.floorMod(id.hashCode() / 14, 60);
        return String.format(java.util.Locale.ROOT, "%02d:%02d", h, m);
    }
}

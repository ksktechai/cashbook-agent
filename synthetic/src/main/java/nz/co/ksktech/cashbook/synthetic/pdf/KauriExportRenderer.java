package nz.co.ksktech.cashbook.synthetic.pdf;

import static nz.co.ksktech.cashbook.synthetic.pdf.PdfDoc.HELVETICA;
import static nz.co.ksktech.cashbook.synthetic.pdf.PdfDoc.HELVETICA_BOLD;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nz.co.ksktech.cashbook.core.BoundingBox;
import nz.co.ksktech.cashbook.synthetic.Fictional;
import nz.co.ksktech.cashbook.synthetic.model.Layout;
import nz.co.ksktech.cashbook.synthetic.model.SpecLine;
import nz.co.ksktech.cashbook.synthetic.model.StatementSpec;

/**
 * Westpac-style "Transaction search" export: newest first, Helvetica 7pt, an end-of-day balance row after
 * each day (accounts only), the opening balance at the very end and a totals block.
 */
public final class KauriExportRenderer {

    private static final float SIZE = 7f;
    private static final float ROW = 18f;
    private static final float WRAP = 8f;
    private static final float FIRST_ROW = 140f;
    private static final float NEXT_PAGE_FIRST_ROW = 51f;
    private static final float LAST_ROW = 810f;
    private static final Set<String> WRAPPING_TYPES = Set.of("EFTPOS TRANSACTION", "AUTOMATIC PAYMENT");
    private static final String END_OF_DAY = "Balance at end of day";

    private record Column(String header, float x) {}

    private static final List<Column> ACCOUNT_COLUMNS = List.of(new Column("Date", 45), new Column("Description", 95),
            new Column("Details", 215), new Column("Type", 335), new Column("Money out", 417),
            new Column("Money in", 467), new Column("Balance", 512));
    private static final List<Column> CARD_COLUMNS = List.of(new Column("Date", 45), new Column("Card", 100),
            new Column("Description", 176), new Column("Type", 346), new Column("Reference", 404),
            new Column("Money out", 457), new Column("Money in", 512));

    private final StatementSpec spec;
    private final boolean cardLayout;
    private final List<RenderedLine> rendered = new ArrayList<>();
    private PdfDoc doc;
    private PdfPage page;
    private float y;

    private KauriExportRenderer(StatementSpec spec) {
        this.spec = spec;
        this.cardLayout = spec.layout() == Layout.KAURI_CARD_EXPORT;
    }

    public static RenderResult render(StatementSpec spec, Path file) {
        return new KauriExportRenderer(spec).run(file);
    }

    private RenderResult run(Path file) {
        try (PdfDoc d = new PdfDoc(spec.file(), PdfDoc.A4)) {
            doc = d;
            firstPage();
            Map<LocalDate, List<SpecLine>> byDay = new LinkedHashMap<>();
            for (SpecLine line : spec.lines().reversed()) {
                byDay.computeIfAbsent(line.date(), k -> new ArrayList<>()).add(line);
            }
            Map<LocalDate, Long> endOfDay = endOfDayBalances();
            for (Map.Entry<LocalDate, List<SpecLine>> day : byDay.entrySet()) {
                for (SpecLine line : day.getValue()) {
                    transaction(line);
                }
                if (!cardLayout) {
                    balanceRow(day.getKey(), END_OF_DAY, endOfDay.get(day.getKey()), false);
                }
            }
            if (!cardLayout) {
                balanceRow(spec.from(), "Opening balance for transactions", spec.openingCents(), true);
            }
            totals();
            d.save(file);
            return new RenderResult(spec.file(), d.pageCount(), rendered);
        }
    }

    private Map<LocalDate, Long> endOfDayBalances() {
        Map<LocalDate, Long> balances = new LinkedHashMap<>();
        long balance = spec.openingCents();
        for (SpecLine line : spec.lines()) {
            balance += line.cents();
            balances.put(line.date(), balance);
        }
        return balances;
    }

    private void firstPage() {
        page = doc.newPage();
        page.textRight(HELVETICA, SIZE, 560, 30, Formats.date(LocalDate.of(2027, 4, 5), Formats.DAY_MON_YEAR));
        page.text(HELVETICA, 14, 150, 55, "Transaction search");
        String account = cardLayout
                ? spec.accountName() + " **** **** **** " + spec.accountNumber()
                : spec.accountName() + " " + spec.accountNumber();
        page.text(HELVETICA, SIZE, 39, 100, "Transactions for  " + account + " between "
                + Formats.date(spec.from(), Formats.DAY_MON_YEAR) + " and "
                + Formats.date(spec.to(), Formats.DAY_MON_YEAR));
        for (Column c : columns()) {
            page.text(HELVETICA_BOLD, 8, c.x(), 122, c.header());
        }
        pageFurniture();
        y = FIRST_ROW;
    }

    private void ensureRoom(float height) {
        if (y + height > LAST_ROW) {
            page = doc.newPage();
            pageFurniture();
            y = NEXT_PAGE_FIRST_ROW;
        }
    }

    private void pageFurniture() {
        page.text(HELVETICA_BOLD, SIZE, 570, 830, Integer.toString(page.number()));
        page.text(HELVETICA, 5, 45, 838, Fictional.SYNTHETIC_FOOTER);
    }

    private List<Column> columns() {
        return cardLayout ? CARD_COLUMNS : ACCOUNT_COLUMNS;
    }

    private float x(String header) {
        return columns().stream().filter(c -> c.header().equals(header)).findFirst().orElseThrow().x();
    }

    private void transaction(SpecLine line) {
        boolean wraps = WRAPPING_TYPES.contains(line.type());
        ensureRoom(wraps ? ROW + WRAP : ROW);
        Row row = new Row(line, page);
        String date = Formats.date(line.date(), Formats.DAY_MON_YEAR);
        row.add(page.text(HELVETICA, SIZE, x("Date"), y, date), date);
        if (cardLayout) {
            String cardNumber = "* ****-****-****-" + spec.accountNumber();
            row.add(page.text(HELVETICA, SIZE, x("Card"), y, cardNumber), cardNumber);
            row.add(page.text(HELVETICA, SIZE, x("Reference"), y, line.details()), line.details());
        } else {
            row.add(page.text(HELVETICA, SIZE, x("Details"), y, line.details()), line.details());
        }
        row.add(page.text(HELVETICA, SIZE, x("Description"), y, line.description()), line.description());
        if (wraps) {
            int space = line.type().lastIndexOf(' ');
            String first = line.type().substring(0, space);
            String second = line.type().substring(space + 1);
            row.add(page.text(HELVETICA, SIZE, x("Type"), y, first), first);
            row.add(page.text(HELVETICA, SIZE, x("Type"), y + WRAP, second), second);
        } else {
            row.add(page.text(HELVETICA, SIZE, x("Type"), y, line.type()), line.type());
        }
        String amount = Formats.dollars(line.cents());
        BoundingBox amountBox = row.add(
                page.text(HELVETICA, SIZE, x(line.cents() < 0 ? "Money out" : "Money in"), y, amount), amount);
        // Like the real highlights, mark mostly the amount: about 6-10% of the line's box.
        row.highlight(amountBox, 3f, 1.5f, 1.5f, spec.flattenHighlights());
        rendered.add(row.rendered());
        y += wraps ? ROW + WRAP : ROW;
    }

    private void balanceRow(LocalDate date, String label, long balance, boolean twoLines) {
        ensureRoom(twoLines ? ROW + WRAP : ROW);
        page.text(HELVETICA, SIZE, x("Date"), y, Formats.date(date, Formats.DAY_MON_YEAR));
        if (twoLines) {
            int split = label.lastIndexOf(' ');
            for (String column : List.of("Description", "Details")) {
                page.text(HELVETICA, SIZE, x(column), y, label.substring(0, split));
                page.text(HELVETICA, SIZE, x(column), y + WRAP, label.substring(split + 1));
            }
        } else {
            page.text(HELVETICA, SIZE, x("Description"), y, label);
            page.text(HELVETICA, SIZE, x("Details"), y, label);
        }
        page.text(HELVETICA, SIZE, x("Balance"), y, Formats.signedSpaced(balance));
        y += twoLines ? ROW + WRAP : ROW;
    }

    private void totals() {
        ensureRoom(45);
        y += 9;
        long in = spec.moneyInCents();
        long out = spec.moneyOutCents();
        String[][] rows = {
            {"Total Money In", Formats.dollars(in)},
            {"Total Money Out", "- " + Formats.dollars(out)},
            {"Difference", Formats.signedSpaced(in - out)}
        };
        for (String[] r : rows) {
            page.text(HELVETICA_BOLD, SIZE, 446, y, r[0]);
            page.textRight(HELVETICA_BOLD, SIZE, 560, y, r[1]);
            y += 10;
        }
    }
}

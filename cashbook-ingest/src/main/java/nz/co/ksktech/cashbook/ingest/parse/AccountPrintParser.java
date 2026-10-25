package nz.co.ksktech.cashbook.ingest.parse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import nz.co.ksktech.cashbook.core.AccountType;
import nz.co.ksktech.cashbook.core.StatementSummary;
import nz.co.ksktech.cashbook.core.Transaction;
import nz.co.ksktech.cashbook.ingest.IngestOptions;
import nz.co.ksktech.cashbook.ingest.highlight.Highlights;
import nz.co.ksktech.cashbook.ingest.text.PageText;
import nz.co.ksktech.cashbook.ingest.text.TextLine;

/**
 * ASB-style account statement: "– $" money out and "+ $" money in, and two-line descriptions whose lines
 * sit just above and just below the line carrying the date and amount.
 */
public final class AccountPrintParser implements StatementParser {

    private static final String DATE = "Date";
    private static final String DESCRIPTION = "Description";
    private static final String MONEY_OUT = "Money out";
    private static final String MONEY_IN = "Money in";
    private static final String BALANCE = "Balance";
    private static final List<String> HEADERS = List.of(DATE, DESCRIPTION, MONEY_OUT, MONEY_IN, BALANCE);
    private static final String TITLE = "Transaction Details";
    private static final String FROM = "From Date:";
    private static final String OPENING = "Opening Balance";
    private static final String CLOSING = "Closing date";
    private static final Pattern ACCOUNT = Pattern.compile("Account Number:\\s*([\\d-]+)");
    private static final Pattern FROM_DATE = Pattern.compile("From Date:\\s*(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern TO_DATE = Pattern.compile("To Date:\\s*(\\d{4}-\\d{2}-\\d{2})");
    /** A description line belongs to the dated line within this distance (they straddle it by ~6pt). */
    private static final float STRADDLE = 8f;

    @Override
    public String layout() {
        return "ACCOUNT_PRINT";
    }

    @Override
    public boolean matches(List<PageText> pages) {
        return StatementParser.firstPageHas(pages, TITLE, FROM);
    }

    @Override
    public Parsed parse(String file, List<PageText> pages, Highlights highlights, IngestOptions options) {
        PageText first = pages.getFirst();
        String number = find(first, ACCOUNT).orElse("");
        LocalDate from = find(first, FROM_DATE).flatMap(Dates::parse).orElse(null);
        LocalDate to = find(first, TO_DATE).flatMap(Dates::parse).orElse(null);
        Columns columns = Columns.findIn(first.lines(), HEADERS).orElseThrow();
        float headerBaseline = first.lines().stream().filter(l -> Columns.find(l, HEADERS).isPresent())
                .findFirst().orElseThrow().baseline();

        List<Transaction> transactions = new ArrayList<>();
        BigDecimal opening = null;
        BigDecimal closing = null;
        for (PageText page : pages) {
            List<RecordBuilder> rows = new ArrayList<>();
            List<TextLine> fragments = new ArrayList<>();
            for (TextLine line : page.lines()) {
                if (page.page() == 1 && line.baseline() <= headerBaseline) {
                    continue;
                }
                Map<String, String> cells = columns.cells(line);
                Optional<LocalDate> date = Dates.parse(cells.get(DATE));
                String description = cells.get(DESCRIPTION);
                if (date.isEmpty()) {
                    if (!description.isEmpty() && cells.get(MONEY_OUT).isEmpty() && cells.get(MONEY_IN).isEmpty()
                            && cells.get(BALANCE).isEmpty()) {
                        fragments.add(line);
                    }
                    continue;
                }
                if (description.startsWith(OPENING)) {
                    opening = Amounts.require(cells.get(BALANCE));
                    continue;
                }
                if (description.startsWith(CLOSING)) {
                    closing = Amounts.require(cells.get(BALANCE));
                    continue;
                }
                Optional<BigDecimal> out = Amounts.parse(cells.get(MONEY_OUT));
                Optional<BigDecimal> in = Amounts.parse(cells.get(MONEY_IN));
                if (out.isEmpty() && in.isEmpty()) {
                    continue;
                }
                RecordBuilder r = new RecordBuilder(line);
                r.date = date.get();
                r.amount = out.map(o -> o.signum() < 0 ? o : o.negate()).orElseGet(in::get);
                if (!description.isEmpty()) {
                    r.descriptionParts.add(description);
                }
                rows.add(r);
            }
            for (TextLine fragment : fragments) {
                rows.stream()
                        .filter(r -> Math.abs(r.lines.getFirst().baseline() - fragment.baseline()) <= STRADDLE)
                        .min(Comparator.comparingDouble(r -> Math.abs(r.lines.getFirst().baseline() - fragment.baseline())))
                        .ifPresent(r -> r.add(fragment));
            }
            for (RecordBuilder r : rows) {
                if (r.lines.size() > 1) { // rebuild the description from its lines, top to bottom
                    r.descriptionParts.clear();
                    r.lines.stream().sorted(Comparator.comparingDouble(TextLine::baseline))
                            .map(l -> columns.cells(l).get(DESCRIPTION))
                            .filter(s -> !s.isEmpty())
                            .forEach(r.descriptionParts::add);
                }
                transactions.add(r.build(file, layout(), AccountType.CHEQUE, highlights));
            }
        }
        StatementSummary summary = new StatementSummary(file, layout(), layout(), AccountType.CHEQUE, "Account",
                number, from, to, opening, closing, null, null, pages.size(), pages.stream().anyMatch(PageText::ocr));
        return new Parsed(summary, transactions);
    }

    private static Optional<String> find(PageText page, Pattern pattern) {
        for (TextLine l : page.lines()) {
            Matcher m = pattern.matcher(l.text());
            if (m.find()) {
                return Optional.of(m.group(1));
            }
        }
        return Optional.empty();
    }
}

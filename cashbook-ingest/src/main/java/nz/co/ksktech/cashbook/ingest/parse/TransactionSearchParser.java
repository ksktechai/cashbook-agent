package nz.co.ksktech.cashbook.ingest.parse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
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
 * Westpac-style "Transaction search" export, account or card variant: newest first, an end-of-day balance
 * row after each day (accounts), the opening balance as the last row (accounts), a Type cell that can wrap
 * onto a second line, and Total Money In / Out at the end.
 */
public final class TransactionSearchParser implements StatementParser {

    private static final String DATE = "Date";
    private static final String DESCRIPTION = "Description";
    private static final String DETAILS = "Details";
    private static final String TYPE = "Type";
    private static final String CARD = "Card";
    private static final String REFERENCE = "Reference";
    private static final String MONEY_OUT = "Money out";
    private static final String MONEY_IN = "Money in";
    private static final String BALANCE = "Balance";
    private static final List<String> ACCOUNT_HEADERS =
            List.of(DATE, DESCRIPTION, DETAILS, TYPE, MONEY_OUT, MONEY_IN, BALANCE);
    private static final List<String> CARD_HEADERS =
            List.of(DATE, CARD, DESCRIPTION, TYPE, REFERENCE, MONEY_OUT, MONEY_IN);
    private static final String TITLE = "Transactions for";
    private static final String END_OF_DAY = "Balance at end of day";
    private static final String OPENING = "Opening balance";
    private static final String TOTAL_IN = "Total Money In";
    private static final String TOTAL_OUT = "Total Money Out";
    private static final Pattern ACCOUNT_TITLE =
            Pattern.compile("Transactions for\\s+(.*?)\\s+([\\d ]{10,})\\s+between\\s+(.+?)\\s+and\\s+(.+)$");
    private static final Pattern CARD_TITLE =
            Pattern.compile("Transactions for\\s+(.*?)\\s+\\*{4} \\*{4} \\*{4} (\\d{4})\\s+between\\s+(.+?)\\s+and\\s+(.+)$");
    /** A continuation (wrapped Type) sits about one font size below its row. */
    private static final float CONTINUATION_GAP = 12f;

    private final boolean card;

    private TransactionSearchParser(boolean card) {
        this.card = card;
    }

    public static TransactionSearchParser account() {
        return new TransactionSearchParser(false);
    }

    public static TransactionSearchParser card() {
        return new TransactionSearchParser(true);
    }

    @Override
    public String layout() {
        return card ? "TRANSACTION_SEARCH_CARD" : "TRANSACTION_SEARCH_ACCOUNT";
    }

    @Override
    public boolean matches(List<PageText> pages) {
        return StatementParser.firstPageHas(pages, TITLE)
                && !pages.isEmpty() && Columns.findIn(pages.getFirst().lines(), headers()).isPresent();
    }

    private List<String> headers() {
        return card ? CARD_HEADERS : ACCOUNT_HEADERS;
    }

    @Override
    public Parsed parse(String file, List<PageText> pages, Highlights highlights, IngestOptions options) {
        PageText first = pages.getFirst();
        TextLine title = first.firstLineContaining(TITLE).orElseThrow();
        Matcher m = (card ? CARD_TITLE : ACCOUNT_TITLE).matcher(title.text());
        if (!m.find()) {
            throw new IllegalArgumentException(file + ": cannot read the account and period from '" + title.text() + "'");
        }
        String name = m.group(1).strip();
        String number = m.group(2).strip();
        LocalDate from = Dates.parse(m.group(3)).orElseThrow();
        LocalDate to = Dates.parse(m.group(4)).orElseThrow();
        AccountType type = card ? AccountType.CREDIT_CARD
                : options.businessAccount().matcher(name).find() ? AccountType.BUSINESS : AccountType.CHEQUE;

        Columns columns = Columns.findIn(first.lines(), headers()).orElseThrow();
        float headerBaseline = first.lines().stream().filter(l -> Columns.find(l, headers()).isPresent())
                .findFirst().orElseThrow().baseline();

        List<Transaction> transactions = new ArrayList<>();
        BigDecimal opening = null;
        BigDecimal closing = null;
        BigDecimal totalIn = null;
        BigDecimal totalOut = null;
        RecordBuilder current = null;
        boolean inOpeningRow = false;
        for (PageText page : pages) {
            for (TextLine line : page.lines()) {
                if (page.page() == 1 && line.baseline() <= headerBaseline) {
                    continue;
                }
                String text = line.text();
                if (text.startsWith(TOTAL_IN)) {
                    totalIn = Amounts.last(text).orElseThrow();
                    current = flush(current, transactions, file, type, highlights);
                    continue;
                }
                if (text.startsWith(TOTAL_OUT)) {
                    totalOut = Amounts.last(text).orElseThrow().abs();
                    current = flush(current, transactions, file, type, highlights);
                    continue;
                }
                Map<String, String> cells = columns.cells(line);
                Optional<LocalDate> date = Dates.parse(cells.get(DATE));
                if (date.isPresent()) {
                    current = flush(current, transactions, file, type, highlights);
                    inOpeningRow = false;
                    String description = cells.get(DESCRIPTION);
                    if (description.startsWith(END_OF_DAY)) {
                        if (closing == null) {
                            closing = Amounts.require(cells.get(BALANCE)); // newest first: the first is the last day
                        }
                        continue;
                    }
                    if (description.startsWith(OPENING)) {
                        opening = Amounts.require(cells.get(BALANCE));
                        inOpeningRow = true;
                        continue;
                    }
                    Optional<BigDecimal> out = Amounts.parse(cells.get(MONEY_OUT));
                    Optional<BigDecimal> in = Amounts.parse(cells.get(MONEY_IN));
                    if (out.isEmpty() && in.isEmpty()) {
                        continue;
                    }
                    current = new RecordBuilder(line);
                    current.date = date.get();
                    current.amount = out.map(BigDecimal::negate).orElseGet(in::get);
                    current.descriptionParts.add(description);
                    if (!card && !cells.get(DETAILS).isEmpty()) {
                        current.descriptionParts.add(cells.get(DETAILS));
                    }
                } else if (current != null && !inOpeningRow && line.page() == current.lines.getFirst().page()
                        && line.baseline() - current.lastBaseline() <= CONTINUATION_GAP
                        && !cells.get(TYPE).isEmpty()) {
                    current.add(line); // the wrapped second half of the Type cell
                } else {
                    current = flush(current, transactions, file, type, highlights);
                }
            }
        }
        flush(current, transactions, file, type, highlights);
        StatementSummary summary = new StatementSummary(file, layout(), layout(), type, name, number, from, to,
                opening, closing, totalIn, totalOut, pages.size(), pages.stream().anyMatch(PageText::ocr));
        return new Parsed(summary, transactions);
    }

    private RecordBuilder flush(RecordBuilder current, List<Transaction> out, String file, AccountType type,
            Highlights highlights) {
        if (current != null) {
            out.add(current.build(file, layout(), type, highlights));
        }
        return null;
    }
}

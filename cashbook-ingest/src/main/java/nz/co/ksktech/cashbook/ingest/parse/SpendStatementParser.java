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
import nz.co.ksktech.cashbook.core.ForeignAmount;
import nz.co.ksktech.cashbook.core.Money;
import nz.co.ksktech.cashbook.core.StatementSummary;
import nz.co.ksktech.cashbook.core.Transaction;
import nz.co.ksktech.cashbook.ingest.IngestOptions;
import nz.co.ksktech.cashbook.ingest.highlight.Highlights;
import nz.co.ksktech.cashbook.ingest.text.PageText;
import nz.co.ksktech.cashbook.ingest.text.TextLine;

/**
 * Sharesies-style spend account statement: timestamped rows, currency exchange and fee columns, NZD
 * balances, and column positions that differ between the first page and the rest (so each page's own
 * header is used).
 */
public final class SpendStatementParser implements StatementParser {

    private static final String DATE = "Date";
    private static final String DESCRIPTION = "Description";
    private static final String EXCHANGE = "Currency exchange";
    private static final String FEE = "Fee";
    private static final String DEPOSIT = "Deposit";
    private static final String WITHDRAWAL = "Withdrawal";
    private static final String BALANCE = "Balance (NZD)";
    private static final List<String> HEADERS = List.of(DATE, DESCRIPTION, EXCHANGE, FEE, DEPOSIT, WITHDRAWAL, BALANCE);
    private static final String TITLE = "Spend account";
    private static final String NZD = "NZD";
    private static final String OPENING = "Opening balance";
    private static final String CLOSING = "Closing balance";
    private static final Pattern PERIOD = Pattern.compile(
            "for the period\\s+(\\d{1,2} [A-Z][a-z]+ \\d{4})\\s*[–-]\\s*(\\d{1,2} [A-Z][a-z]+ \\d{4})");
    private static final Pattern ACCOUNT = Pattern.compile("Spend account number\\s+([\\d-]+)");

    @Override
    public String layout() {
        return "SPEND_STATEMENT";
    }

    @Override
    public boolean matches(List<PageText> pages) {
        return StatementParser.firstPageHas(pages, TITLE, EXCHANGE);
    }

    @Override
    public Parsed parse(String file, List<PageText> pages, Highlights highlights, IngestOptions options) {
        LocalDate from = null;
        LocalDate to = null;
        String number = "";
        for (TextLine l : pages.getFirst().lines()) {
            Matcher p = PERIOD.matcher(l.text());
            if (from == null && p.find()) {
                from = Dates.parse(p.group(1)).orElseThrow();
                to = Dates.parse(p.group(2)).orElseThrow();
            }
            Matcher a = ACCOUNT.matcher(l.text());
            if (a.find()) {
                number = a.group(1);
            }
        }
        if (from == null) {
            throw new IllegalArgumentException(file + ": no statement period found");
        }

        List<Transaction> transactions = new ArrayList<>();
        BigDecimal opening = null;
        BigDecimal closing = null;
        for (PageText page : pages) {
            Optional<TextLine> header = page.lines().stream().filter(l -> Columns.find(l, HEADERS).isPresent()).findFirst();
            if (header.isEmpty()) {
                continue;
            }
            Columns columns = Columns.find(header.get(), HEADERS).orElseThrow();
            for (TextLine line : page.lines()) {
                if (line.baseline() <= header.get().baseline()) {
                    continue;
                }
                Map<String, String> cells = columns.cells(line);
                Optional<LocalDate> date = Dates.parseWithin(cells.get(DATE), from, to);
                if (date.isEmpty()) {
                    continue;
                }
                String description = cells.get(DESCRIPTION);
                if (description.startsWith(OPENING)) {
                    opening = Amounts.require(cells.get(BALANCE));
                    continue;
                }
                if (description.startsWith(CLOSING)) {
                    closing = Amounts.require(cells.get(BALANCE));
                    continue;
                }
                RecordBuilder r = new RecordBuilder(line);
                r.date = date.get();
                r.descriptionParts.add(description);
                r.amount = Money.ZERO;
                String withdrawal = cells.get(WITHDRAWAL);
                String deposit = cells.get(DEPOSIT);
                if (!withdrawal.isEmpty()) {
                    BigDecimal value = Amounts.require(withdrawal);
                    String currency = Amounts.currency(withdrawal).orElse(NZD);
                    if (currency.equals(NZD)) {
                        r.amount = value;
                    } else {
                        r.foreign = new ForeignAmount(value, currency); // paid from a foreign-currency wallet
                    }
                } else if (!deposit.isEmpty()) {
                    r.amount = Amounts.require(deposit);
                }
                Amounts.foreign(cells.get(EXCHANGE)).ifPresent(f -> r.foreign = f);
                Amounts.parse(cells.get(FEE)).ifPresent(f -> r.fee = f.abs());
                transactions.add(r.build(file, layout(), AccountType.SPEND, highlights));
            }
        }
        StatementSummary summary = new StatementSummary(file, layout(), layout(), AccountType.SPEND, TITLE, number,
                from, to, opening, closing, null, null, pages.size(), pages.stream().anyMatch(PageText::ocr));
        return new Parsed(summary, transactions);
    }
}

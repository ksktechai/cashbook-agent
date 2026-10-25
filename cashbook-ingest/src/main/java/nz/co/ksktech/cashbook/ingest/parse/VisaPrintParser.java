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
import nz.co.ksktech.cashbook.core.StatementSummary;
import nz.co.ksktech.cashbook.core.Transaction;
import nz.co.ksktech.cashbook.ingest.IngestOptions;
import nz.co.ksktech.cashbook.ingest.highlight.Highlights;
import nz.co.ksktech.cashbook.ingest.text.PageText;
import nz.co.ksktech.cashbook.ingest.text.TextLine;

/**
 * ASB-style Visa statement printed from the browser: oldest first, credits marked "CR", a running balance
 * (the amount owing), and foreign charges over three lines: the merchant above the row, "12.00 US DOLLAR"
 * on the row, and the conversion rate below it.
 */
public final class VisaPrintParser implements StatementParser {

    private static final String CARD = "Card";
    private static final String DATE = "Date";
    private static final String DESCRIPTION = "Description";
    private static final String AMOUNT = "Amount";
    private static final String PROCESSED = "Date Processed";
    private static final String BALANCE = "Balance";
    private static final List<String> HEADERS = List.of(CARD, DATE, DESCRIPTION, AMOUNT, PROCESSED, BALANCE);
    private static final String CARD_NUMBER = "Card Number:";
    private static final String CONVERSION = "at a Conversion Rate";
    private static final Pattern CARD_DIGITS = Pattern.compile("Card Number:\\s*\\S*?(\\d{4})\\b");
    private static final Pattern PERIOD = Pattern.compile("^(\\d{1,2} [A-Z][a-z]+ \\d{4}) - (\\d{1,2} [A-Z][a-z]+ \\d{4})$");
    private static final Pattern FOREIGN = Pattern.compile("^(\\d[\\d,]*\\.\\d{2}) ([A-Z][A-Z ]+)$");
    private static final Pattern LAST_FOUR = Pattern.compile("^\\d{4}$");
    /** Currency names as the card statements print them. */
    private static final Map<String, String> CURRENCIES = Map.of(
            "US DOLLAR", "USD", "AUSTRALIAN DOLLAR", "AUD", "EURO", "EUR", "POUND STERLING", "GBP",
            "CANADIAN DOLLAR", "CAD", "JAPANESE YEN", "JPY", "SINGAPORE DOLLAR", "SGD");
    /** The merchant line sits about 10pt above its row, the conversion line about 11pt below. */
    private static final float BLOCK_GAP = 14f;

    @Override
    public String layout() {
        return "VISA_PRINT";
    }

    @Override
    public boolean matches(List<PageText> pages) {
        return StatementParser.firstPageHas(pages, CARD_NUMBER, PROCESSED);
    }

    @Override
    public Parsed parse(String file, List<PageText> pages, Highlights highlights, IngestOptions options) {
        PageText first = pages.getFirst();
        Matcher card = CARD_DIGITS.matcher(first.firstLineContaining(CARD_NUMBER).orElseThrow().text());
        String number = card.find() ? card.group(1) : "";
        LocalDate from = null;
        LocalDate to = null;
        for (TextLine l : first.lines()) {
            Matcher p = PERIOD.matcher(l.text());
            if (p.find()) {
                from = Dates.parse(p.group(1)).orElseThrow();
                to = Dates.parse(p.group(2)).orElseThrow();
                break;
            }
        }
        Columns columns = Columns.findIn(first.lines(), HEADERS).orElseThrow();
        float headerBaseline = first.lines().stream().filter(l -> Columns.find(l, HEADERS).isPresent())
                .findFirst().orElseThrow().baseline();

        List<Transaction> transactions = new ArrayList<>();
        BigDecimal opening = null;
        BigDecimal closing = null;
        RecordBuilder last = null;
        TextLine merchant = null;
        for (PageText page : pages) {
            for (TextLine line : page.lines()) {
                if (page.page() == 1 && line.baseline() <= headerBaseline) {
                    continue;
                }
                Map<String, String> cells = columns.cells(line);
                Optional<LocalDate> date = Dates.parse(cells.get(DATE));
                boolean row = LAST_FOUR.matcher(cells.get(CARD)).matches() && date.isPresent();
                String description = cells.get(DESCRIPTION);
                if (!row) {
                    boolean onlyDescription = !description.isEmpty() && cells.get(AMOUNT).isEmpty()
                            && cells.get(BALANCE).isEmpty() && cells.get(CARD).isEmpty();
                    if (onlyDescription && description.startsWith(CONVERSION) && last != null
                            && line.baseline() - last.lastBaseline() <= BLOCK_GAP) {
                        last.add(line);
                    } else if (onlyDescription && !description.startsWith(CONVERSION)) {
                        merchant = line; // may be the first line of a foreign charge
                    }
                    continue;
                }
                RecordBuilder r = new RecordBuilder(line);
                r.date = date.get();
                r.processed = Dates.parse(cells.get(PROCESSED)).orElse(r.date);
                String amountCell = cells.get(AMOUNT);
                BigDecimal amount = Amounts.require(amountCell);
                r.amount = Amounts.isCredit(amountCell) ? amount : amount.negate();
                Matcher foreign = FOREIGN.matcher(description);
                if (merchant != null && merchant.page() == line.page()
                        && line.baseline() - merchant.baseline() <= BLOCK_GAP && foreign.matches()) {
                    r.add(merchant);
                    r.descriptionParts.add(columns.cells(merchant).get(DESCRIPTION));
                    String currency = CURRENCIES.getOrDefault(foreign.group(2).strip(), foreign.group(2).strip());
                    r.foreign = new ForeignAmount(new BigDecimal(foreign.group(1).replace(",", "")), currency);
                } else {
                    r.descriptionParts.add(description);
                }
                merchant = null;
                BigDecimal balance = Amounts.require(cells.get(BALANCE));
                if (opening == null) {
                    opening = balance.add(r.amount); // the balance owing before the first line
                }
                closing = balance;
                if (last != null) {
                    transactions.add(last.build(file, layout(), AccountType.CREDIT_CARD, highlights));
                }
                last = r;
            }
        }
        if (last != null) {
            transactions.add(last.build(file, layout(), AccountType.CREDIT_CARD, highlights));
        }
        StatementSummary summary = new StatementSummary(file, layout(), layout(), AccountType.CREDIT_CARD,
                "Visa", number, from, to, opening, closing, null, null, pages.size(),
                pages.stream().anyMatch(PageText::ocr));
        return new Parsed(summary, transactions);
    }
}

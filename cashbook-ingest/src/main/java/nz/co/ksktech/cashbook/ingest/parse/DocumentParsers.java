package nz.co.ksktech.cashbook.ingest.parse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import nz.co.ksktech.cashbook.core.LevyNotice;
import nz.co.ksktech.cashbook.core.Money;
import nz.co.ksktech.cashbook.core.UtilityInvoice;
import nz.co.ksktech.cashbook.ingest.text.PageText;
import nz.co.ksktech.cashbook.ingest.text.TextLine;

/**
 * Parsers for supplier documents, read by their printed labels: Orcon-style power and broadband invoices
 * and body-corporate levy notices. Labels are matched loosely so OCR'd text still parses.
 */
public final class DocumentParsers {

    private static final String POWER_SUBTOTAL = "Power - Subtotal";
    private static final String BROADBAND_SUBTOTAL = "Broadband - Subtotal";
    private static final String LEVIES = "BODY CORPORATE LEVIES";
    private static final Pattern INVOICE_NO = Pattern.compile("Invoice No:?\\s*(\\d+)");
    private static final Pattern INV_DATE = Pattern.compile("Inv(?:oice)? Date:?\\s*(\\d{1,2}/\\d{2}/\\d{2,4})");
    private static final Pattern DUE_DATE = Pattern.compile("Due Date:?\\s*(\\d{1,2}/\\d{2}/\\d{2,4})");
    private static final Pattern AMOUNTS = Pattern.compile("\\d{1,3}(?:,\\d{3})*\\.\\d{2}");

    private DocumentParsers() {}

    public static boolean isUtilityInvoice(List<PageText> pages) {
        return line(pages, POWER_SUBTOTAL).isPresent() && line(pages, BROADBAND_SUBTOTAL).isPresent();
    }

    public static boolean isLevyNotice(List<PageText> pages) {
        return line(pages, LEVIES).isPresent();
    }

    public static UtilityInvoice utilityInvoice(String file, List<PageText> pages) {
        String number = lastSegment(pages, "Invoice number");
        LocalDate date = Dates.parse(lastSegment(pages, "Invoice date")).orElseThrow();
        LocalDate due = Dates.parse(lastSegment(pages, "Due date")).orElseThrow();
        BigDecimal credit = line(pages, "Opening balance (")
                .filter(l -> l.text().contains("(credit)"))
                .flatMap(l -> Amounts.last(l.text()))
                .map(BigDecimal::abs)
                .orElse(Money.ZERO);
        return new UtilityInvoice(file, number, date, due,
                amountAfter(pages, POWER_SUBTOTAL), amountAfter(pages, BROADBAND_SUBTOTAL),
                line(pages, "Card payment fee").flatMap(l -> Amounts.last(l.text())).orElse(Money.ZERO),
                credit, amountAfter(pages, "This invoice ("), amountAfter(pages, "Total amount due"), ocr(pages));
    }

    public static LevyNotice levyNotice(String file, List<PageText> pages) {
        String all = String.join("\n", pages.stream().flatMap(p -> p.lines().stream()).map(TextLine::text).toList());
        String number = group(INVOICE_NO, all).orElseThrow(() -> new IllegalArgumentException(file + ": no invoice number"));
        LocalDate date = group(INV_DATE, all).flatMap(Dates::parse).orElseThrow();
        LocalDate due = group(DUE_DATE, all).flatMap(Dates::parse).orElseThrow();
        List<BigDecimal> current = amounts(line(pages, "Current Invoice Amount").orElseThrow().text());
        if (current.size() < 3) {
            throw new IllegalArgumentException(file + ": cannot read net, GST and total of the current invoice");
        }
        return new LevyNotice(file, number, date, due, current.get(0), current.get(1),
                amountAfter(pages, "Gross Amount"), amountAfter(pages, "Discount if payment"),
                amountAfter(pages, "Net Amount Due"), ocr(pages));
    }

    // ---------------------------------------------------------------- helpers

    private static boolean ocr(List<PageText> pages) {
        return pages.stream().anyMatch(PageText::ocr);
    }

    /** The first line that starts with (or, failing that, contains) the label. */
    private static Optional<TextLine> line(List<PageText> pages, String label) {
        List<TextLine> all = pages.stream().flatMap(p -> p.lines().stream()).toList();
        return all.stream().filter(l -> l.text().startsWith(label)).findFirst()
                .or(() -> all.stream().filter(l -> l.text().contains(label)).findFirst());
    }

    /** The last amount on the label's line, as printed (signs and "(credit)" aside). */
    private static BigDecimal amountAfter(List<PageText> pages, String label) {
        TextLine l = line(pages, label).orElseThrow(() -> new IllegalArgumentException("no '" + label + "' line"));
        List<BigDecimal> found = amounts(l.text());
        if (found.isEmpty()) {
            throw new IllegalArgumentException("no amount on the '" + label + "' line: " + l.text());
        }
        return found.getLast();
    }

    private static String lastSegment(List<PageText> pages, String label) {
        TextLine l = line(pages, label).orElseThrow(() -> new IllegalArgumentException("no '" + label + "' line"));
        List<TextLine.Segment> segments = l.segments();
        String last = segments.getLast().text();
        return last.startsWith(label) ? last.substring(label.length()).strip() : last;
    }

    private static List<BigDecimal> amounts(String text) {
        Matcher m = AMOUNTS.matcher(text);
        List<BigDecimal> found = new java.util.ArrayList<>();
        while (m.find()) {
            found.add(Money.of(m.group().replace(",", "")));
        }
        return found;
    }

    private static Optional<String> group(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }
}

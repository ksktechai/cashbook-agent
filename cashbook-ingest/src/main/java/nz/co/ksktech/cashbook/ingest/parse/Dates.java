package nz.co.ksktech.cashbook.ingest.parse;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the date formats the statements use. */
public final class Dates {

    private static final List<DateTimeFormatter> FULL = List.of(
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d/MM/yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d/MM/yy", Locale.ENGLISH),
            DateTimeFormatter.ISO_LOCAL_DATE);
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final Pattern DAY_MON_YEAR = Pattern.compile("\\b\\d{1,2} [A-Z][a-z]{2} \\d{4}\\b");
    private static final Pattern DAY_MONTH_YEAR = Pattern.compile("\\b\\d{1,2} [A-Z][a-z]+ \\d{4}\\b");
    private static final Pattern NUMERIC = Pattern.compile("\\b\\d{1,2}/\\d{2}/(?:\\d{4}|\\d{2})\\b");
    private static final Pattern ISO = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b");

    private Dates() {}

    /** A complete date in any supported format, or empty. */
    public static Optional<LocalDate> parse(String text) {
        String s = text.strip();
        for (DateTimeFormatter f : FULL) {
            try {
                return Optional.of(LocalDate.parse(s, f));
            } catch (DateTimeParseException e) {
                // try the next format
            }
        }
        return Optional.empty();
    }

    /** All dates found inside a longer text, in order. */
    public static List<LocalDate> findAll(String text) {
        List<LocalDate> found = new java.util.ArrayList<>();
        for (Pattern p : List.of(DAY_MONTH_YEAR, DAY_MON_YEAR, NUMERIC, ISO)) {
            Matcher m = p.matcher(text);
            while (m.find()) {
                parse(m.group()).ifPresent(d -> found.add(d));
            }
            if (!found.isEmpty()) {
                return found;
            }
        }
        return found;
    }

    /** "9 Oct" (any trailing time ignored), with the year that puts it inside [from, to]. */
    public static Optional<LocalDate> parseWithin(String text, LocalDate from, LocalDate to) {
        String[] parts = text.strip().split("\\s+");
        if (parts.length < 2) {
            return Optional.empty();
        }
        try {
            MonthDay md = MonthDay.parse(parts[0] + " " + parts[1], DAY_MONTH);
            for (int year = from.getYear(); year <= to.getYear(); year++) {
                LocalDate candidate = md.atYear(year);
                if (!candidate.isBefore(from) && !candidate.isAfter(to)) {
                    return Optional.of(candidate);
                }
            }
            return Optional.of(md.atYear(from.getYear()));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}

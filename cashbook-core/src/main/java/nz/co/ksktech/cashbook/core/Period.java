package nz.co.ksktech.cashbook.core;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/** A GST period of whole months, inclusive at both ends, e.g. {@code 2026-10:2027-03}. */
public record Period(YearMonth start, YearMonth end) {

    private static final String SEPARATOR = ":";

    public Period {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("period end " + end + " is before start " + start);
        }
    }

    /** Parses {@code YYYY-MM:YYYY-MM}. */
    public static Period parse(String text) {
        Objects.requireNonNull(text, "text");
        String[] parts = text.split(SEPARATOR, -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("period must be YYYY-MM:YYYY-MM but was '" + text + "'");
        }
        try {
            return new Period(YearMonth.parse(parts[0].strip()), YearMonth.parse(parts[1].strip()));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("period must be YYYY-MM:YYYY-MM but was '" + text + "'", e);
        }
    }

    public LocalDate firstDay() {
        return start.atDay(1);
    }

    public LocalDate lastDay() {
        return end.atEndOfMonth();
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(firstDay()) && !date.isAfter(lastDay());
    }

    @Override
    public String toString() {
        return start + SEPARATOR + end;
    }
}

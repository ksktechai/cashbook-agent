package nz.co.ksktech.cashbook.synthetic.pdf;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import nz.co.ksktech.cashbook.synthetic.Cents;

/** Date and amount formats used by the layouts. */
final class Formats {

    static final DateTimeFormatter DAY_MON_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);
    static final DateTimeFormatter DAY_MON = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    static final DateTimeFormatter NUMERIC = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH);
    static final DateTimeFormatter NUMERIC_SHORT = DateTimeFormatter.ofPattern("dd/MM/yy", Locale.ENGLISH);
    static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private Formats() {}

    /** "$1,234.56". */
    static String dollars(long cents) {
        return "$" + Cents.format(cents);
    }

    /** "$1,234.56" or "- $1,234.56", as in the Westpac-style exports. */
    static String signedSpaced(long cents) {
        return cents < 0 ? "- " + dollars(cents) : dollars(cents);
    }

    static String date(LocalDate date, DateTimeFormatter format) {
        return date.format(format);
    }
}

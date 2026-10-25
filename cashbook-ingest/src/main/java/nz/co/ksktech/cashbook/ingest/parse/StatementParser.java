package nz.co.ksktech.cashbook.ingest.parse;

import java.util.List;
import nz.co.ksktech.cashbook.core.StatementSummary;
import nz.co.ksktech.cashbook.core.Transaction;
import nz.co.ksktech.cashbook.ingest.IngestOptions;
import nz.co.ksktech.cashbook.ingest.highlight.Highlights;
import nz.co.ksktech.cashbook.ingest.text.PageText;

/** Parses one statement layout. Detection uses the page structure, never the institution's name. */
public interface StatementParser {

    /** The layout id written to the output, e.g. "TRANSACTION_SEARCH_ACCOUNT". */
    String layout();

    boolean matches(List<PageText> pages);

    Parsed parse(String file, List<PageText> pages, Highlights highlights, IngestOptions options);

    /** A parsed statement: what it says about itself, and its lines in statement order. */
    record Parsed(StatementSummary summary, List<Transaction> transactions) {

        public Parsed {
            transactions = List.copyOf(transactions);
        }
    }

    /** True when the first page has a line containing every fragment (in any lines). */
    static boolean firstPageHas(List<PageText> pages, String... fragments) {
        if (pages.isEmpty()) {
            return false;
        }
        PageText first = pages.getFirst();
        for (String f : fragments) {
            if (first.lines().stream().noneMatch(l -> l.text().contains(f))) {
                return false;
            }
        }
        return true;
    }
}

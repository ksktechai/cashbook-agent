package nz.co.ksktech.cashbook.ingest.parse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import nz.co.ksktech.cashbook.core.AccountType;
import nz.co.ksktech.cashbook.core.ForeignAmount;
import nz.co.ksktech.cashbook.core.Money;
import nz.co.ksktech.cashbook.core.SourceRef;
import nz.co.ksktech.cashbook.core.Transaction;
import nz.co.ksktech.cashbook.ingest.highlight.Highlights;
import nz.co.ksktech.cashbook.ingest.text.TextLine;

/** Collects the lines and fields of one transaction while a parser walks a page. */
final class RecordBuilder {

    final List<TextLine> lines = new ArrayList<>();
    final List<String> descriptionParts = new ArrayList<>();
    LocalDate date;
    LocalDate processed;
    BigDecimal amount;
    ForeignAmount foreign;
    BigDecimal fee = Money.ZERO;

    RecordBuilder(TextLine first) {
        lines.add(first);
    }

    void add(TextLine line) {
        lines.add(line);
    }

    float lastBaseline() {
        return lines.stream().map(TextLine::baseline).max(Float::compare).orElseThrow();
    }

    Transaction build(String file, String institution, AccountType type, Highlights highlights) {
        List<TextLine> ordered = lines.stream().sorted(Comparator.comparingDouble(TextLine::baseline)).toList();
        int page = ordered.getFirst().page();
        String raw = ordered.stream().map(TextLine::text).collect(Collectors.joining(" "));
        boolean highlighted = ordered.stream().anyMatch(l -> highlights.highlighted(l.page(), l.box(),
                l.segments().stream().map(TextLine.Segment::box).toList()));
        String description = String.join(" ", descriptionParts).replaceAll("\\s+", " ").strip();
        SourceRef source = new SourceRef(file, page, TextLine.union(ordered.stream().map(TextLine::box).toList()), raw);
        return new Transaction(source, institution, type, date, processed == null ? date : processed, description,
                amount, foreign, fee, highlighted);
    }
}

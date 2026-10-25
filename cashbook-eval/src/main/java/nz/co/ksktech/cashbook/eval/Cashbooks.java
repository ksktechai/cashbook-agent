package nz.co.ksktech.cashbook.eval;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import nz.co.ksktech.cashbook.core.CashbookLine;
import nz.co.ksktech.cashbook.core.GstTreatment;
import nz.co.ksktech.cashbook.core.Period;
import nz.co.ksktech.cashbook.core.SourceRef;
import nz.co.ksktech.cashbook.export.CashbookReader;
import nz.co.ksktech.cashbook.export.ExportInput;
import nz.co.ksktech.cashbook.export.ExportOptions;

/** Reads a cashbook to evaluate: a filled xlsx, a cashbook.jsonl, or a reconcile output folder. */
public final class Cashbooks {

    private static final String XLSX = ".xlsx";
    private static final BigDecimal GST_NUMERATOR = BigDecimal.valueOf(3);
    private static final BigDecimal GST_DENOMINATOR = BigDecimal.valueOf(23);
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private Cashbooks() {}

    public static List<EvalLine> read(Path path, Period period, ExportOptions options) {
        if (Files.isDirectory(path)) {
            return read(path.resolve(ExportInput.CASHBOOK), period, options);
        }
        if (path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(XLSX)) {
            return CashbookReader.read(path, period, options).stream()
                    .map(l -> new EvalLine(l.month(), l.date(), l.code(), l.detail(), l.amount(), l.gstCollected(),
                            l.gstClaimed(), "row " + l.row(), false))
                    .toList();
        }
        return fromLines(jsonl(path), l -> false);
    }

    /** Lines from reconciliation; GST is 3/23 of a STANDARD line's amount, as the template's formulas give. */
    public static List<EvalLine> fromLines(List<CashbookLine> lines, Predicate<CashbookLine> ruleDecided) {
        List<EvalLine> out = new ArrayList<>();
        for (CashbookLine l : lines) {
            BigDecimal gst = l.gst() == GstTreatment.STANDARD
                    ? l.amount().multiply(GST_NUMERATOR).divide(GST_DENOMINATOR, CashbookReader.GST_SCALE, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(CashbookReader.GST_SCALE);
            BigDecimal zero = BigDecimal.ZERO.setScale(CashbookReader.GST_SCALE);
            out.add(new EvalLine(l.month(), l.date(), l.code(), l.detail(), l.amount(), gst.signum() > 0 ? gst : zero,
                    gst.signum() < 0 ? gst : zero, source(l.sources()), ruleDecided.test(l)));
        }
        return out;
    }

    private static String source(List<SourceRef> sources) {
        if (sources.isEmpty()) {
            return "";
        }
        SourceRef first = sources.getFirst();
        String more = sources.size() > 1 ? " (+" + (sources.size() - 1) + " more)" : "";
        return first.file() + " p." + first.page() + more;
    }

    private static List<CashbookLine> jsonl(Path file) {
        try {
            List<CashbookLine> out = new ArrayList<>();
            for (String line : Files.readAllLines(file)) {
                if (!line.isBlank()) {
                    out.add(MAPPER.readValue(line, CashbookLine.class));
                }
            }
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }
}

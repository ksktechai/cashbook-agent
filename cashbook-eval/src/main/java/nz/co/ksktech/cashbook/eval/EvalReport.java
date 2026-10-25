package nz.co.ksktech.cashbook.eval;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import nz.co.ksktech.cashbook.core.Period;

/**
 * The evaluation report: one column per mode, side by side, then each mode's misclassified, missing and
 * extra lines. Written as report.md and report.json.
 */
public record EvalReport(LocalDate generated, String golden, String period, List<ModeResult> modes) {

    public static final String MARKDOWN = "report.md";
    public static final String JSON_FILE = "report.json";
    private static final int MAX_DETAIL_ROWS = 100;
    private static final String NONE = "–";
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    public EvalReport {
        modes = List.copyOf(modes);
    }

    public static EvalReport of(Path golden, Period period, List<ModeResult> modes) {
        return new EvalReport(LocalDate.now(), golden.toString(), period.start() + ":" + period.end(), modes);
    }

    /** Writes report.md and report.json into {@code dir}. */
    public void write(Path dir) {
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(MARKDOWN), markdown(), StandardCharsets.UTF_8);
            Files.writeString(dir.resolve(JSON_FILE), MAPPER.writeValueAsString(this) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write the report to " + dir, e);
        }
    }

    public String markdown() {
        StringBuilder b = new StringBuilder();
        b.append("# Cashbook evaluation\n\n");
        b.append("Golden: `").append(golden).append("` · Period: ").append(period).append(" · Generated: ")
                .append(generated).append("\n\n");
        b.append("Each run accepts every suggestion a rule or the model makes, as a person pressing \"y\" in review"
                + " would; a line nothing settled stays out. Lines match on month, code and amount (within $0.01).\n\n");
        summary(b);
        codes(b);
        for (ModeResult m : modes) {
            if (m.ran()) {
                details(b, m);
            }
        }
        return b.toString();
    }

    private void summary(StringBuilder b) {
        b.append("## Summary\n\n");
        header(b, "", modes.stream().map(ModeResult::mode).toList());
        row(b, "Classifier", ModeResult::classifier, false);
        row(b, "Golden lines", m -> Integer.toString(m.score().goldenLines()), true);
        row(b, "Lines produced", m -> Integer.toString(m.score().actualLines()), true);
        row(b, "**Matched**", m -> "**" + m.score().matched() + " (" + percent(m.score().accuracy()) + ")**", true);
        row(b, "Wrong code", m -> Integer.toString(m.score().wrongCode()), true);
        row(b, "Missing", m -> Integer.toString(m.score().missing()), true);
        row(b, "Extra", m -> Integer.toString(m.score().extra()), true);
        row(b, "Known-vendor lines correct", m -> m.score().ruleDecidedLines() == 0 ? NONE
                : m.score().ruleDecidedCorrect() + "/" + m.score().ruleDecidedLines() + " ("
                        + percent((double) m.score().ruleDecidedCorrect() / m.score().ruleDecidedLines()) + ")", true);
        row(b, "GST collected (golden " + golden(Score::goldenGstCollected) + ")",
                m -> money(m.score().actualGstCollected()) + " (" + signed(m.score().gstCollectedDiff()) + ")", true);
        row(b, "GST claimed (golden " + golden(Score::goldenGstClaimed) + ")",
                m -> money(m.score().actualGstClaimed()) + " (" + signed(m.score().gstClaimedDiff()) + ")", true);
        row(b, "GST within $1", m -> m.score().gstWithinTolerance() ? "yes" : "**no**", true);
        row(b, "Statements reconciled", m -> m.stats().statements() == null ? NONE
                : m.stats().statementsReconciled() + "/" + m.stats().statements(), true);
        row(b, "Export checks", m -> m.stats().exportFailures() == null ? NONE
                : m.stats().exportFailures().isEmpty() ? "all passed" : "**" + m.stats().exportFailures().size() + " failed**", true);
        row(b, "Review items", m -> number(m.stats().reviewItems()), true);
        row(b, "Lines left pending", m -> number(m.stats().pendingLines()), true);
        row(b, "LLM calls", m -> number(m.stats().llmCalls()), true);
        row(b, "LLM tokens in / out", m -> m.stats().llmInputTokens() == null ? NONE
                : String.format(Locale.ROOT, "%,d / %,d", m.stats().llmInputTokens(), m.stats().llmOutputTokens()), true);
        row(b, "LLM time", m -> seconds(m.stats().llmMillis()), true);
        row(b, "Cost", m -> m.stats().costNzd() == null ? NONE
                : String.format(Locale.ROOT, "$%.2f (local)", m.stats().costNzd()), true);
        row(b, "Run time", m -> seconds(m.stats().runMillis()), true);
        b.append('\n');
        modes.stream().filter(m -> !m.ran())
                .forEach(m -> b.append("**").append(m.mode()).append("** not run: ").append(m.notRun()).append("\n\n"));
    }

    private void codes(StringBuilder b) {
        List<ModeResult> ran = modes.stream().filter(ModeResult::ran).toList();
        if (ran.isEmpty()) {
            return;
        }
        b.append("## By code\n\n");
        b.append("P = precision (produced lines with the code that are right), R = recall (golden lines found),"
                + " $ diff = produced total minus golden total.\n\n");
        List<String> columns = new ArrayList<>(List.of("Golden lines", "Golden $"));
        ran.forEach(m -> columns.addAll(List.of(m.mode() + " P", m.mode() + " R", m.mode() + " $ diff")));
        header(b, "Code", columns);
        Map<String, Map<String, Score.CodeScore>> byCode = new TreeMap<>();
        ran.forEach(m -> m.score().codes().forEach(c -> byCode.computeIfAbsent(c.code(), k -> new TreeMap<>()).put(m.mode(), c)));
        byCode.forEach((code, scores) -> {
            Score.CodeScore any = scores.values().iterator().next();
            b.append("| ").append(code).append(" | ").append(any.goldenLines()).append(" | ").append(money(any.goldenAmount()));
            for (ModeResult m : ran) {
                Score.CodeScore c = scores.get(m.mode());
                b.append(" | ").append(c == null ? NONE : ratio(c.precision()))
                        .append(" | ").append(c == null ? NONE : ratio(c.recall()))
                        .append(" | ").append(c == null ? NONE : signed(c.difference()));
            }
            b.append(" |\n");
        });
        b.append('\n');
    }

    private static void details(StringBuilder b, ModeResult m) {
        LineMatcher.Matching x = m.matching();
        b.append("## ").append(m.mode()).append(": differences\n\n");
        b.append("### Wrong code (").append(x.wrongCode().size()).append(")\n\n");
        if (!x.wrongCode().isEmpty()) {
            b.append("| Month | Amount | Golden | Produced | Detail | Source |\n|---|---:|---|---|---|---|\n");
            limit(x.wrongCode(), b, p -> cells(p.golden().month(), money(p.golden().amount()), p.golden().code(),
                    p.actual().code(), p.actual().detail(), p.actual().source()));
        }
        b.append("### Missing (").append(x.missing().size()).append(")\n\n");
        if (!x.missing().isEmpty()) {
            b.append("| Month | Code | Amount | Detail |\n|---|---|---:|---|\n");
            limit(x.missing(), b, l -> cells(l.month(), l.code(), money(l.amount()), l.detail()));
        }
        b.append("### Extra (").append(x.extra().size()).append(")\n\n");
        if (!x.extra().isEmpty()) {
            b.append("| Month | Code | Amount | Detail | Source |\n|---|---|---:|---|---|\n");
            limit(x.extra(), b, l -> cells(l.month(), l.code(), money(l.amount()), l.detail(), l.source()));
        }
        list(b, "Statements that do not reconcile", m.stats().reconcileProblems());
        list(b, "Export checks that failed", m.stats().exportFailures());
    }

    private static void list(StringBuilder b, String title, List<String> items) {
        if (items != null && !items.isEmpty()) {
            b.append("### ").append(title).append("\n\n");
            items.forEach(i -> b.append("- ").append(i).append('\n'));
            b.append('\n');
        }
    }

    private static <T> void limit(List<T> items, StringBuilder b, Function<T, String> row) {
        items.stream().limit(MAX_DETAIL_ROWS).forEach(i -> b.append(row.apply(i)));
        if (items.size() > MAX_DETAIL_ROWS) {
            b.append("\n… and ").append(items.size() - MAX_DETAIL_ROWS).append(" more (see ").append(JSON_FILE).append(")\n");
        }
        b.append('\n');
    }

    private static String cells(Object... values) {
        StringBuilder b = new StringBuilder("|");
        for (Object v : values) {
            b.append(' ').append(String.valueOf(v).replace("|", "\\|")).append(" |");
        }
        return b.append('\n').toString();
    }

    private static void header(StringBuilder b, String first, List<String> columns) {
        b.append(first.isEmpty() ? "| " : "| " + first + " ");
        columns.forEach(c -> b.append("| ").append(c).append(' '));
        b.append("|\n|---");
        columns.forEach(c -> b.append("|---:"));
        b.append("|\n");
    }

    private void row(StringBuilder b, String label, Function<ModeResult, String> value, boolean needsRun) {
        b.append("| ").append(label);
        for (ModeResult m : modes) {
            b.append(" | ").append(needsRun && !m.ran() ? NONE : value.apply(m));
        }
        b.append(" |\n");
    }

    private String golden(Function<Score, BigDecimal> value) {
        return modes.stream().filter(ModeResult::ran).findFirst().map(m -> money(value.apply(m.score()))).orElse(NONE);
    }

    private static String number(Number n) {
        return n == null ? NONE : String.format(Locale.ROOT, "%,d", n.longValue());
    }

    private static String seconds(Long millis) {
        return millis == null ? NONE : String.format(Locale.ROOT, "%.1f s", millis / 1000.0);
    }

    private static String percent(double v) {
        return String.format(Locale.ROOT, "%.1f%%", v * 100);
    }

    private static String ratio(Double v) {
        return v == null ? NONE : percent(v);
    }

    private static String money(BigDecimal v) {
        return (v.signum() < 0 ? "−" : "") + String.format(Locale.ROOT, "$%,.2f", v.abs());
    }

    private static String signed(BigDecimal v) {
        return (v.signum() >= 0 ? "+" : "−") + String.format(Locale.ROOT, "%,.2f", v.abs());
    }
}

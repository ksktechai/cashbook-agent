package nz.co.ksktech.cashbook.eval;

import java.nio.file.Path;
import java.time.Duration;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nz.co.ksktech.cashbook.classify.ClassificationService;
import nz.co.ksktech.cashbook.classify.ClassifiedLine;
import nz.co.ksktech.cashbook.classify.ClassifyStore;
import nz.co.ksktech.cashbook.classify.ReconcileBridge;
import nz.co.ksktech.cashbook.classify.ReconcileStore;
import nz.co.ksktech.cashbook.classify.llm.Classifier;
import nz.co.ksktech.cashbook.core.CashbookLine;
import nz.co.ksktech.cashbook.core.ClassificationMethod;
import nz.co.ksktech.cashbook.core.Period;
import nz.co.ksktech.cashbook.core.SourceRef;
import nz.co.ksktech.cashbook.core.reconcile.ReconcileOptions;
import nz.co.ksktech.cashbook.core.reconcile.Reconciler;
import nz.co.ksktech.cashbook.core.rules.Rules;
import nz.co.ksktech.cashbook.export.CashbookExporter;
import nz.co.ksktech.cashbook.export.ExportInput;
import nz.co.ksktech.cashbook.export.ExportOptions;
import nz.co.ksktech.cashbook.ingest.IngestOptions;
import nz.co.ksktech.cashbook.ingest.IngestWriter;
import nz.co.ksktech.cashbook.ingest.Ingestor;

/**
 * Runs the pipeline once for the evaluation, as if a person accepted every suggestion: a rule's or the
 * model's proposal is taken, a line nothing settled stays out (pending), and flags keep their line. Writes
 * every intermediate file to {@code work} (never to a real run's folders) and scores the result.
 */
public final class EvalRunner {

    public static final String INGEST = "ingest";
    public static final String CLASSIFY = "classify";
    public static final String RECONCILE = "reconcile";
    public static final String CASHBOOK = "cashbook.xlsx";

    /** Everything a run needs besides its input folder and classifier. */
    public record Setup(IngestOptions ingest, Rules rules, double threshold, boolean reviewAll, ReconcileOptions reconcile,
            ExportOptions export, Path template) {}

    private EvalRunner() {}

    /** @param classifier the model, or null for rules only */
    public static ModeResult run(String mode, Path in, Classifier classifier, Setup setup, Path work, List<EvalLine> golden) {
        long start = System.nanoTime();
        Ingestor.Result ingested = new Ingestor(setup.ingest()).ingest(in);
        IngestWriter.write(ingested, work.resolve(INGEST));

        ClassificationService.Result classes = new ClassificationService(setup.rules(), classifier, setup.threshold(),
                setup.reviewAll()).classify(ingested.transactions());
        ClassifyStore.write(work.resolve(CLASSIFY), classes.lines(), classes.review(), classes.usage());
        List<ClassifiedLine> accepted = classes.lines().stream().map(EvalRunner::accept).toList();

        Map<String, String> names = new HashMap<>();
        setup.rules().codes().forEach((k, v) -> names.put(k, v.name()));
        Reconciler.Result r = new Reconciler(setup.reconcile(), names)
                .reconcile(ReconcileBridge.toClassified(accepted), ingested.invoices(), Map.of());
        ReconcileStore.write(work.resolve(RECONCILE), r);

        CashbookExporter.Report export = CashbookExporter.export(setup.template(), new ExportInput(r.lines(), r.items(),
                r.splits()), setup.reconcile().period(), setup.export(), work.resolve(CASHBOOK));
        long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();

        List<EvalLine> actual = Cashbooks.fromLines(r.lines(), ruleDecided(accepted, setup.reconcile().monthlyCodes()));
        LineMatcher.Matching matching = LineMatcher.match(golden, actual);
        List<String> problems = new ArrayList<>();
        ingested.statements().forEach(s -> s.reconciliation().problems().forEach(p -> problems.add(s.summary().file() + ": " + p)));
        Classifier.Usage usage = classes.usage();
        RunStats stats = new RunStats(classes.review().size() + r.flags().size(), r.unreviewed().size(), usage.calls(),
                usage.inputTokens(), usage.outputTokens(), usage.millis(), RunStats.LOCAL_COST, millis,
                ingested.statements().size(),
                (int) ingested.statements().stream().filter(s -> s.reconciliation().reconciled()).count(), problems,
                export.failures());
        return new ModeResult(mode, classifier == null ? "rules only" : classifier.name(), null,
                Score.of(golden, actual, matching), stats, matching);
    }

    /**
     * Scores a finished cashbook (xlsx, cashbook.jsonl or reconcile folder). Run figures come from {@code work}
     * (a {@code run} command's work folder) when given and present.
     */
    public static ModeResult compare(Path actual, Period period, ExportOptions options, List<EvalLine> golden, Path work) {
        List<EvalLine> lines = Cashbooks.read(actual, period, options);
        LineMatcher.Matching matching = LineMatcher.match(golden, lines);
        return new ModeResult("actual", actual.getFileName().toString(), null, Score.of(golden, lines, matching),
                WorkFolder.stats(work), matching);
    }

    /** What a person accepting every proposal would leave: only lines nothing settled stay under review. */
    static ClassifiedLine accept(ClassifiedLine l) {
        if (l.review().isEmpty() || ClassificationService.UNKNOWN.equals(l.classification().decidedBy())) {
            return l;
        }
        return new ClassifiedLine(l.id(), l.transaction(), l.classification(), l.special(), l.aggregate(),
                l.businessAmount(), List.of());
    }

    /**
     * A line is a known vendor's when a rule settled every transaction behind it. A line summed per month
     * (or from several transactions) also needs its month to have no pending line, which might have belonged
     * in the sum.
     */
    static java.util.function.Predicate<CashbookLine> ruleDecided(List<ClassifiedLine> lines, Set<String> monthlyCodes) {
        Map<SourceRef, ClassifiedLine> bySource = new HashMap<>();
        Set<YearMonth> pendingMonths = new HashSet<>();
        for (ClassifiedLine l : lines) {
            bySource.put(l.transaction().source(), l);
            if (!l.review().isEmpty()) {
                pendingMonths.add(YearMonth.from(l.transaction().transactionDate()));
            }
        }
        return line -> !line.sources().isEmpty()
                && line.sources().stream().allMatch(s -> {
                    ClassifiedLine c = bySource.get(s);
                    return c != null && c.review().isEmpty() && c.classification().code() != null
                            && c.classification().method() == ClassificationMethod.RULE;
                })
                && (!summed(line, bySource, monthlyCodes) || !pendingMonths.contains(line.month()));
    }

    private static boolean summed(CashbookLine line, Map<SourceRef, ClassifiedLine> bySource, Set<String> monthlyCodes) {
        return line.sources().size() > 1 || monthlyCodes.contains(line.code())
                || line.sources().stream().anyMatch(s -> bySource.get(s).aggregate() != null);
    }
}

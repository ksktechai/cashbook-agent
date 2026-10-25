package nz.co.ksktech.cashbook.app;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import nz.co.ksktech.cashbook.classify.ClassifiedLine;
import nz.co.ksktech.cashbook.classify.ClassifyStore;
import nz.co.ksktech.cashbook.classify.ReconcileBridge;
import nz.co.ksktech.cashbook.classify.ReconcileStore;
import nz.co.ksktech.cashbook.classify.ReviewEntry;
import nz.co.ksktech.cashbook.classify.RulesLoader;
import nz.co.ksktech.cashbook.core.Period;
import nz.co.ksktech.cashbook.core.reconcile.ReconcileOptions;
import nz.co.ksktech.cashbook.core.reconcile.Reconciler;
import nz.co.ksktech.cashbook.core.rules.Rules;
import nz.co.ksktech.cashbook.ingest.IngestReader;
import org.jboss.logging.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * {@code cashbook-agent reconcile}: turns reviewed classifications into cashbook lines (utility splits, FX,
 * mixed charges, monthly sums), adds anything to confirm (cut-off, high value, double count) to the review
 * queue, and exits 1 while lines are unreviewed or flags unanswered.
 */
@Command(name = "reconcile", mixinStandardHelpOptions = true,
        description = "Build cashbook lines from reviewed classifications; flags go to the review queue.")
public class ReconcileCommand implements Callable<Integer> {

    private static final Logger LOG = Logger.getLogger(ReconcileCommand.class);

    @Option(names = "--classified", required = true, description = "The classify output folder (review answers included).")
    Path classified;

    @Option(names = "--ingest", required = true, description = "The ingest output folder (for utility invoices).")
    Path ingest;

    @Option(names = "--period", required = true, converter = RunCommand.PeriodConverter.class,
            description = "GST period as YYYY-MM:YYYY-MM.")
    Period period;

    @Option(names = "--out", required = true, description = "Folder for cashbook.jsonl and the detail files.")
    Path out;

    @Option(names = "--rules", description = "Rules YAML, for code names (default: cashbook.rules.file).")
    Path rules;

    private final CashbookConfig config;

    ReconcileCommand(CashbookConfig config) {
        this.config = config;
    }

    @Override
    public Integer call() {
        try (var _ = RunContext.start()) {
            Rules loaded = RulesLoader.load(rules != null ? rules : config.rules().file());
            Map<String, String> names = new HashMap<>();
            loaded.codes().forEach((k, v) -> names.put(k, v.name()));
            CashbookConfig.Reconcile r = config.reconcile();
            ReconcileOptions options = new ReconcileOptions(period, r.capitaliseThresholdExclGst(), r.capitaliseCodes(),
                    r.monthlyCodes(), r.sections(), r.repaymentWindowDays());
            List<ClassifiedLine> lines = ClassifyStore.readLines(classified);
            List<ReviewEntry> queue = ClassifyStore.readReview(classified);
            Reconciler.Result result = Stages.run("reconcile", () -> new Reconciler(options, names).reconcile(
                    ReconcileBridge.toClassified(lines), IngestReader.invoices(ingest), ReconcileBridge.decisions(queue)));
            Stages.run("write", () -> {
                ReconcileStore.write(out, result);
                ClassifyStore.writeLines(classified, lines, ReconcileBridge.withFlags(queue, result.flags(), lines));
                return out;
            });
            LOG.infof("cashbookLines=%d splits=%d sectionItems=%d openFlags=%d unreviewed=%d", result.lines().size(),
                    result.splits().size(), result.items().size(), result.flags().size(), result.unreviewed().size());
            result.notes().forEach(n -> LOG.infof("note: %s", n));
            if (!result.flags().isEmpty() || !result.unreviewed().isEmpty()) {
                LOG.warnf("cashbook incomplete: answer the review queue (cashbook-agent review --dir %s) and run reconcile again",
                        classified);
                return CommandLine.ExitCode.SOFTWARE;
            }
            return CommandLine.ExitCode.OK;
        }
    }
}

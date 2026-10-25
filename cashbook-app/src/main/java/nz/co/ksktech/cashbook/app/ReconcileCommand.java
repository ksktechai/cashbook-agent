package nz.co.ksktech.cashbook.app;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import nz.co.ksktech.cashbook.core.Period;
import nz.co.ksktech.cashbook.core.reconcile.Reconciler;
import org.jboss.logging.Logger;
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

    private final PipelineSteps steps;

    ReconcileCommand(PipelineSteps steps) {
        this.steps = steps;
    }

    @Override
    public Integer call() {
        try (var _ = RunContext.start()) {
            Reconciler.Result result = steps.reconcile(classified, ingest, period, rules, out);
            boolean complete = result.flags().isEmpty() && result.unreviewed().isEmpty();
            if (!complete) {
                LOG.warnf("cashbook incomplete: answer the review queue (cashbook-agent review --dir %s) and run reconcile again",
                        classified);
            }
            return PipelineSteps.exitCode(complete);
        }
    }
}

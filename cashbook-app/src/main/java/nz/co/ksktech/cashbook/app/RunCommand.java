package nz.co.ksktech.cashbook.app;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import nz.co.ksktech.cashbook.classify.ClassificationService;
import nz.co.ksktech.cashbook.core.Period;
import nz.co.ksktech.cashbook.core.reconcile.Reconciler;
import nz.co.ksktech.cashbook.export.CashbookExporter;
import org.jboss.logging.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Option;

/**
 * {@code cashbook-agent run}: the whole pipeline for one GST period, in a work folder next to the output.
 * It stops (exit 1) when the review queue has open items; answer them with {@code review} and run again:
 * earlier answers are kept, so each run picks up where the last one stopped.
 */
@Command(name = "run", mixinStandardHelpOptions = true,
        description = "Ingest, classify, reconcile and export one GST period (stops when review is needed).")
public class RunCommand implements Callable<Integer> {

    private static final Logger LOG = Logger.getLogger(RunCommand.class);
    private static final String WORK_SUFFIX = ".work";

    @Option(names = "--period", required = true, converter = PeriodConverter.class,
            description = "GST period as YYYY-MM:YYYY-MM, e.g. 2026-10:2027-03.")
    Period period;

    @Option(names = "--in", required = true, description = "Folder of statements and invoices.")
    Path in;

    @Option(names = "--template", required = true, description = "Accountant's xlsx template.")
    Path template;

    @Option(names = "--out", required = true, description = "Where to write the filled cashbook xlsx.")
    Path out;

    @Option(names = "--rules", description = "Rules YAML (default: cashbook.rules.file).")
    Path rules;

    @Option(names = "--llm", description = "none (rules only) or ollama (default: cashbook.llm.provider).")
    String llm;

    @Option(names = "--work", description = "Work folder for the intermediate files (default: <out>.work).")
    Path work;

    private final PipelineSteps steps;

    RunCommand(PipelineSteps steps) {
        this.steps = steps;
    }

    @Override
    public Integer call() {
        try (var _ = RunContext.start()) {
            return Stages.run("run", this::pipeline);
        } catch (IllegalArgumentException e) {
            throw new CommandLine.ParameterException(new CommandLine(this), e.getMessage());
        }
    }

    private int pipeline() {
        Path dir = work != null ? work : out.resolveSibling(out.getFileName() + WORK_SUFFIX);
        Path ingested = dir.resolve("ingest");
        Path classified = dir.resolve("classify");
        Path reconciled = dir.resolve("reconcile");
        Path rulesFile = steps.rulesFile(rules);
        if (!Files.isDirectory(in) || !Files.isRegularFile(template)) {
            LOG.errorf("--in must be a folder and --template a file (in=%s, template=%s)", in, template);
            return CommandLine.ExitCode.SOFTWARE;
        }
        steps.ingest(in, ingested);
        ClassificationService.Result classes = steps.classify(ingested, classified, rules, llm, List.of(), null);
        long open = classes.review().stream().filter(e -> !e.answered()).count();
        if (open > 0) {
            return stop(open + " item(s) need review", classified, rulesFile);
        }
        Reconciler.Result result = steps.reconcile(classified, ingested, period, rules, reconciled);
        if (!result.flags().isEmpty() || !result.unreviewed().isEmpty()) {
            return stop(result.flags().size() + " flag(s) to confirm", classified, rulesFile);
        }
        CashbookExporter.Report report = steps.export(reconciled, template, period, out);
        if (!report.failures().isEmpty()) {
            LOG.errorf("%s written, but %d check(s) failed; see the messages above", out, report.failures().size());
            return CommandLine.ExitCode.SOFTWARE;
        }
        LOG.infof("cashbook written to %s; every check passed", out);
        return CommandLine.ExitCode.OK;
    }

    private static int stop(String why, Path classified, Path rulesFile) {
        LOG.warnf("%s: run `cashbook-agent review --dir %s --rules %s`, then run again", why, classified, rulesFile);
        return CommandLine.ExitCode.SOFTWARE;
    }

    static class PeriodConverter implements ITypeConverter<Period> {
        @Override
        public Period convert(String value) {
            try {
                return Period.parse(value);
            } catch (IllegalArgumentException e) {
                throw new CommandLine.TypeConversionException(e.getMessage());
            }
        }
    }
}

package nz.co.ksktech.cashbook.app;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import nz.co.ksktech.cashbook.core.Period;
import nz.co.ksktech.cashbook.export.CashbookExporter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * {@code cashbook-agent export}: fills a copy of the accountant's template with the reconciled cashbook and
 * checks it (row checks, cash position, section totals). Exits 1 if any check fails; the file is still
 * written so it can be inspected.
 */
@Command(name = "export", mixinStandardHelpOptions = true,
        description = "Fill a copy of the template with the reconciled cashbook and check that it balances.")
public class ExportCommand implements Callable<Integer> {

    @Option(names = "--reconciled", required = true, description = "The reconcile output folder.")
    Path reconciled;

    @Option(names = "--template", required = true, description = "The accountant's xlsx template (not modified).")
    Path template;

    @Option(names = "--period", required = true, converter = RunCommand.PeriodConverter.class,
            description = "GST period as YYYY-MM:YYYY-MM.")
    Period period;

    @Option(names = "--out", required = true, description = "The filled xlsx to write.")
    Path out;

    private final PipelineSteps steps;

    ExportCommand(PipelineSteps steps) {
        this.steps = steps;
    }

    @Override
    public Integer call() {
        try (var _ = RunContext.start()) {
            CashbookExporter.Report report = steps.export(reconciled, template, period, out);
            return PipelineSteps.exitCode(report.failures().isEmpty());
        }
    }
}

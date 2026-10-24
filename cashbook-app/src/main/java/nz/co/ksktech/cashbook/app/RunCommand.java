package nz.co.ksktech.cashbook.app;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import nz.co.ksktech.cashbook.core.Period;
import org.jboss.logging.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Option;

/** {@code cashbook-agent run}: the full pipeline for one GST period. Stages arrive in M2–M5. */
@Command(name = "run", mixinStandardHelpOptions = true, description = "Ingest, classify, reconcile and export one GST period.")
public class RunCommand implements Callable<Integer> {

    private static final Logger LOG = Logger.getLogger(RunCommand.class);

    @Option(names = "--period", required = true, converter = PeriodConverter.class,
            description = "GST period as YYYY-MM:YYYY-MM, e.g. 2026-10:2027-03.")
    Period period;

    @Option(names = "--in", required = true, description = "Folder of statements and invoices.")
    Path in;

    @Option(names = "--template", required = true, description = "Accountant's xlsx template.")
    Path template;

    @Option(names = "--out", required = true, description = "Where to write the filled cashbook xlsx.")
    Path out;

    private final CashbookConfig config;

    RunCommand(CashbookConfig config) {
        this.config = config;
    }

    @Override
    public Integer call() {
        try (var _ = RunContext.start()) {
            return Stages.run("run", () -> {
                LOG.infof("period=%s in=%s template=%s out=%s rules=%s reviewThreshold=%s",
                        period, in, template, out, config.rules().file(), config.review().threshold());
                LOG.error("pipeline stages are not implemented yet (M2-M5)");
                return CommandLine.ExitCode.SOFTWARE;
            });
        }
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

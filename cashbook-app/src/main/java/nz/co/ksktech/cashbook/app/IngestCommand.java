package nz.co.ksktech.cashbook.app;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import nz.co.ksktech.cashbook.ingest.IngestOptions;
import nz.co.ksktech.cashbook.ingest.IngestWriter;
import nz.co.ksktech.cashbook.ingest.Ingestor;
import org.jboss.logging.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * {@code cashbook-agent ingest}: reads every statement, invoice and levy notice under a folder and writes
 * transactions.jsonl, statements.jsonl and documents.jsonl. Exits 1 if any file could not be read or any
 * statement does not reconcile (the outputs are still written).
 */
@Command(name = "ingest", mixinStandardHelpOptions = true,
        description = "Extract transactions, balances and supplier documents from PDFs into JSONL.")
public class IngestCommand implements Callable<Integer> {

    private static final Logger LOG = Logger.getLogger(IngestCommand.class);
    private static final String JNA_LIBRARY_PATH = "jna.library.path";

    @Option(names = "--in", required = true, description = "Folder of statements and documents (searched recursively).")
    Path in;

    @Option(names = "--out", required = true, description = "Folder for the JSONL output.")
    Path out;

    private final CashbookConfig config;

    IngestCommand(CashbookConfig config) {
        this.config = config;
    }

    @Override
    public Integer call() {
        try (var _ = RunContext.start()) {
            CashbookConfig.Ingest ingest = config.ingest();
            if (System.getProperty(JNA_LIBRARY_PATH) == null) {
                System.setProperty(JNA_LIBRARY_PATH, ingest.ocr().libraryPath());
            }
            IngestOptions options = new IngestOptions(ingest.highlightThreshold(),
                    Pattern.compile(ingest.businessAccountPattern()), ingest.ocr().enabled(),
                    ingest.ocr().tessdata().orElse(""), ingest.ocr().dpi());
            Ingestor.Result result = Stages.run("ingest", () -> new Ingestor(options).ingest(in));
            Stages.run("write", () -> {
                IngestWriter.write(result, out);
                return out;
            });
            LOG.infof("transactions=%d statements=%d invoices=%d levies=%d skipped=%d", result.transactions().size(),
                    result.statements().size(), result.invoices().size(), result.levies().size(),
                    result.skipped().size());
            result.skipped().forEach(s -> LOG.warnf("skipped %s", s));
            result.statements().stream().filter(s -> !s.reconciliation().reconciled())
                    .forEach(s -> LOG.errorf("%s does not reconcile: %s", s.summary().file(), s.reconciliation().problems()));
            return result.allReconciled() && result.skipped().isEmpty() ? CommandLine.ExitCode.OK
                    : CommandLine.ExitCode.SOFTWARE;
        }
    }
}

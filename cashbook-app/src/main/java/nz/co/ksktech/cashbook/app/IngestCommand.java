package nz.co.ksktech.cashbook.app;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import nz.co.ksktech.cashbook.ingest.Ingestor;
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

    @Option(names = "--in", required = true, description = "Folder of statements and documents (searched recursively).")
    Path in;

    @Option(names = "--out", required = true, description = "Folder for the JSONL output.")
    Path out;

    private final PipelineSteps steps;

    IngestCommand(PipelineSteps steps) {
        this.steps = steps;
    }

    @Override
    public Integer call() {
        try (var _ = RunContext.start()) {
            Ingestor.Result result = steps.ingest(in, out);
            return PipelineSteps.exitCode(result.allReconciled() && result.skipped().isEmpty());
        }
    }
}

package nz.co.ksktech.cashbook.app;

import jakarta.enterprise.context.ApplicationScoped;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import nz.co.ksktech.cashbook.classify.ClassificationService;
import nz.co.ksktech.cashbook.classify.ClassifiedLine;
import nz.co.ksktech.cashbook.classify.ClassifyStore;
import nz.co.ksktech.cashbook.classify.ReconcileBridge;
import nz.co.ksktech.cashbook.classify.ReconcileStore;
import nz.co.ksktech.cashbook.classify.ReviewEntry;
import nz.co.ksktech.cashbook.classify.ReviewMerge;
import nz.co.ksktech.cashbook.classify.RulesLoader;
import nz.co.ksktech.cashbook.classify.llm.Classifier;
import nz.co.ksktech.cashbook.classify.llm.OllamaClassifier;
import nz.co.ksktech.cashbook.classify.llm.OllamaLocator;
import nz.co.ksktech.cashbook.core.Period;
import nz.co.ksktech.cashbook.core.Transaction;
import nz.co.ksktech.cashbook.core.reconcile.ReconcileOptions;
import nz.co.ksktech.cashbook.core.reconcile.Reconciler;
import nz.co.ksktech.cashbook.core.rules.Rules;
import nz.co.ksktech.cashbook.export.CashbookExporter;
import nz.co.ksktech.cashbook.export.ExportInput;
import nz.co.ksktech.cashbook.export.ExportOptions;
import nz.co.ksktech.cashbook.ingest.IngestOptions;
import nz.co.ksktech.cashbook.ingest.IngestReader;
import nz.co.ksktech.cashbook.ingest.IngestWriter;
import nz.co.ksktech.cashbook.ingest.Ingestor;
import org.jboss.logging.Logger;
import picocli.CommandLine;

/** The pipeline steps, shared by the individual commands and by {@code run}. Each reads and writes files. */
@ApplicationScoped
public class PipelineSteps {

    private static final Logger LOG = Logger.getLogger(PipelineSteps.class);
    private static final String JNA_LIBRARY_PATH = "jna.library.path";
    private static final String OLLAMA = "ollama";
    private static final String NONE = "none";

    private final CashbookConfig config;

    PipelineSteps(CashbookConfig config) {
        this.config = config;
    }

    Path rulesFile(Path override) {
        return override != null ? override : config.rules().file();
    }

    Ingestor.Result ingest(Path in, Path out) {
        CashbookConfig.Ingest ingest = config.ingest();
        if (System.getProperty(JNA_LIBRARY_PATH) == null) {
            System.setProperty(JNA_LIBRARY_PATH, ingest.ocr().libraryPath());
        }
        IngestOptions options = new IngestOptions(ingest.highlightThreshold(), Pattern.compile(ingest.businessAccountPattern()),
                ingest.ocr().enabled(), ingest.ocr().tessdata().orElse(""), ingest.ocr().dpi());
        Ingestor.Result result = Stages.run("ingest", () -> new Ingestor(options).ingest(in));
        IngestWriter.write(result, out);
        LOG.infof("transactions=%d statements=%d invoices=%d levies=%d skipped=%d", result.transactions().size(),
                result.statements().size(), result.invoices().size(), result.levies().size(), result.skipped().size());
        result.skipped().forEach(s -> LOG.warnf("skipped %s", s));
        result.statements().stream().filter(s -> !s.reconciliation().reconciled())
                .forEach(s -> LOG.errorf("%s does not reconcile: %s", s.summary().file(), s.reconciliation().problems()));
        return result;
    }

    /** Classifies, keeping any review answers already in {@code out}. */
    ClassificationService.Result classify(Path ingestDir, Path out, Path rules, String llm, List<String> urls, String model) {
        Path file = rulesFile(rules);
        Rules loaded = Stages.run("rules", () -> RulesLoader.load(file));
        Classifier classifier = classifier(llm, urls, model);
        List<Transaction> transactions = ClassifyStore.readTransactions(ingestDir.resolve(IngestWriter.TRANSACTIONS));
        ClassificationService.Result fresh = Stages.run("classify", () -> new ClassificationService(loaded, classifier,
                config.review().threshold(), config.llm().reviewAll()).classify(transactions));
        ClassificationService.Result result = Files.exists(out.resolve(ClassifyStore.CLASSIFICATIONS))
                ? ReviewMerge.carryOver(fresh, ClassifyStore.readLines(out), ClassifyStore.readReview(out))
                : fresh;
        ClassifyStore.write(out, result.lines(), result.review(), result.usage());
        LOG.infof("rules=%s lines=%d review=%d open=%d llmCalls=%d llmTokens=%d/%d", file, result.lines().size(),
                result.review().size(), result.review().stream().filter(e -> !e.answered()).count(),
                result.usage().calls(), result.usage().inputTokens(), result.usage().outputTokens());
        return result;
    }

    Reconciler.Result reconcile(Path classified, Path ingestDir, Period period, Path rules, Path out) {
        Rules loaded = RulesLoader.load(rulesFile(rules));
        Map<String, String> names = new HashMap<>();
        loaded.codes().forEach((k, v) -> names.put(k, v.name()));
        CashbookConfig.Reconcile r = config.reconcile();
        ReconcileOptions options = new ReconcileOptions(period, r.capitaliseThresholdExclGst(), r.capitaliseCodes(),
                r.monthlyCodes(), r.sections(), r.repaymentWindowDays());
        List<ClassifiedLine> lines = ClassifyStore.readLines(classified);
        List<ReviewEntry> queue = ClassifyStore.readReview(classified);
        Reconciler.Result result = Stages.run("reconcile", () -> new Reconciler(options, names).reconcile(
                ReconcileBridge.toClassified(lines), IngestReader.invoices(ingestDir), ReconcileBridge.decisions(queue)));
        ReconcileStore.write(out, result);
        ClassifyStore.writeLines(classified, lines, ReconcileBridge.withFlags(queue, result.flags(), lines));
        LOG.infof("cashbookLines=%d splits=%d sectionItems=%d openFlags=%d unreviewed=%d", result.lines().size(),
                result.splits().size(), result.items().size(), result.flags().size(), result.unreviewed().size());
        result.notes().forEach(n -> LOG.infof("note: %s", n));
        return result;
    }

    CashbookExporter.Report export(Path reconciled, Path template, Period period, Path out) {
        CashbookExporter.Report report = Stages.run("export",
                () -> CashbookExporter.export(template, ExportInput.read(reconciled), period, ExportOptions.defaults(), out));
        report.notes().forEach(n -> LOG.infof("template: %s", n));
        report.failures().forEach(f -> LOG.errorf("check failed: %s", f));
        LOG.infof("out=%s insertedRows=%d checksFailed=%d", out, report.insertedRows(), report.failures().size());
        return report;
    }

    private Classifier classifier(String llm, List<String> urls, String model) {
        String provider = llm != null ? llm : config.llm().provider();
        if (NONE.equalsIgnoreCase(provider)) {
            return null;
        }
        if (!OLLAMA.equalsIgnoreCase(provider)) {
            throw new IllegalArgumentException("--llm must be none or ollama (only local open-source models are supported)");
        }
        CashbookConfig.Llm.Ollama o = config.llm().ollama();
        String modelName = model != null ? model : o.model();
        List<String> candidates = urls != null && !urls.isEmpty() ? urls : o.urls();
        String url = Stages.run("locate-ollama", () -> OllamaLocator.locate(candidates, modelName, o.probeTimeout()));
        LOG.infof("using Ollama at %s with model %s", url, modelName);
        return new OllamaClassifier(url, modelName, o.timeout());
    }

    static int exitCode(boolean complete) {
        return complete ? CommandLine.ExitCode.OK : CommandLine.ExitCode.SOFTWARE;
    }
}

package nz.co.ksktech.cashbook.app;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import nz.co.ksktech.cashbook.classify.ClassificationService;
import nz.co.ksktech.cashbook.classify.ClassifyStore;
import nz.co.ksktech.cashbook.classify.RulesLoader;
import nz.co.ksktech.cashbook.classify.llm.Classifier;
import nz.co.ksktech.cashbook.classify.llm.OllamaClassifier;
import nz.co.ksktech.cashbook.classify.llm.OllamaLocator;
import nz.co.ksktech.cashbook.core.Transaction;
import nz.co.ksktech.cashbook.core.rules.Rules;
import nz.co.ksktech.cashbook.ingest.IngestWriter;
import org.jboss.logging.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * {@code cashbook-agent classify}: rules first; with {@code --llm ollama}, a local model suggests codes for
 * lines no rule matches. Writes classifications.jsonl, review.jsonl and llm-usage.json.
 */
@Command(name = "classify", mixinStandardHelpOptions = true,
        description = "Classify ingested transactions with the rules (and optionally a local Ollama model).")
public class ClassifyCommand implements Callable<Integer> {

    private static final Logger LOG = Logger.getLogger(ClassifyCommand.class);
    private static final String OLLAMA = "ollama";
    private static final String NONE = "none";

    @Option(names = "--in", required = true, description = "Ingest output folder (with transactions.jsonl).")
    Path in;

    @Option(names = "--out", required = true, description = "Folder for classifications.jsonl and review.jsonl.")
    Path out;

    @Option(names = "--rules", description = "Rules YAML (default: cashbook.rules.file).")
    Path rules;

    @Option(names = "--llm", description = "none (rules only) or ollama (default: cashbook.llm.provider).")
    String llm;

    @Option(names = "--ollama-url", description = "Ollama URL to try; repeat for several (overrides config).")
    List<String> ollamaUrls;

    @Option(names = "--model", description = "Ollama model (default: cashbook.llm.ollama.model).")
    String model;

    private final CashbookConfig config;

    ClassifyCommand(CashbookConfig config) {
        this.config = config;
    }

    @Override
    public Integer call() {
        try (var _ = RunContext.start()) {
            Path rulesFile = rules != null ? rules : config.rules().file();
            Rules loaded = Stages.run("rules", () -> RulesLoader.load(rulesFile));
            Classifier classifier = classifier();
            List<Transaction> transactions = ClassifyStore.readTransactions(in.resolve(IngestWriter.TRANSACTIONS));
            ClassificationService.Result result = Stages.run("classify",
                    () -> new ClassificationService(loaded, classifier, config.review().threshold(), config.llm().reviewAll())
                            .classify(transactions));
            Stages.run("write", () -> {
                ClassifyStore.write(out, result.lines(), result.review(), result.usage());
                return out;
            });
            Map<String, Long> byMethod = result.lines().stream().collect(Collectors.groupingBy(
                    l -> l.classification().method().name(), java.util.TreeMap::new, Collectors.counting()));
            LOG.infof("rules=%s vendors=%d lines=%d byMethod=%s review=%d llmCalls=%d llmTokens=%d/%d llmMillis=%d",
                    rulesFile, loaded.vendors().size(), result.lines().size(), byMethod, result.review().size(),
                    result.usage().calls(), result.usage().inputTokens(), result.usage().outputTokens(),
                    result.usage().millis());
            return CommandLine.ExitCode.OK;
        }
    }

    private Classifier classifier() {
        String provider = llm != null ? llm : config.llm().provider();
        if (NONE.equalsIgnoreCase(provider)) {
            return null;
        }
        if (!OLLAMA.equalsIgnoreCase(provider)) {
            throw new CommandLine.ParameterException(new CommandLine(this),
                    "--llm must be none or ollama (only local open-source models are supported)");
        }
        CashbookConfig.Llm.Ollama o = config.llm().ollama();
        String modelName = model != null ? model : o.model();
        List<String> urls = ollamaUrls != null && !ollamaUrls.isEmpty() ? ollamaUrls : o.urls();
        String url = Stages.run("locate-ollama", () -> OllamaLocator.locate(urls, modelName, o.probeTimeout()));
        LOG.infof("using Ollama at %s with model %s", url, modelName);
        return new OllamaClassifier(url, modelName, o.timeout());
    }
}

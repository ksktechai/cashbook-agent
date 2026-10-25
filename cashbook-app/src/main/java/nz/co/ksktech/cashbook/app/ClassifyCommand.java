package nz.co.ksktech.cashbook.app;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * {@code cashbook-agent classify}: rules first; with {@code --llm ollama}, a local model suggests codes for
 * lines no rule matches. Writes classifications.jsonl, review.jsonl and llm-usage.json, keeping any review
 * answers already in the output folder.
 */
@Command(name = "classify", mixinStandardHelpOptions = true,
        description = "Classify ingested transactions with the rules (and optionally a local Ollama model).")
public class ClassifyCommand implements Callable<Integer> {

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

    private final PipelineSteps steps;

    ClassifyCommand(PipelineSteps steps) {
        this.steps = steps;
    }

    @Override
    public Integer call() {
        try (var _ = RunContext.start()) {
            steps.classify(in, out, rules, llm, ollamaUrls, model);
            return CommandLine.ExitCode.OK;
        } catch (IllegalArgumentException e) {
            throw new CommandLine.ParameterException(new CommandLine(this), e.getMessage());
        }
    }
}

package nz.co.ksktech.cashbook.app;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import nz.co.ksktech.cashbook.classify.llm.Classifier;
import nz.co.ksktech.cashbook.classify.llm.OllamaLocator;
import nz.co.ksktech.cashbook.core.Period;
import nz.co.ksktech.cashbook.eval.Cashbooks;
import nz.co.ksktech.cashbook.eval.EvalLine;
import nz.co.ksktech.cashbook.eval.EvalReport;
import nz.co.ksktech.cashbook.eval.EvalRunner;
import nz.co.ksktech.cashbook.eval.ModeResult;
import nz.co.ksktech.cashbook.export.ExportOptions;
import org.jboss.logging.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * {@code cashbook-agent eval}: scores cashbooks against the golden one and writes report.md and report.json.
 * Either compares a finished cashbook ({@code --actual}) or runs the pipeline once per mode ({@code --in},
 * {@code --template}), accepting every suggestion, so rules-only and rules-plus-LLM show side by side.
 */
@Command(name = "eval", mixinStandardHelpOptions = true,
        description = "Score a cashbook (or rules-only and rules+LLM runs) against the golden cashbook.")
public class EvalCommand implements Callable<Integer> {

    private static final Logger LOG = Logger.getLogger(EvalCommand.class);
    static final String RULES = "rules";
    static final String LLM = "llm";
    private static final String NONE = "none";
    private static final String OLLAMA = "ollama";
    private static final String WORK = "work";
    private static final String WORK_SUFFIX = ".work";
    private static final Path PRIVATE = Path.of("private");

    @Option(names = "--golden", required = true, description = "The hand-checked cashbook: xlsx, or cashbook.jsonl.")
    Path golden;

    @Option(names = "--period", required = true, converter = RunCommand.PeriodConverter.class,
            description = "GST period as YYYY-MM:YYYY-MM.")
    Period period;

    @Option(names = "--out", required = true,
            description = "Folder for report.md and report.json (for real data, somewhere under private/).")
    Path out;

    @Option(names = "--actual", description = "A finished cashbook to score (xlsx, cashbook.jsonl or reconcile folder).")
    Path actual;

    @Option(names = "--in", description = "Statements folder: run the pipeline once per mode.")
    Path in;

    @Option(names = "--template", description = "The accountant's template, for the runs.")
    Path template;

    @Option(names = "--rules", description = "Rules YAML (default: cashbook.rules.file).")
    Path rules;

    @Option(names = "--modes", split = ",", defaultValue = RULES + "," + LLM,
            description = "Runs to compare: rules, llm (default: both).")
    List<String> modes;

    @Option(names = "--ollama-url", description = "Ollama URL to try; repeat for several (overrides config).")
    List<String> ollamaUrls;

    @Option(names = "--model", description = "Ollama model (default: cashbook.llm.ollama.model).")
    String model;

    @Option(names = "--work", description = "Work folder: the runs' files (default: <out>/work), or a finished"
            + " cashbook's run folder (default: <actual>.work).")
    Path work;

    private final PipelineSteps steps;

    EvalCommand(PipelineSteps steps) {
        this.steps = steps;
    }

    @Override
    public Integer call() {
        if ((actual == null) == (in == null || template == null)) {
            throw usage("give either --actual, or --in and --template");
        }
        modes.stream().filter(m -> !RULES.equals(m) && !LLM.equals(m)).findFirst()
                .ifPresent(m -> { throw usage("--modes takes rules and/or llm, not " + m); });
        for (Path input : new Path[] {golden, actual, in}) {
            if (input != null && isPrivate(input) && !isPrivate(out)) {
                throw usage("--out must be under private/ when evaluating private data (" + input + ")");
            }
        }
        try (var _ = RunContext.start()) {
            return Stages.run("eval", this::evaluate);
        }
    }

    private int evaluate() {
        ExportOptions options = ExportOptions.defaults();
        List<EvalLine> goldenLines = Stages.run("read-golden", () -> Cashbooks.read(golden, period, options));
        List<ModeResult> results = new ArrayList<>();
        if (actual != null) {
            Path folder = work != null ? work : actual.resolveSibling(actual.getFileName() + WORK_SUFFIX);
            results.add(Stages.run("eval-actual", () -> EvalRunner.compare(actual, period, options, goldenLines, folder)));
        } else {
            EvalRunner.Setup setup = steps.evalSetup(period, rules, template);
            Path workDir = work != null ? work : out.resolve(WORK);
            for (String mode : modes) {
                results.add(Stages.run("eval-" + mode, () -> runMode(mode, setup, workDir.resolve(mode), goldenLines)));
            }
        }
        EvalReport.of(golden, period, results).write(out);
        for (ModeResult r : results) {
            if (r.ran()) {
                LOG.infof("mode=%s matched=%d/%d accuracy=%.3f knownVendor=%d/%d gstWithin1=%s review=%s llmCalls=%s",
                        r.mode(), r.score().matched(), r.score().goldenLines(), r.score().accuracy(),
                        r.score().ruleDecidedCorrect(), r.score().ruleDecidedLines(), r.score().gstWithinTolerance(),
                        r.stats().reviewItems(), r.stats().llmCalls());
            } else {
                LOG.warnf("mode=%s not run: %s", r.mode(), r.notRun());
            }
        }
        LOG.infof("report written to %s", out.resolve(EvalReport.MARKDOWN));
        return CommandLine.ExitCode.OK;
    }

    private ModeResult runMode(String mode, EvalRunner.Setup setup, Path dir, List<EvalLine> goldenLines) {
        Classifier classifier;
        switch (mode) {
            case RULES -> classifier = steps.classifier(NONE, null, null);
            case LLM -> {
                try {
                    classifier = steps.classifier(OLLAMA, ollamaUrls, model);
                } catch (OllamaLocator.UnavailableException e) {
                    return ModeResult.notRun(mode, steps.modelName(model), e.getMessage());
                }
            }
            default -> throw new IllegalStateException("unchecked mode " + mode);
        }
        return EvalRunner.run(mode, in, classifier, setup, dir, goldenLines);
    }

    private static boolean isPrivate(Path p) {
        return p.toAbsolutePath().normalize().startsWith(PRIVATE.toAbsolutePath().normalize());
    }

    private CommandLine.ParameterException usage(String message) {
        return new CommandLine.ParameterException(new CommandLine(this), message);
    }
}

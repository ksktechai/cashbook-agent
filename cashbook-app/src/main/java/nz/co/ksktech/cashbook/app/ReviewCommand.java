package nz.co.ksktech.cashbook.app;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import nz.co.ksktech.cashbook.classify.ReviewSession;
import nz.co.ksktech.cashbook.classify.RulesLoader;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** {@code cashbook-agent review}: answer the review queue interactively (stdin/stdout). */
@Command(name = "review", mixinStandardHelpOptions = true,
        description = "Walk the review queue: accept, exclude or choose a code; optionally save as a rule.")
public class ReviewCommand implements Callable<Integer> {

    @Option(names = "--dir", required = true, description = "The classify output folder.")
    Path dir;

    @Option(names = "--rules", description = "Rules YAML; learned rules go next to it (default: cashbook.rules.file).")
    Path rules;

    private final CashbookConfig config;

    ReviewCommand(CashbookConfig config) {
        this.config = config;
    }

    @Override
    public Integer call() {
        try (var _ = RunContext.start()) {
            Path rulesFile = rules != null ? rules : config.rules().file();
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
            new ReviewSession(dir, rulesFile, RulesLoader.load(rulesFile), in, out).run();
            return CommandLine.ExitCode.OK;
        }
    }
}

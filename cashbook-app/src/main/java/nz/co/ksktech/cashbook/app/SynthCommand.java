package nz.co.ksktech.cashbook.app;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import nz.co.ksktech.cashbook.synthetic.SyntheticGenerator;
import nz.co.ksktech.cashbook.synthetic.truth.Manifest;
import org.jboss.logging.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** {@code cashbook-agent synth}: writes a synthetic period with its truth and golden files. */
@Command(name = "synth", mixinStandardHelpOptions = true,
        description = "Generate a synthetic period: statements, invoices, golden cashbook and template.")
public class SynthCommand implements Callable<Integer> {

    private static final Logger LOG = Logger.getLogger(SynthCommand.class);

    @Option(names = "--seed", defaultValue = "42", description = "Random seed; the same seed gives identical files.")
    long seed;

    @Option(names = "--out", required = true, description = "Output folder, e.g. target/synth.")
    Path out;

    @Override
    public Integer call() {
        try (var _ = RunContext.start()) {
            Manifest manifest = Stages.run("synth", () -> SyntheticGenerator.generate(seed, out));
            LOG.infof("seed=%d files=%d out=%s", seed, manifest.files().size(), out);
            return CommandLine.ExitCode.OK;
        }
    }
}

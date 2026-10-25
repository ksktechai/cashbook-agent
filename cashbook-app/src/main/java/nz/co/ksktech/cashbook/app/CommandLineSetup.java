package nz.co.ksktech.cashbook.app;

import io.quarkus.picocli.runtime.PicocliCommandLineFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.jboss.logging.Logger;
import picocli.CommandLine;

/**
 * Makes unexpected errors end as one JSON log line and exit code 1, instead of picocli printing a plain-text
 * stack trace (stderr is JSONL only). The stage that failed has already logged the exception in full.
 */
@ApplicationScoped
public class CommandLineSetup {

    private static final Logger LOG = Logger.getLogger(CommandLineSetup.class);

    @Produces
    CommandLine commandLine(PicocliCommandLineFactory factory) {
        return factory.create().setExecutionExceptionHandler((e, cmd, parsed) -> {
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            LOG.errorf("%s failed: %s (%s)", cmd.getCommandName(), e.getMessage(), root.getMessage());
            return CommandLine.ExitCode.SOFTWARE;
        });
    }
}

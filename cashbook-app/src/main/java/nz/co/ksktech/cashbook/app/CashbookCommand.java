package nz.co.ksktech.cashbook.app;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import org.eclipse.microprofile.config.ConfigProvider;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;

/** Entry point: {@code cashbook-agent <command>}. */
@TopCommand
@Command(
        name = "cashbook-agent",
        mixinStandardHelpOptions = true,
        versionProvider = CashbookCommand.Version.class,
        description = "Turns statements and invoices into an accountant-ready GST cashbook.",
        subcommands = {RunCommand.class, IngestCommand.class, ClassifyCommand.class, ReviewCommand.class,
            ReconcileCommand.class, SynthCommand.class})
public class CashbookCommand {

    static class Version implements IVersionProvider {
        private static final String NAME_KEY = "quarkus.application.name";
        private static final String VERSION_KEY = "quarkus.application.version";

        @Override
        public String[] getVersion() {
            var config = ConfigProvider.getConfig();
            return new String[] {config.getValue(NAME_KEY, String.class) + " " + config.getValue(VERSION_KEY, String.class)};
        }
    }
}

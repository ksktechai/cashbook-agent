package nz.co.ksktech.cashbook.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Runs the packaged CLI as a separate process, so stderr holds exactly what a user would see. */
@QuarkusMainIntegrationTest
class JsonLoggingIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    // Framework start/stop lines fall outside a run; the launcher re-enables them to detect startup.
    private static final String APP_LOGGERS = "nz.co.ksktech.";

    @Test
    @Launch(value = {"run", "--period", "2026-10:2027-03", "--in", "in", "--template", "t.xlsx", "--out", "o.xlsx"},
            exitCode = 1)
    void runLogsJsonLinesWithOneCorrelationIdAndStageMarkers(LaunchResult result) throws Exception {
        List<String> lines = result.getErrorStream();
        assertThat(lines).isNotEmpty();

        List<JsonNode> records = lines.stream().map(JsonLoggingIT::parse).toList();
        List<JsonNode> appRecords = records.stream()
                .filter(r -> r.path("loggerName").asText().startsWith(APP_LOGGERS))
                .toList();
        List<String> correlationIds = appRecords.stream()
                .map(r -> r.path("mdc").path(RunContext.CORRELATION_ID).asText())
                .distinct()
                .toList();
        List<String> messages = appRecords.stream().map(r -> r.path("message").asText()).toList();

        assertThat(correlationIds).as("log lines: %s", lines).hasSize(1).first().asString().isNotBlank();
        assertThat(messages).anyMatch(m -> m.equals(">>> run")).anyMatch(m -> m.startsWith("<<< run durationMs="));
    }

    private static JsonNode parse(String line) {
        try {
            JsonNode node = JSON.readTree(line);
            if (node == null || !node.isObject()) {
                throw new AssertionError("not a JSON log line: " + line);
            }
            return node;
        } catch (Exception e) {
            throw new AssertionError("not a JSON log line: " + line, e);
        }
    }
}

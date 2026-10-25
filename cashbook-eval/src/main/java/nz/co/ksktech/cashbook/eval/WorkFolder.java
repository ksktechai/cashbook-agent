package nz.co.ksktech.cashbook.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import nz.co.ksktech.cashbook.classify.ClassifyStore;
import nz.co.ksktech.cashbook.ingest.IngestWriter;

/**
 * Run figures read back from a {@code run} command's work folder (ingest, classify), for comparing a
 * finished cashbook. Anything missing stays unknown.
 */
final class WorkFolder {

    private static final ObjectMapper JSON = new ObjectMapper();

    private WorkFolder() {}

    static RunStats stats(Path work) {
        if (work == null || !Files.isDirectory(work)) {
            return RunStats.unknown();
        }
        Integer statements = null;
        Integer reconciled = null;
        List<String> problems = new ArrayList<>();
        Path statementsFile = work.resolve(EvalRunner.INGEST).resolve(IngestWriter.STATEMENTS);
        if (Files.exists(statementsFile)) {
            List<JsonNode> nodes = jsonl(statementsFile);
            statements = nodes.size();
            int ok = 0;
            for (JsonNode n : nodes) {
                JsonNode p = n.path("reconciliation").path("problems");
                if (p.isEmpty()) {
                    ok++;
                }
                p.forEach(x -> problems.add(n.path("summary").path("file").asText() + ": " + x.asText()));
            }
            reconciled = ok;
        }
        Integer review = null;
        Integer open = null;
        Path reviewFile = work.resolve(EvalRunner.CLASSIFY).resolve(ClassifyStore.REVIEW);
        if (Files.exists(reviewFile)) {
            List<JsonNode> nodes = jsonl(reviewFile);
            review = nodes.size();
            open = (int) nodes.stream().filter(n -> n.path("item").path("answer").isNull()
                    || n.path("item").path("answer").isMissingNode()).count();
        }
        Integer calls = null;
        Long in = null;
        Long out = null;
        Long millis = null;
        Path usage = work.resolve(EvalRunner.CLASSIFY).resolve(ClassifyStore.USAGE);
        if (Files.exists(usage)) {
            JsonNode u = read(usage);
            calls = u.path("calls").asInt();
            in = u.path("inputTokens").asLong();
            out = u.path("outputTokens").asLong();
            millis = u.path("millis").asLong();
        }
        return new RunStats(review, open, calls, in, out, millis, calls == null ? null : RunStats.LOCAL_COST, null,
                statements, reconciled, problems, null);
    }

    private static List<JsonNode> jsonl(Path file) {
        try {
            List<JsonNode> out = new ArrayList<>();
            for (String line : Files.readAllLines(file)) {
                if (!line.isBlank()) {
                    out.add(JSON.readTree(line));
                }
            }
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }

    private static JsonNode read(Path file) {
        try {
            return JSON.readTree(file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }
}

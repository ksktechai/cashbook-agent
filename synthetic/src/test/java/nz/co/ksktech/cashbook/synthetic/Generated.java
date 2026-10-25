package nz.co.ksktech.cashbook.synthetic;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import nz.co.ksktech.cashbook.synthetic.truth.Jsonl;

/** One seed-42 run shared by the tests, generated on first use. */
final class Generated {

    static final long SEED = 42;
    static final Path OUT = Path.of("build", "synth-test", "seed-" + SEED);

    private static boolean done;

    private Generated() {}

    static synchronized Path out() {
        if (!done) {
            SyntheticGenerator.generate(SEED, OUT);
            done = true;
        }
        return OUT;
    }

    static List<JsonNode> jsonl(String relative) {
        try {
            return Files.readAllLines(out().resolve(relative)).stream().map(Generated::parse).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JsonNode parse(String line) {
        try {
            return Jsonl.MAPPER.readTree(line);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

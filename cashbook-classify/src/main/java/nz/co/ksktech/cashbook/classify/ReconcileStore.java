package nz.co.ksktech.cashbook.classify;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import nz.co.ksktech.cashbook.core.reconcile.Reconciler;

/** Writes the reconcile outputs: cashbook.jsonl, sections.jsonl, split.jsonl and flags.jsonl. */
public final class ReconcileStore {

    public static final String CASHBOOK = "cashbook.jsonl";
    public static final String SECTIONS = "sections.jsonl";
    public static final String SPLIT = "split.jsonl";
    public static final String FLAGS = "flags.jsonl";

    private ReconcileStore() {}

    public static void write(Path dir, Reconciler.Result result) {
        lines(dir.resolve(CASHBOOK), result.lines());
        lines(dir.resolve(SECTIONS), result.items());
        lines(dir.resolve(SPLIT), result.splits());
        lines(dir.resolve(FLAGS), result.flags());
    }

    private static void lines(Path file, List<?> records) {
        StringBuilder b = new StringBuilder();
        try {
            for (Object r : records) {
                b.append(ClassifyStore.MAPPER.writeValueAsString(r)).append('\n');
            }
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, b.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + file, e);
        }
    }
}

package nz.co.ksktech.cashbook.classify;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import nz.co.ksktech.cashbook.classify.llm.Classifier;
import nz.co.ksktech.cashbook.core.Transaction;

/** Reads and writes the classify outputs: classifications.jsonl, review.jsonl and llm-usage.json. */
public final class ClassifyStore {

    public static final String CLASSIFICATIONS = "classifications.jsonl";
    public static final String REVIEW = "review.jsonl";
    public static final String USAGE = "llm-usage.json";

    static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
            .build();

    private ClassifyStore() {}

    public static List<Transaction> readTransactions(Path file) {
        return read(file, Transaction.class);
    }

    public static List<ClassifiedLine> readLines(Path dir) {
        return read(dir.resolve(CLASSIFICATIONS), ClassifiedLine.class);
    }

    public static List<ReviewEntry> readReview(Path dir) {
        Path f = dir.resolve(REVIEW);
        return Files.exists(f) ? read(f, ReviewEntry.class) : List.of();
    }

    public static void write(Path dir, List<ClassifiedLine> lines, List<ReviewEntry> review, Classifier.Usage usage) {
        writeLines(dir, lines, review);
        try {
            atomicWrite(dir.resolve(USAGE), MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(usage) + "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Rewrites both files (used after each review answer, so progress survives a quit). */
    public static void writeLines(Path dir, List<ClassifiedLine> lines, List<ReviewEntry> review) {
        atomicWrite(dir.resolve(CLASSIFICATIONS), jsonl(lines));
        atomicWrite(dir.resolve(REVIEW), jsonl(review));
    }

    private static <T> List<T> read(Path file, Class<T> type) {
        try {
            List<T> out = new ArrayList<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    out.add(MAPPER.readValue(line, type));
                }
            }
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }

    private static String jsonl(List<?> records) {
        StringBuilder b = new StringBuilder();
        try {
            for (Object r : records) {
                b.append(MAPPER.writeValueAsString(r)).append('\n');
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return b.toString();
    }

    private static void atomicWrite(Path file, String content) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + file, e);
        }
    }
}

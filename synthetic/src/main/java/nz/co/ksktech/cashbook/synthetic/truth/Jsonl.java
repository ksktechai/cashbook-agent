package nz.co.ksktech.cashbook.synthetic.truth;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** JSON and JSONL output with ISO dates and plain decimals. */
public final class Jsonl {

    public static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
            .build();

    private Jsonl() {}

    public static void writeLines(Path file, List<?> records) {
        StringBuilder b = new StringBuilder();
        for (Object r : records) {
            b.append(json(r)).append('\n');
        }
        write(file, b.toString());
    }

    public static void writeJson(Path file, Object value) {
        try {
            write(file, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String json(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialise " + value, e);
        }
    }

    private static void write(Path file, String text) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + file, e);
        }
    }
}

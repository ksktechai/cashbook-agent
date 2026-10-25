package nz.co.ksktech.cashbook.ingest;

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
import java.util.ArrayList;
import java.util.List;

/** Writes an ingest result as JSONL files. */
public final class IngestWriter {

    public static final String TRANSACTIONS = "transactions.jsonl";
    public static final String STATEMENTS = "statements.jsonl";
    public static final String DOCUMENTS = "documents.jsonl";

    static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
            .build();

    /** One line of documents.jsonl: the kind, then the document's figures. */
    public record Document(String kind, Object document) {}

    private IngestWriter() {}

    public static void write(Ingestor.Result result, Path dir) {
        lines(dir.resolve(TRANSACTIONS), result.transactions());
        lines(dir.resolve(STATEMENTS), result.statements());
        List<Document> documents = new ArrayList<>();
        result.invoices().forEach(i -> documents.add(new Document("UTILITY_INVOICE", i)));
        result.levies().forEach(l -> documents.add(new Document("LEVY_NOTICE", l)));
        lines(dir.resolve(DOCUMENTS), documents);
    }

    private static void lines(Path file, List<?> records) {
        StringBuilder b = new StringBuilder();
        try {
            for (Object r : records) {
                b.append(MAPPER.writeValueAsString(r)).append('\n');
            }
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, b.toString(), StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + file, e);
        }
    }
}

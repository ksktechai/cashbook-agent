package nz.co.ksktech.cashbook.ingest;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import nz.co.ksktech.cashbook.core.LevyNotice;
import nz.co.ksktech.cashbook.core.UtilityInvoice;

/** Reads documents.jsonl back into typed documents. */
public final class IngestReader {

    private static final ObjectMapper MAPPER = IngestWriter.MAPPER.copy()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private IngestReader() {}

    public static List<UtilityInvoice> invoices(Path dir) {
        return documents(dir, "UTILITY_INVOICE", UtilityInvoice.class);
    }

    public static List<LevyNotice> levies(Path dir) {
        return documents(dir, "LEVY_NOTICE", LevyNotice.class);
    }

    private static <T> List<T> documents(Path dir, String kind, Class<T> type) {
        Path file = dir.resolve(IngestWriter.DOCUMENTS);
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            List<T> out = new ArrayList<>();
            for (String line : Files.readAllLines(file)) {
                JsonNode node = MAPPER.readTree(line);
                if (kind.equals(node.path("kind").asText())) {
                    out.add(MAPPER.treeToValue(node.get("document"), type));
                }
            }
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }
}

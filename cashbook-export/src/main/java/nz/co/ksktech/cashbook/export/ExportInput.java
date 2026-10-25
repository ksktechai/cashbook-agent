package nz.co.ksktech.cashbook.export;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import nz.co.ksktech.cashbook.core.CashbookLine;
import nz.co.ksktech.cashbook.core.reconcile.SectionItem;
import nz.co.ksktech.cashbook.core.reconcile.UtilitySplit;

/** What the export writes: reconciled cashbook lines, itemised section detail and the utility split table. */
public record ExportInput(List<CashbookLine> lines, List<SectionItem> items, List<UtilitySplit> splits) {

    public static final String CASHBOOK = "cashbook.jsonl";
    public static final String SECTIONS = "sections.jsonl";
    public static final String SPLIT = "split.jsonl";

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public ExportInput {
        lines = List.copyOf(lines);
        items = List.copyOf(items);
        splits = List.copyOf(splits);
    }

    /** Reads the reconcile output folder. */
    public static ExportInput read(Path dir) {
        return new ExportInput(read(dir.resolve(CASHBOOK), CashbookLine.class), read(dir.resolve(SECTIONS), SectionItem.class),
                read(dir.resolve(SPLIT), UtilitySplit.class));
    }

    private static <T> List<T> read(Path file, Class<T> type) {
        try {
            List<T> out = new ArrayList<>();
            for (String line : Files.readAllLines(file)) {
                if (!line.isBlank()) {
                    out.add(MAPPER.readValue(line, type));
                }
            }
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }
}

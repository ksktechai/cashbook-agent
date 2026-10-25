package nz.co.ksktech.cashbook.ingest.text;

import java.util.List;
import java.util.Optional;

/** The lines of one page, top to bottom. {@code ocr} is true when they came from OCR. */
public record PageText(int page, float width, float height, List<TextLine> lines, boolean ocr) {

    public PageText {
        lines = List.copyOf(lines);
    }

    public Optional<TextLine> firstLineContaining(String text) {
        return lines.stream().filter(l -> l.text().contains(text)).findFirst();
    }
}

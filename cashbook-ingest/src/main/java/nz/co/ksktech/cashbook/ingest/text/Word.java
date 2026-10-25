package nz.co.ksktech.cashbook.ingest.text;

import java.util.Objects;
import nz.co.ksktech.cashbook.core.BoundingBox;

/** One word and its box (top-left origin, points). {@code size} is the font size, or the box height for OCR. */
public record Word(String text, BoundingBox box, float size) {

    public Word {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(box, "box");
    }
}

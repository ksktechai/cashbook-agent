package nz.co.ksktech.cashbook.core;

import java.util.Objects;

/** Where a figure came from: file, 1-based page, the line's box and its raw text. */
public record SourceRef(String file, int page, BoundingBox box, String rawText) {

    public SourceRef {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(box, "box");
        Objects.requireNonNull(rawText, "rawText");
        if (page < 1) {
            throw new IllegalArgumentException("page must be 1 or more but was " + page);
        }
    }
}

package nz.co.ksktech.cashbook.synthetic.pdf;

import nz.co.ksktech.cashbook.core.BoundingBox;

/**
 * Where a statement line ended up: its 1-based page, the union box of all its text pieces (wrapped and
 * continuation lines included) and the pieces' text in reading order.
 *
 * @param highlightBox the highlighted area, or null when the line is not highlighted
 */
public record RenderedLine(String lineId, int page, BoundingBox box, String rawText, BoundingBox highlightBox) {}

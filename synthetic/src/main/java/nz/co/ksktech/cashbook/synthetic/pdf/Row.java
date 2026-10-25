package nz.co.ksktech.cashbook.synthetic.pdf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import nz.co.ksktech.cashbook.core.BoundingBox;
import nz.co.ksktech.cashbook.synthetic.model.HighlightColour;
import nz.co.ksktech.cashbook.synthetic.model.SpecLine;

/** Collects the text pieces of one statement line as it is drawn, for its truth record. */
final class Row {

    private record Piece(BoundingBox box, String text) {}

    private final SpecLine line;
    private final PdfPage page;
    private final List<Piece> pieces = new ArrayList<>();
    private BoundingBox highlightBox;

    Row(SpecLine line, PdfPage page) {
        this.line = line;
        this.page = page;
    }

    /** Records a drawn piece and returns its box. Empty text is ignored. */
    BoundingBox add(BoundingBox box, String text) {
        if (!text.isEmpty()) {
            pieces.add(new Piece(box, text));
        }
        return box;
    }

    /** Highlights {@code area} padded by the given amounts, if the line is highlighted. */
    void highlight(BoundingBox area, float padX, float padTop, float padBottom, boolean flatten) {
        HighlightColour colour = line.highlight();
        if (colour == null) {
            return;
        }
        BoundingBox padded = new BoundingBox(area.left() - padX, area.top() - padTop, area.width() + 2 * padX,
                area.height() + padTop + padBottom);
        page.highlight(padded, colour, flatten);
        highlightBox = highlightBox == null ? padded : union(List.of(highlightBox, padded));
    }

    RenderedLine rendered() {
        List<Piece> ordered = pieces.stream()
                .sorted(Comparator.comparingDouble((Piece p) -> Math.round(p.box().top()))
                        .thenComparingDouble(p -> p.box().left()))
                .toList();
        String raw = ordered.stream().map(Piece::text).collect(Collectors.joining(" "));
        return new RenderedLine(line.id(), page.number(), union(ordered.stream().map(Piece::box).toList()), raw,
                highlightBox);
    }

    static BoundingBox union(List<BoundingBox> boxes) {
        float left = Float.MAX_VALUE;
        float top = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        float bottom = -Float.MAX_VALUE;
        for (BoundingBox b : boxes) {
            left = Math.min(left, b.left());
            top = Math.min(top, b.top());
            right = Math.max(right, b.right());
            bottom = Math.max(bottom, b.bottom());
        }
        return new BoundingBox(left, top, right - left, bottom - top);
    }
}

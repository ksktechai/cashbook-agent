package nz.co.ksktech.cashbook.synthetic.pdf;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Lays out fixed-height blocks onto pages before drawing, for layouts that print "page n/N".
 * Each block is drawn with its page and top y.
 */
final class Blocks {

    private record Block(float height, BiConsumer<PdfPage, Float> draw) {}

    private record Placed(int page, float y, Block block) {}

    private final float firstTop;
    private final float nextTop;
    private final float bottom;
    private final List<Block> blocks = new ArrayList<>();

    Blocks(float firstTop, float nextTop, float bottom) {
        this.firstTop = firstTop;
        this.nextTop = nextTop;
        this.bottom = bottom;
    }

    void add(float height, BiConsumer<PdfPage, Float> draw) {
        blocks.add(new Block(height, draw));
    }

    /** Draws everything, calling {@code furniture} on each new page with (page, totalPages). */
    void draw(PdfDoc doc, BiConsumer<PdfPage, Integer> furniture) {
        List<Placed> placed = new ArrayList<>();
        int page = 1;
        float y = firstTop;
        for (Block b : blocks) {
            if (y + b.height() > bottom) {
                page++;
                y = nextTop;
            }
            placed.add(new Placed(page, y, b));
            y += b.height();
        }
        int total = page;
        int current = 0;
        PdfPage pdfPage = null;
        for (Placed p : placed) {
            while (current < p.page()) {
                pdfPage = doc.newPage();
                current++;
                furniture.accept(pdfPage, total);
            }
            p.block().draw().accept(pdfPage, p.y());
        }
        if (pdfPage == null) {
            furniture.accept(doc.newPage(), total);
        }
    }
}

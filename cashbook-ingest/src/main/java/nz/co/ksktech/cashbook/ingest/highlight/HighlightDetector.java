package nz.co.ksktech.cashbook.ingest.highlight;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nz.co.ksktech.cashbook.core.BoundingBox;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationHighlight;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

/**
 * Decides whether a line is highlighted. Any highlighter colour counts.
 *
 * <ol>
 *   <li>Annotations: the share of the line's box covered by Highlight annotation quads.
 *   <li>Pixels, for highlights flattened into the page: the page is rendered at 72 dpi and the share of
 *       the box's non-text pixels that are highlighter-coloured (saturated) is measured. Dark
 *       text pixels are left out of the count, so text on a highlight does not dilute it.
 * </ol>
 *
 * The line is highlighted when either share reaches the threshold (default 5%), or when any single cell
 * on it (an amount, a description) is at least half covered: a highlighter stroke covers a whole cell,
 * while bleed from a neighbouring row only clips a sliver of one.
 */
public final class HighlightDetector implements Highlights {

    /** Rendering at 72 dpi makes one pixel one point. */
    private static final float DPI = 72f;
    /** Pixels darker than this are text and are not counted either way. */
    private static final int TEXT_BRIGHTNESS = 100;
    /**
     * A counted pixel is highlighter-coloured when its channels differ by at least this much. Greys, white
     * and anti-aliased black text differ by ~0; highlighter colours, even where blended with text edges, don't.
     */
    private static final int MIN_SATURATION = 60;

    private final PDDocument doc;
    private final float threshold;
    private final Map<Integer, BufferedImage> rendered = new HashMap<>();
    private final Map<Integer, List<BoundingBox>> quads = new HashMap<>();

    public HighlightDetector(PDDocument doc, float threshold) {
        this.doc = doc;
        this.threshold = threshold;
    }

    /** Share of a single cell that must be covered for its line to count as highlighted. */
    private static final float CELL_THRESHOLD = 0.5f;

    @Override
    public boolean highlighted(int page, BoundingBox line, List<BoundingBox> cells) {
        return coverage(page, line) >= threshold
                || cells.stream().anyMatch(c -> coverage(page, c) >= CELL_THRESHOLD);
    }

    /** The larger of the annotation and pixel coverage, 0 to 1. */
    public float coverage(int page, BoundingBox line) {
        if (line.area() <= 0) {
            return 0;
        }
        float annotated = 0;
        for (BoundingBox q : quads(page)) {
            annotated += line.intersectionArea(q) / line.area();
        }
        return Math.max(Math.min(1f, annotated), pixelCoverage(page, line));
    }

    private float pixelCoverage(int page, BoundingBox line) {
        BufferedImage image = image(page);
        int x0 = Math.max(0, (int) Math.floor(line.left()));
        int x1 = Math.min(image.getWidth() - 1, (int) Math.ceil(line.right()));
        int y0 = Math.max(0, (int) Math.floor(line.top()));
        int y1 = Math.min(image.getHeight() - 1, (int) Math.ceil(line.bottom()));
        long counted = 0;
        long coloured = 0;
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                int max = Math.max(r, Math.max(g, b));
                int min = Math.min(r, Math.min(g, b));
                if (max < TEXT_BRIGHTNESS) {
                    continue;
                }
                counted++;
                if (max - min >= MIN_SATURATION) {
                    coloured++;
                }
            }
        }
        return counted == 0 ? 0 : (float) coloured / counted;
    }

    private BufferedImage image(int page) {
        return rendered.computeIfAbsent(page, p -> {
            try {
                return new PDFRenderer(doc).renderImageWithDPI(p - 1, DPI, ImageType.RGB);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /** Highlight annotation quads on a page, top-left origin. */
    private List<BoundingBox> quads(int page) {
        return quads.computeIfAbsent(page, p -> {
            PDPage pdPage = doc.getPage(p - 1);
            float height = pdPage.getMediaBox().getHeight();
            List<BoundingBox> boxes = new ArrayList<>();
            try {
                for (PDAnnotation a : pdPage.getAnnotations()) {
                    if (a instanceof PDAnnotationHighlight markup) {
                        float[] q = markup.getQuadPoints();
                        for (int i = 0; q != null && i + 7 < q.length; i += 8) {
                            float left = Math.min(Math.min(q[i], q[i + 2]), Math.min(q[i + 4], q[i + 6]));
                            float right = Math.max(Math.max(q[i], q[i + 2]), Math.max(q[i + 4], q[i + 6]));
                            float top = Math.max(Math.max(q[i + 1], q[i + 3]), Math.max(q[i + 5], q[i + 7]));
                            float bottom = Math.min(Math.min(q[i + 1], q[i + 3]), Math.min(q[i + 5], q[i + 7]));
                            boxes.add(new BoundingBox(left, height - top, right - left, top - bottom));
                        }
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return boxes;
        });
    }
}

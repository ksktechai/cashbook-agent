package nz.co.ksktech.cashbook.synthetic.pdf;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import nz.co.ksktech.cashbook.core.BoundingBox;
import nz.co.ksktech.cashbook.synthetic.model.HighlightColour;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.blend.BlendMode;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationHighlight;

/**
 * One page. Coordinates are top-left based (y grows downwards, {@code baseline} is the text baseline),
 * matching how PDFBox reports text positions, and are converted to PDF space here.
 */
public final class PdfPage {

    /** Fraction of the font size above the baseline and below it, used for truth boxes. */
    private static final float ASCENT = 0.72f;
    private static final float DESCENT = 0.22f;

    private final PDDocument document;
    private final PDPage page;
    private final int number;
    private final PDPageContentStream stream;
    private final List<Runnable> flattened = new ArrayList<>();
    private final java.util.function.Function<Face, PDFont> fonts;
    private boolean finished;

    PdfPage(PDDocument document, PDPage page, int number, java.util.function.Function<Face, PDFont> fonts) {
        this.document = document;
        this.fonts = fonts;
        this.page = page;
        this.number = number;
        try {
            this.stream = new PDPageContentStream(document, page);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public int number() {
        return number;
    }

    public float width() {
        return page.getMediaBox().getWidth();
    }

    public float height() {
        return page.getMediaBox().getHeight();
    }

    /** Draws text with its left edge at x and returns its box. */
    public BoundingBox text(Face font, float size, float x, float baseline, String text) {
        if (text.isEmpty()) {
            return new BoundingBox(x, baseline - ASCENT * size, 0, (ASCENT + DESCENT) * size);
        }
        try {
            stream.beginText();
            stream.setFont(fonts.apply(font), size);
            stream.newLineAtOffset(x, height() - baseline);
            stream.showText(text);
            stream.endText();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new BoundingBox(x, baseline - ASCENT * size, width(font, size, text), (ASCENT + DESCENT) * size);
    }

    /** Draws text with its right edge at {@code right}. */
    public BoundingBox textRight(Face font, float size, float right, float baseline, String text) {
        return text(font, size, right - width(font, size, text), baseline, text);
    }

    public BoundingBox textCentred(Face font, float size, float baseline, String text) {
        return text(font, size, (width() - width(font, size, text)) / 2, baseline, text);
    }

    public static float width(Face font, float size, String text) {
        try {
            return font.metrics().getStringWidth(text) / 1000f * size;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Highlights an area: as a real Highlight annotation, or (flattened) as a multiply-blended rectangle
     * drawn into the page content, like a highlight that was printed to PDF.
     */
    public void highlight(BoundingBox area, HighlightColour colour, boolean flatten) {
        float x0 = area.left();
        float x1 = area.right();
        float yTop = height() - area.top();
        float yBottom = height() - area.bottom();
        if (flatten) {
            flattened.add(() -> fillMultiply(area, colour));
            return;
        }
        PDAnnotationHighlight h = new PDAnnotationHighlight();
        h.setRectangle(new PDRectangle(x0, yBottom, x1 - x0, yTop - yBottom));
        h.setQuadPoints(new float[] {x0, yTop, x1, yTop, x0, yBottom, x1, yBottom});
        h.setColor(new PDColor(colour.rgb(), PDDeviceRGB.INSTANCE));
        h.setPrinted(true);
        h.constructAppearances(document);
        try {
            page.getAnnotations().add(h);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void fillMultiply(BoundingBox area, HighlightColour colour) {
        try {
            stream.saveGraphicsState();
            PDExtendedGraphicsState state = new PDExtendedGraphicsState();
            state.setBlendMode(BlendMode.MULTIPLY);
            stream.setGraphicsStateParameters(state);
            float[] rgb = colour.rgb();
            stream.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
            stream.addRect(area.left(), height() - area.bottom(), area.width(), area.height());
            stream.fill();
            stream.restoreGraphicsState();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A thin horizontal rule, as used under table headers. */
    public void rule(float x0, float x1, float y) {
        try {
            stream.setLineWidth(0.5f);
            stream.moveTo(x0, height() - y);
            stream.lineTo(x1, height() - y);
            stream.stroke();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void finish() {
        if (finished) {
            return;
        }
        finished = true;
        flattened.forEach(Runnable::run);
        try {
            stream.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

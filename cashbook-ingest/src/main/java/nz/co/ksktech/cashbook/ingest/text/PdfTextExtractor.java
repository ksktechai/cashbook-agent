package nz.co.ksktech.cashbook.ingest.text;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import nz.co.ksktech.cashbook.core.BoundingBox;
import nz.co.ksktech.cashbook.ingest.ocr.OcrEngine;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * Extracts each page's words with their boxes and groups them into lines. A page with no text layer is
 * rendered and read by OCR instead, giving the same {@link TextLine}s.
 */
public final class PdfTextExtractor {

    private final OcrEngine ocr;
    private final float ocrDpi;

    /** @param ocr the OCR engine, or null to leave image-only pages empty */
    public PdfTextExtractor(OcrEngine ocr, float ocrDpi) {
        this.ocr = ocr;
        this.ocrDpi = ocrDpi;
    }

    public List<PageText> extract(PDDocument doc) {
        List<PageText> pages = new ArrayList<>();
        for (int p = 1; p <= doc.getNumberOfPages(); p++) {
            PDPage page = doc.getPage(p - 1);
            float width = page.getMediaBox().getWidth();
            float height = page.getMediaBox().getHeight();
            List<Word> words = textWords(doc, p);
            boolean viaOcr = false;
            if (words.isEmpty() && ocr != null) {
                words = ocrWords(doc, p);
                viaOcr = true;
            }
            pages.add(new PageText(p, width, height, Lines.group(p, words), viaOcr));
        }
        return pages;
    }

    private static List<Word> textWords(PDDocument doc, int page) {
        List<Word> words = new ArrayList<>();
        try {
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> positions) {
                    // A run can hold several words ("Money out"), or two cells drawn with no space between
                    // them ("18:42Top"); split at spaces and at gaps wider than a fraction of a space.
                    List<TextPosition> word = new ArrayList<>();
                    for (TextPosition p : positions) {
                        boolean gap = !word.isEmpty() && p.getXDirAdj() - right(word.getLast())
                                > WORD_GAP_EM * p.getFontSizeInPt();
                        if (p.getUnicode().isBlank() || gap) {
                            addWord(word, words);
                            word = new ArrayList<>();
                        }
                        if (!p.getUnicode().isBlank()) {
                            word.add(p);
                        }
                    }
                    addWord(word, words);
                }
            };
            stripper.setSortByPosition(true);
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            stripper.getText(doc);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return words;
    }

    /** A gap between glyphs wider than this many font sizes separates words (a space is ~0.28). */
    private static final float WORD_GAP_EM = 0.15f;

    private static float right(TextPosition p) {
        return p.getXDirAdj() + p.getWidthDirAdj();
    }

    private static void addWord(List<TextPosition> glyphs, List<Word> words) {
        if (glyphs.isEmpty()) {
            return;
        }
        TextPosition first = glyphs.getFirst();
        TextPosition last = glyphs.getLast();
        StringBuilder text = new StringBuilder();
        glyphs.forEach(g -> text.append(g.getUnicode()));
        float baseline = first.getYDirAdj();
        float size = first.getFontSizeInPt();
        float height = Math.max(first.getHeightDir(), size * 0.7f);
        float left = first.getXDirAdj();
        float right = last.getXDirAdj() + last.getWidthDirAdj();
        words.add(new Word(text.toString(), new BoundingBox(left, baseline - height, right - left, height), size));
    }

    private List<Word> ocrWords(PDDocument doc, int page) {
        try {
            BufferedImage image = new PDFRenderer(doc).renderImageWithDPI(page - 1, ocrDpi, ImageType.GRAY);
            return ocr.read(image, 72f / ocrDpi);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

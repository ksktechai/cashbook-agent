package nz.co.ksktech.cashbook.synthetic.pdf;

import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * A standard-14 typeface. Each document gets its own font objects (PDFBox fonts keep per-document state,
 * so sharing one across documents makes the output depend on what was saved before).
 */
public enum Face {
    HELVETICA(Standard14Fonts.FontName.HELVETICA),
    HELVETICA_BOLD(Standard14Fonts.FontName.HELVETICA_BOLD),
    TIMES(Standard14Fonts.FontName.TIMES_ROMAN);

    private final Standard14Fonts.FontName name;
    /** Used only to measure text, never added to a document. */
    private final PDType1Font metrics;

    Face(Standard14Fonts.FontName name) {
        this.name = name;
        this.metrics = new PDType1Font(name);
    }

    PDType1Font newFont() {
        return new PDType1Font(name);
    }

    PDType1Font metrics() {
        return metrics;
    }
}

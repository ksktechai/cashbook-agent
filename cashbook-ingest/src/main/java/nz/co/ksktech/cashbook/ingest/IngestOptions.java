package nz.co.ksktech.cashbook.ingest;

import java.util.regex.Pattern;

/**
 * Settings for ingestion (the app fills these from {@code @ConfigMapping}).
 *
 * @param highlightThreshold share of a line's box that must be highlighted, e.g. 0.05
 * @param businessAccount account names matching this are BUSINESS accounts; others CHEQUE
 * @param ocrEnabled read image-only pages with Tesseract
 * @param tessdata Tesseract language data folder, or blank to look in the usual places
 * @param ocrDpi resolution pages are rendered at for OCR
 */
public record IngestOptions(
        double highlightThreshold, Pattern businessAccount, boolean ocrEnabled, String tessdata, int ocrDpi) {

    public static IngestOptions defaults() {
        return new IngestOptions(0.05, Pattern.compile("(?i)business"), true, "", 300);
    }
}

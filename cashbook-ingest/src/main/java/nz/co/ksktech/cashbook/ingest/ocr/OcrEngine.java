package nz.co.ksktech.cashbook.ingest.ocr;

import java.awt.image.BufferedImage;
import java.util.List;
import nz.co.ksktech.cashbook.ingest.text.Word;

/** Reads words from a page image. */
public interface OcrEngine {

    /**
     * @param scale multiply image pixels by this to get points (72 / dpi)
     * @return words with boxes in points, top-left origin
     */
    List<Word> read(BufferedImage image, float scale);
}

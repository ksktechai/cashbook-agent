package nz.co.ksktech.cashbook.ingest.ocr;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import nz.co.ksktech.cashbook.core.BoundingBox;
import nz.co.ksktech.cashbook.ingest.text.Word;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;

/** OCR with Tesseract through Tess4J. Needs the native library and English language data installed. */
public final class TesseractOcr implements OcrEngine {

    /** Where package managers put tessdata (Homebrew on Apple silicon and Intel, Debian/Ubuntu, Alpine). */
    public static final List<String> TESSDATA_CANDIDATES = List.of(
            "/opt/homebrew/share/tessdata",
            "/usr/local/share/tessdata",
            "/usr/share/tesseract-ocr/5/tessdata",
            "/usr/share/tesseract-ocr/4.00/tessdata",
            "/usr/share/tessdata");
    private static final String TESSDATA_ENV = "TESSDATA_PREFIX";
    private static final String LANGUAGE = "eng";

    private final String tessdata;
    private final int dpi;

    public TesseractOcr(String tessdata, int dpi) {
        this.tessdata = tessdata;
        this.dpi = dpi;
    }

    /** The configured path if given, else $TESSDATA_PREFIX, else the first known location with English data. */
    public static Optional<String> findTessdata(String configured) {
        if (configured != null && !configured.isBlank()) {
            return Optional.of(configured);
        }
        String env = System.getenv(TESSDATA_ENV);
        if (env != null && Files.exists(Path.of(env, LANGUAGE + ".traineddata"))) {
            return Optional.of(env);
        }
        return TESSDATA_CANDIDATES.stream()
                .filter(p -> Files.exists(Path.of(p, LANGUAGE + ".traineddata")))
                .findFirst();
    }

    /** True when Tesseract can actually run here (native library loads and data is present). */
    public static boolean available() {
        Optional<String> data = findTessdata(null);
        if (data.isEmpty()) {
            return false;
        }
        try {
            new TesseractOcr(data.get(), 72).read(new BufferedImage(8, 8, BufferedImage.TYPE_BYTE_GRAY), 1f);
            return true;
        } catch (LinkageError | RuntimeException e) {
            return false;
        }
    }

    @Override
    public List<Word> read(BufferedImage image, float scale) {
        Tesseract t = new Tesseract();
        t.setDatapath(tessdata);
        t.setLanguage(LANGUAGE);
        t.setVariable("user_defined_dpi", Integer.toString(dpi));
        t.setPageSegMode(ITessAPI.TessPageSegMode.PSM_AUTO);
        return t.getWords(image, ITessAPI.TessPageIteratorLevel.RIL_WORD).stream()
                .filter(w -> !w.getText().isBlank())
                .map(w -> {
                    Rectangle r = w.getBoundingBox();
                    BoundingBox box = new BoundingBox(r.x * scale, r.y * scale, r.width * scale, r.height * scale);
                    return new Word(w.getText().strip(), box, r.height * scale);
                })
                .toList();
    }
}

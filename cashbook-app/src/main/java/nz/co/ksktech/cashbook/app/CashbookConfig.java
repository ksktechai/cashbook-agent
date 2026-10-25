package nz.co.ksktech.cashbook.app;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.nio.file.Path;
import java.util.Optional;

/** Application settings under the {@code cashbook} prefix. */
@ConfigMapping(prefix = "cashbook")
public interface CashbookConfig {

    Rules rules();

    Review review();

    Ingest ingest();

    interface Rules {
        /** Rules YAML; the real one lives in {@code private/rules.yaml}. */
        @WithDefault("config/rules.example.yaml")
        Path file();
    }

    interface Review {
        /** Classifications below this confidence go to the review queue. */
        @WithDefault("0.8")
        double threshold();
    }

    interface Ingest {
        /** Share of a line's box that must be highlighted (any colour) for it to count. */
        @WithDefault("0.05")
        double highlightThreshold();

        /** Account names matching this regex are BUSINESS accounts; other accounts are CHEQUE. */
        @WithDefault("(?i)business")
        String businessAccountPattern();

        Ocr ocr();

        interface Ocr {
            /** Read image-only pages with Tesseract. */
            @WithDefault("true")
            boolean enabled();

            /** Tesseract language data folder; when absent the usual install locations are searched. */
            Optional<String> tessdata();

            /** Resolution pages are rendered at for OCR. */
            @WithDefault("300")
            int dpi();

            /** Where JNA looks for the native Tesseract library (Homebrew installs it outside the defaults). */
            @WithDefault("/opt/homebrew/lib:/usr/local/lib")
            String libraryPath();
        }
    }
}

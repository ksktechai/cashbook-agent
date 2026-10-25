package nz.co.ksktech.cashbook.app;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Application settings under the {@code cashbook} prefix. */
@ConfigMapping(prefix = "cashbook")
public interface CashbookConfig {

    Rules rules();

    Review review();

    Ingest ingest();

    Llm llm();

    Reconcile reconcile();

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

    /** The optional LLM. Only local open-source models through Ollama; no paid APIs. */
    interface Llm {
        /** "none" (rules only, the default) or "ollama". */
        @WithDefault("none")
        String provider();

        /**
         * Send every LLM suggestion to review (pre-filled, accept with "y"). On by default because local
         * models' confidence is not calibrated; set false to accept suggestions at or above
         * cashbook.review.threshold without review.
         */
        @WithDefault("true")
        boolean reviewAll();

        Ollama ollama();

        interface Ollama {
            /**
             * Servers to try in order; the first that answers is used. Put a Bonjour name (host.local) first
             * when the server's IP changes. Set CASHBOOK_LLM_OLLAMA_URLS in a git-ignored .env file.
             */
            @WithDefault("http://localhost:11434")
            List<String> urls();

            @WithDefault("qwen3:30b")
            String model();

            /** Per classification call. */
            @WithDefault("PT60S")
            Duration timeout();

            /** For checking whether a server is up. */
            @WithDefault("PT2S")
            Duration probeTimeout();
        }
    }

    interface Reconcile {
        /** Purchases above this, GST-exclusive, are flagged "may need capitalising". */
        @WithDefault("1000.00")
        BigDecimal capitaliseThresholdExclGst();

        /** Codes whose purchases can be assets (not services, levies or insurance). */
        @WithDefault("734,709")
        Set<String> capitaliseCodes();

        /** Codes summed to one cashbook line per month (the template has one row per month for these). */
        @WithDefault("726,729")
        Set<String> monthlyCodes();

        /** Code -> OtherCalculation section; defaults in application.yaml. */
        Map<String, String> sections();

        /** Days allowed between a bank repayment and the card's "payment received". */
        @WithDefault("3")
        int repaymentWindowDays();
    }
}

package nz.co.ksktech.cashbook.app;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.nio.file.Path;

/** Application settings under the {@code cashbook} prefix. */
@ConfigMapping(prefix = "cashbook")
public interface CashbookConfig {

    Rules rules();

    Review review();

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
}

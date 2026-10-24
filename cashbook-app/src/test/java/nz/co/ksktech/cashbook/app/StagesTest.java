package nz.co.ksktech.cashbook.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.jboss.logmanager.formatters.PatternFormatter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StagesTest {

    private static final PatternFormatter MESSAGE_ONLY = new PatternFormatter("%s");

    private final List<String> messages = new ArrayList<>();
    private final Logger logger = Logger.getLogger(Stages.class.getName());
    private final Handler capture = new Handler() {
        @Override
        public void publish(LogRecord record) {
            messages.add(MESSAGE_ONLY.format(record));
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
    };

    @BeforeEach
    void attach() {
        logger.addHandler(capture);
    }

    @AfterEach
    void detach() {
        logger.removeHandler(capture);
    }

    @Test
    void marksEntryAndExitAndReturnsResult() {
        String result = Stages.run("ingest", () -> "done");

        assertThat(result).isEqualTo("done");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).isEqualTo(">>> ingest");
        assertThat(messages.get(1)).startsWith("<<< ingest durationMs=");
    }

    @Test
    void marksFailureAndRethrows() {
        assertThatIllegalStateException()
                .isThrownBy(() -> Stages.run("classify", () -> {
                    throw new IllegalStateException("boom");
                }))
                .withMessage("boom");

        assertThat(messages).hasSize(2);
        assertThat(messages.get(1)).startsWith("<<< classify failed durationMs=");
    }
}

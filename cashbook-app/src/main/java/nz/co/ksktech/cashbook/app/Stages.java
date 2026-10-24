package nz.co.ksktech.cashbook.app;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.jboss.logging.Logger;

/** Wraps a pipeline stage with {@code >>>} entry and {@code <<<} exit log markers. */
public final class Stages {

    private static final Logger LOG = Logger.getLogger(Stages.class);

    private Stages() {}

    public static <T> T run(String stage, Supplier<T> body) {
        LOG.infof(">>> %s", stage);
        long start = System.nanoTime();
        try {
            T result = body.get();
            LOG.infof("<<< %s durationMs=%d", stage, elapsedMs(start));
            return result;
        } catch (RuntimeException | Error e) {
            LOG.errorf(e, "<<< %s failed durationMs=%d", stage, elapsedMs(start));
            throw e;
        }
    }

    private static long elapsedMs(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}

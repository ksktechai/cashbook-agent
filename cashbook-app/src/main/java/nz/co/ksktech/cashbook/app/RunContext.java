package nz.co.ksktech.cashbook.app;

import java.util.UUID;
import org.jboss.logging.MDC;

/** Scopes one pipeline run: every log line inside it carries the run's correlation id. */
public final class RunContext implements AutoCloseable {

    public static final String CORRELATION_ID = "correlationId";

    private final String correlationId;

    private RunContext(String correlationId) {
        this.correlationId = correlationId;
        MDC.put(CORRELATION_ID, correlationId);
    }

    public static RunContext start() {
        return new RunContext(UUID.randomUUID().toString());
    }

    public String correlationId() {
        return correlationId;
    }

    @Override
    public void close() {
        MDC.remove(CORRELATION_ID);
    }
}

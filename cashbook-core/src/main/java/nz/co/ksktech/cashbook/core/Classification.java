package nz.co.ksktech.cashbook.core;

import java.util.Objects;

/**
 * A decision about one transaction.
 *
 * @param decidedBy rule id or model name
 */
public record Classification(
        String code,
        GstTreatment gst,
        double confidence,
        ClassificationMethod method,
        String decidedBy,
        String rationale) {

    public Classification {
        Objects.requireNonNull(gst, "gst");
        Objects.requireNonNull(method, "method");
        if (confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("confidence must be between 0 and 1 but was " + confidence);
        }
    }
}

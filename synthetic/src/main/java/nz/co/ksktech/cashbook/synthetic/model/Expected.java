package nz.co.ksktech.cashbook.synthetic.model;

import java.util.Objects;
import nz.co.ksktech.cashbook.core.GstTreatment;

/**
 * The answer key for one statement line.
 *
 * @param code the cashbook code, or null when the line is excluded
 * @param reason why, in a few words (shown in review and eval reports)
 */
public record Expected(String code, GstTreatment gst, String reason) {

    public Expected {
        Objects.requireNonNull(gst, "gst");
        Objects.requireNonNull(reason, "reason");
        if ((code == null) != (gst == GstTreatment.EXCLUDED)) {
            throw new IllegalArgumentException("excluded lines have no code, others need one: " + code + "/" + gst);
        }
    }

    public static Expected coded(String code, GstTreatment gst, String reason) {
        return new Expected(code, gst, reason);
    }

    public static Expected excluded(String reason) {
        return new Expected(null, GstTreatment.EXCLUDED, reason);
    }
}

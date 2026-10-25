package nz.co.ksktech.cashbook.core.reconcile;

/**
 * Something reconciliation wants a person to confirm. The flagged line stays in the cashbook unless the
 * answer excludes it.
 *
 * @param id stable: the line id plus the kind, so an answer applies again on the next run
 */
public record Flag(String id, String lineId, Kind kind, String question) {

    public enum Kind {
        PERIOD_CUT_OFF,
        HIGH_VALUE,
        DOUBLE_COUNT,
        UTILITY_MISMATCH,
        UNPAIRED_FX
    }

    public static Flag of(String lineId, Kind kind, String question) {
        return new Flag(idFor(lineId, kind), lineId, kind, question);
    }

    public static String idFor(String lineId, Kind kind) {
        return lineId + ":" + kind.name();
    }
}

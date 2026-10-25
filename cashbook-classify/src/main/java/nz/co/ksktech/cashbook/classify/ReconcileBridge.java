package nz.co.ksktech.cashbook.classify;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nz.co.ksktech.cashbook.core.ReviewItem;
import nz.co.ksktech.cashbook.core.reconcile.Classified;
import nz.co.ksktech.cashbook.core.reconcile.Flag;

/**
 * Connects classify's files to reconciliation: classified lines in, review answers to flags as decisions,
 * and new flags added to the review queue (so `review` asks them and the next reconcile honours them).
 */
public final class ReconcileBridge {

    private static final String EXCLUDED = "excluded";
    private static final String FLAG_SEPARATOR = ":";

    private ReconcileBridge() {}

    /** A line still under review is pending and stays out of the cashbook. */
    public static List<Classified> toClassified(List<ClassifiedLine> lines) {
        return lines.stream().map(l -> new Classified(l.id(), l.transaction(), l.classification(), l.special(),
                l.aggregate(), l.businessAmount(), !l.review().isEmpty())).toList();
    }

    /** Answered flags: flag id -> keep the line (false when the answer excluded it). */
    public static Map<String, Boolean> decisions(List<ReviewEntry> queue) {
        Map<String, Boolean> decisions = new HashMap<>();
        for (ReviewEntry e : queue) {
            if (e.id().contains(FLAG_SEPARATOR) && e.answered()) {
                decisions.put(e.id(), !e.item().answer().startsWith(EXCLUDED));
            }
        }
        return decisions;
    }

    /** The queue with an entry added for each flag not already in it. */
    public static List<ReviewEntry> withFlags(List<ReviewEntry> queue, List<Flag> flags, List<ClassifiedLine> lines) {
        Map<String, ClassifiedLine> byId = new HashMap<>();
        lines.forEach(l -> byId.put(l.id(), l));
        Set<String> present = new HashSet<>();
        queue.forEach(e -> present.add(e.id()));
        List<ReviewEntry> out = new ArrayList<>(queue);
        for (Flag f : flags) {
            ClassifiedLine line = byId.get(f.lineId());
            if (line != null && present.add(f.id())) {
                out.add(new ReviewEntry(f.id(), new ReviewItem(line.transaction(), line.classification(), f.question(), null)));
            }
        }
        return out;
    }
}

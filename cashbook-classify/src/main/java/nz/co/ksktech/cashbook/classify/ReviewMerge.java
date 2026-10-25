package nz.co.ksktech.cashbook.classify;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nz.co.ksktech.cashbook.core.ClassificationMethod;

/**
 * Keeps earlier review work when classify runs again: lines a person already decided keep that HUMAN
 * classification, answered questions are not asked again, and reconcile flags (answered or not) stay in
 * the queue. Lines are matched by their stable transaction id.
 */
public final class ReviewMerge {

    private static final String FLAG_SEPARATOR = ":";

    private ReviewMerge() {}

    public static ClassificationService.Result carryOver(ClassificationService.Result fresh, List<ClassifiedLine> previous,
            List<ReviewEntry> previousQueue) {
        Map<String, ClassifiedLine> decided = new HashMap<>();
        previous.stream().filter(l -> l.classification().method() == ClassificationMethod.HUMAN)
                .forEach(l -> decided.put(l.id(), l));
        Map<String, ReviewEntry> answered = new HashMap<>();
        previousQueue.stream().filter(ReviewEntry::answered).forEach(e -> answered.put(e.id(), e));

        List<ClassifiedLine> lines = new ArrayList<>();
        for (ClassifiedLine line : fresh.lines()) {
            ClassifiedLine human = decided.get(line.id());
            lines.add(human == null ? line : new ClassifiedLine(line.id(), line.transaction(), human.classification(),
                    line.special(), line.aggregate(), line.businessAmount(), List.of()));
        }
        Set<String> ids = new HashSet<>();
        List<ReviewEntry> queue = new ArrayList<>();
        for (ReviewEntry e : fresh.review()) {
            ReviewEntry kept = answered.getOrDefault(e.id(), decided.containsKey(e.id()) ? null : e);
            if (kept != null && ids.add(kept.id())) {
                queue.add(kept);
            }
        }
        for (ReviewEntry e : previousQueue) {
            boolean flag = e.id().contains(FLAG_SEPARATOR);
            if ((flag || answered.containsKey(e.id())) && ids.add(e.id())) {
                queue.add(e);
            }
        }
        return new ClassificationService.Result(lines, queue, fresh.usage());
    }
}

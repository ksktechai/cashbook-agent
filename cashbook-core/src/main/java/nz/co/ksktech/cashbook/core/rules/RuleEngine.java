package nz.co.ksktech.cashbook.core.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import nz.co.ksktech.cashbook.core.Classification;
import nz.co.ksktech.cashbook.core.ClassificationMethod;
import nz.co.ksktech.cashbook.core.GstTreatment;
import nz.co.ksktech.cashbook.core.Transaction;
import nz.co.ksktech.cashbook.core.rules.Rules.Exclude;
import nz.co.ksktech.cashbook.core.rules.Rules.HighlightPolicy;
import nz.co.ksktech.cashbook.core.rules.Rules.Vendor;

/**
 * Applies the rules to one transaction, in order: highlight policy, exclusions, vendors. The first match
 * decides with confidence 1.0. Pure: no I/O.
 */
public final class RuleEngine {

    public static final String HIGHLIGHT_POLICY = "highlightPolicy";

    private final Rules rules;

    public RuleEngine(Rules rules) {
        this.rules = rules;
    }

    public Rules rules() {
        return rules;
    }

    /** A rule's decision, the vendor rule behind it (if any), and why the line still needs a human. */
    public record Decision(Classification classification, Vendor vendor, List<String> reviewReasons) {

        public Decision {
            reviewReasons = List.copyOf(reviewReasons);
        }
    }

    /** The decision, or empty when no rule matches (the line is unknown). */
    public Optional<Decision> decide(Transaction t) {
        if (rules.policyFor(t.accountType()) == HighlightPolicy.HIGHLIGHTED_ONLY && !t.highlighted()) {
            return Optional.of(excluded(HIGHLIGHT_POLICY, "personal: not highlighted on a "
                    + t.accountType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ') + " account"));
        }
        String text = t.description();
        for (Exclude e : rules.excludes()) {
            if ((e.accounts().isEmpty() || e.accounts().contains(t.accountType())) && e.match().matcher(text).find()) {
                return Optional.of(excluded(e.id(), e.reason()));
            }
        }
        for (Vendor v : rules.vendors()) {
            if (!v.accounts().isEmpty() && !v.accounts().contains(t.accountType())) {
                continue;
            }
            if (v.onlyIfHighlighted() && !t.highlighted()) {
                continue;
            }
            if (!v.match().matcher(text).find()) {
                continue;
            }
            Rules.Code code = rules.codes().get(v.code());
            GstTreatment gst = v.gst() != null ? v.gst() : code.gst();
            String rationale = code.name() + (v.note() == null ? "" : " - " + v.note());
            List<String> review = new ArrayList<>();
            if (v.ask() != null) {
                review.add(v.ask());
            }
            if (v.reviewIfOverInclGst() != null && t.amount().abs().compareTo(v.reviewIfOverInclGst()) > 0) {
                review.add("Over " + v.reviewIfOverInclGst() + " incl. GST: check the classification");
            }
            return Optional.of(new Decision(
                    new Classification(v.code(), gst, 1.0, ClassificationMethod.RULE, v.id(), rationale), v, review));
        }
        return Optional.empty();
    }

    private static Decision excluded(String ruleId, String reason) {
        return new Decision(new Classification(null, GstTreatment.EXCLUDED, 1.0, ClassificationMethod.RULE, ruleId,
                reason), null, List.of());
    }
}

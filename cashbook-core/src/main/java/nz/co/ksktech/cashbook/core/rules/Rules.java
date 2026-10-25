package nz.co.ksktech.cashbook.core.rules;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import nz.co.ksktech.cashbook.core.AccountType;
import nz.co.ksktech.cashbook.core.GstTreatment;

/**
 * The rules file, after loading and validation. Regexes are compiled; codes referenced by vendors exist.
 *
 * @param businessUse reference percentages by code (0-1); the template does not apply them, the accountant does
 */
public record Rules(
        Map<String, Code> codes,
        Map<String, BigDecimal> businessUse,
        List<Vendor> vendors,
        List<Exclude> excludes,
        Map<AccountType, HighlightPolicy> highlightPolicy) {

    public Rules {
        codes = Map.copyOf(codes);
        businessUse = Map.copyOf(businessUse);
        vendors = List.copyOf(vendors);
        excludes = List.copyOf(excludes);
        highlightPolicy = Map.copyOf(highlightPolicy);
    }

    /**
     * A code from the accountant's chart and its usual GST treatment.
     *
     * @param hint what belongs under the code (for the optional LLM), or null
     */
    public record Code(String name, GstTreatment gst, String hint) {}

    public enum HighlightPolicy {
        /** Every line on the account is classified. */
        ALL_LINES,
        /** Only highlighted lines are classified; the rest are personal. */
        HIGHLIGHTED_ONLY
    }

    public enum Aggregate {
        MONTHLY
    }

    /**
     * A vendor rule. {@code id} identifies it in classifications ("vendors[3]" or "learned[0]").
     *
     * @param gst overrides the code's default treatment, or null
     * @param accounts account types the rule applies to; empty means all
     * @param special handler applied in reconciliation, e.g. "utilitySplit", or null
     * @param businessAmount for a mixed charge, the fixed business portion; the rest is personal
     * @param reviewIfOverInclGst send to review when the line's amount (GST incl.) is larger, or null
     * @param ask a policy question that always sends the line to review, or null
     */
    public record Vendor(
            String id,
            Pattern match,
            String code,
            GstTreatment gst,
            Set<AccountType> accounts,
            boolean onlyIfHighlighted,
            Aggregate aggregate,
            String special,
            BigDecimal businessAmount,
            BigDecimal reviewIfOverInclGst,
            String ask,
            String note) {

        public Vendor {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(match, "match");
            Objects.requireNonNull(code, "code");
            accounts = accounts == null ? Set.of() : Set.copyOf(accounts);
        }
    }

    /** Lines matching are excluded from the cashbook with this reason (transfers, card repayments, tax...). */
    public record Exclude(String id, Pattern match, String reason, Set<AccountType> accounts) {

        public Exclude {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(match, "match");
            Objects.requireNonNull(reason, "reason");
            accounts = accounts == null ? Set.of() : Set.copyOf(accounts);
        }
    }

    public HighlightPolicy policyFor(AccountType type) {
        return highlightPolicy.getOrDefault(type, HighlightPolicy.HIGHLIGHTED_ONLY);
    }
}

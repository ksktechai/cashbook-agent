package nz.co.ksktech.cashbook.classify;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import nz.co.ksktech.cashbook.classify.llm.Classifier;
import nz.co.ksktech.cashbook.classify.llm.Classifier.RejectedException;
import nz.co.ksktech.cashbook.classify.llm.Classifier.Suggestion;
import nz.co.ksktech.cashbook.core.Classification;
import nz.co.ksktech.cashbook.core.ClassificationMethod;
import nz.co.ksktech.cashbook.core.GstTreatment;
import nz.co.ksktech.cashbook.core.ReviewItem;
import nz.co.ksktech.cashbook.core.Transaction;
import nz.co.ksktech.cashbook.core.TransactionId;
import nz.co.ksktech.cashbook.core.rules.RuleEngine;
import nz.co.ksktech.cashbook.core.rules.Rules;

/**
 * Classifies transactions: rules first (confidence 1.0); for lines no rule matches, the optional LLM
 * suggests a code from redacted fields; anything unsettled goes to the review queue.
 */
public final class ClassificationService {

    /** decidedBy of a line no rule or model settled: it waits for a person. */
    public static final String UNKNOWN = "unknown";
    private static final String NZD = "NZD";

    private final RuleEngine engine;
    private final Classifier classifier;
    private final double threshold;
    private final boolean reviewAllSuggestions;

    /** @param classifier the LLM, or null for rules only */
    public ClassificationService(Rules rules, Classifier classifier, double threshold) {
        this(rules, classifier, threshold, false);
    }

    /**
     * @param reviewAllSuggestions send every LLM suggestion to review as a proposal, whatever its confidence
     *     (local models' confidence is not calibrated)
     */
    public ClassificationService(Rules rules, Classifier classifier, double threshold, boolean reviewAllSuggestions) {
        this.engine = new RuleEngine(rules);
        this.classifier = classifier;
        this.threshold = threshold;
        this.reviewAllSuggestions = reviewAllSuggestions;
    }

    /** Classified lines in input order, and the review queue. */
    public record Result(List<ClassifiedLine> lines, List<ReviewEntry> review, Classifier.Usage usage) {

        public Result {
            lines = List.copyOf(lines);
            review = List.copyOf(review);
        }
    }

    public Result classify(List<Transaction> transactions) {
        List<ClassifiedLine> lines = new ArrayList<>();
        List<ReviewEntry> review = new ArrayList<>();
        Map<Classifier.Request, Object> cache = new HashMap<>();
        for (Transaction t : transactions) {
            String id = TransactionId.of(t);
            Optional<RuleEngine.Decision> decision = engine.decide(t);
            ClassifiedLine line = decision.isPresent() ? fromRule(id, t, decision.get()) : fromModel(id, t, cache);
            lines.add(line);
            if (!line.review().isEmpty()) {
                review.add(new ReviewEntry(id, new ReviewItem(t, line.classification(), String.join(" ", line.review()), null)));
            }
        }
        return new Result(lines, review, classifier == null ? new Classifier.Usage(0, 0, 0, 0) : classifier.usage());
    }

    private static ClassifiedLine fromRule(String id, Transaction t, RuleEngine.Decision d) {
        Rules.Vendor v = d.vendor();
        return new ClassifiedLine(id, t, d.classification(), v == null ? null : v.special(),
                v == null || v.aggregate() == null ? null : v.aggregate().name(), v == null ? null : v.businessAmount(),
                d.reviewReasons());
    }

    private ClassifiedLine fromModel(String id, Transaction t, Map<Classifier.Request, Object> cache) {
        if (classifier == null) {
            return pending(id, t, "No rule matches this line. Which code is it, or is it personal?");
        }
        Classifier.Request request = new Classifier.Request(Redactor.redact(t.description()), t.amount(),
                t.foreign() == null ? NZD : t.foreign().currency(), t.transactionDate(), t.accountType(), t.highlighted());
        // the same merchant line (same redacted text and account) is asked once per run
        Classifier.Request key = new Classifier.Request(request.description(), null, null, null, request.accountType(),
                request.highlighted());
        Object answer = cache.computeIfAbsent(key, k -> {
            try {
                return classifier.suggest(request, engine.rules().codes());
            } catch (RejectedException e) {
                return e;
            }
        });
        if (answer instanceof RejectedException e) {
            return pending(id, t, "The model's answer was rejected (" + e.getMessage() + "). Which code is it?");
        }
        Suggestion s = (Suggestion) answer;
        Classification c = new Classification(s.code(), s.gst(), s.confidence(), ClassificationMethod.LLM,
                classifier.name(), s.rationale());
        List<String> review = s.confidence() < threshold
                ? List.of(String.format(java.util.Locale.ROOT, "Low confidence (%.2f): is this right?", s.confidence()))
                : reviewAllSuggestions ? List.of("Model suggestion: is this right?") : List.of();
        return new ClassifiedLine(id, t, c, null, null, null, review);
    }

    private static ClassifiedLine pending(String id, Transaction t, String question) {
        Classification c = new Classification(null, GstTreatment.EXCLUDED, 0.0, ClassificationMethod.RULE, UNKNOWN,
                "not classified yet");
        return new ClassifiedLine(id, t, c, null, null, null, List.of(question));
    }
}

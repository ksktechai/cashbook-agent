package nz.co.ksktech.cashbook.classify.llm;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import nz.co.ksktech.cashbook.core.AccountType;
import nz.co.ksktech.cashbook.core.GstTreatment;
import nz.co.ksktech.cashbook.core.rules.Rules;

/**
 * The single port for LLM classification. Implementations get only the redacted fields below and must
 * return a {@link Suggestion} or throw {@link RejectedException}; nothing they return is trusted until
 * {@link SuggestionValidator} has checked it.
 */
public interface Classifier {

    /** "ollama:qwen3:30b" and the like, recorded as the classification's decidedBy. */
    String name();

    Suggestion suggest(Request request, Map<String, Rules.Code> allowedCodes);

    /** Usage so far: calls, tokens and time, for the evaluation report. */
    Usage usage();

    /** What the model sees. {@code description} has already been redacted. */
    record Request(String description, BigDecimal amount, String currency, LocalDate date, AccountType accountType,
            boolean highlighted) {}

    /** The model's answer. {@code code} is null when it says the line is not a cashbook item. */
    record Suggestion(String code, GstTreatment gst, double confidence, String rationale) {}

    record Usage(int calls, long inputTokens, long outputTokens, long millis) {}

    /** The model's answer was unusable (malformed, off-schema, a code not in the list) or the call failed. */
    final class RejectedException extends RuntimeException {
        public RejectedException(String message) {
            super(message);
        }

        public RejectedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

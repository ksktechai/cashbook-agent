package nz.co.ksktech.cashbook.classify.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import nz.co.ksktech.cashbook.classify.llm.Classifier.RejectedException;
import nz.co.ksktech.cashbook.classify.llm.Classifier.Suggestion;
import nz.co.ksktech.cashbook.core.GstTreatment;
import nz.co.ksktech.cashbook.core.rules.Rules;

/**
 * Checks a model's raw reply. It must be one JSON object with exactly {code, gst, confidence, rationale};
 * the code must be one of the rules file's codes (or null with gst EXCLUDED); confidence 0-1. Anything
 * else is rejected, whatever the reply says about itself.
 */
public final class SuggestionValidator {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> FIELDS = Set.of("code", "gst", "confidence", "rationale");
    private static final int MAX_RATIONALE = 300;

    private SuggestionValidator() {}

    public static Suggestion validate(String reply, Map<String, Rules.Code> allowedCodes) {
        JsonNode node;
        try {
            node = JSON.readTree(reply);
        } catch (Exception e) {
            throw new RejectedException("reply is not JSON");
        }
        if (node == null || !node.isObject()) {
            throw new RejectedException("reply is not a JSON object");
        }
        for (var it = node.fieldNames(); it.hasNext(); ) {
            String f = it.next();
            if (!FIELDS.contains(f)) {
                throw new RejectedException("unexpected field '" + f + "'");
            }
        }
        JsonNode gstNode = node.get("gst");
        JsonNode confidence = node.get("confidence");
        if (gstNode == null || !gstNode.isTextual() || confidence == null || !confidence.isNumber()) {
            throw new RejectedException("gst and confidence are required");
        }
        GstTreatment gst;
        try {
            gst = GstTreatment.valueOf(gstNode.asText());
        } catch (IllegalArgumentException e) {
            throw new RejectedException("gst '" + gstNode.asText() + "' is not a GST treatment");
        }
        double c = confidence.asDouble();
        if (c < 0 || c > 1) {
            throw new RejectedException("confidence " + c + " is outside 0-1");
        }
        JsonNode codeNode = node.get("code");
        String code = codeNode == null || codeNode.isNull() ? null : codeNode.asText();
        if (code == null && gst != GstTreatment.EXCLUDED) {
            throw new RejectedException("a missing code needs gst EXCLUDED");
        }
        if (code != null && gst == GstTreatment.EXCLUDED) {
            throw new RejectedException("gst EXCLUDED cannot have a code");
        }
        if (code != null && !allowedCodes.containsKey(code)) {
            throw new RejectedException("code '" + code + "' is not in the rules file");
        }
        String rationale = node.path("rationale").asText("");
        if (rationale.length() > MAX_RATIONALE) {
            rationale = rationale.substring(0, MAX_RATIONALE);
        }
        return new Suggestion(code, gst, c, rationale);
    }
}

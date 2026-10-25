package nz.co.ksktech.cashbook.synthetic.truth;

import java.util.List;
import nz.co.ksktech.cashbook.core.BoundingBox;
import nz.co.ksktech.cashbook.core.Transaction;
import nz.co.ksktech.cashbook.synthetic.model.Expected;

/**
 * Ground truth for one generated statement line: what the extractor should produce, how it was
 * highlighted, the expected classification and the traps it exercises.
 *
 * @param highlightColour colour name, or null when not highlighted
 * @param highlightBox highlighted area on the page, or null
 */
public record TruthTransaction(
        String id,
        String layout,
        Transaction transaction,
        String highlightColour,
        boolean flattenedHighlight,
        BoundingBox highlightBox,
        Expected expected,
        List<String> traps) {}

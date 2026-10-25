package nz.co.ksktech.cashbook.classify;

import java.math.BigDecimal;
import java.util.List;
import nz.co.ksktech.cashbook.core.Classification;
import nz.co.ksktech.cashbook.core.Transaction;

/**
 * One line of classifications.jsonl.
 *
 * @param id stable id ({@link nz.co.ksktech.cashbook.core.TransactionId}), also used by review.jsonl
 * @param special the vendor rule's handler for reconciliation (e.g. "utilitySplit"), or null
 * @param aggregate "MONTHLY" when the vendor's lines are summed per month, or null
 * @param businessAmount the fixed business portion of a mixed charge, or null
 * @param review why a person must look at it; empty when settled
 */
public record ClassifiedLine(
        String id,
        Transaction transaction,
        Classification classification,
        String special,
        String aggregate,
        BigDecimal businessAmount,
        List<String> review) {

    public ClassifiedLine {
        review = review == null ? List.of() : List.copyOf(review);
    }
}

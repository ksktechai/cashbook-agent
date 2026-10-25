package nz.co.ksktech.cashbook.core.reconcile;

import java.math.BigDecimal;
import java.util.Objects;
import nz.co.ksktech.cashbook.core.Classification;
import nz.co.ksktech.cashbook.core.Transaction;

/**
 * A classified transaction as reconciliation sees it.
 *
 * @param special the vendor rule's handler ("utilitySplit", "mixedCharge", ...), or null
 * @param aggregate "MONTHLY" to sum per month, or null
 * @param businessAmount the business portion of a mixed charge, or null
 * @param pending still waiting for review (left out of the cashbook)
 */
public record Classified(
        String id,
        Transaction transaction,
        Classification classification,
        String special,
        String aggregate,
        BigDecimal businessAmount,
        boolean pending) {

    public Classified {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(classification, "classification");
    }
}

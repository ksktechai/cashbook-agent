package nz.co.ksktech.cashbook.core.reconcile;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import nz.co.ksktech.cashbook.core.AccountType;
import nz.co.ksktech.cashbook.core.GstTreatment;

/**
 * The double-count guard. A card's "payment received" (money in on a card) must be matched by a payment of
 * the same amount out of a bank account within a few days. If that bank payment was <em>coded</em> as an
 * expense rather than excluded, the card's purchases would be counted twice, so it is flagged.
 */
public final class RepaymentGuard {

    private RepaymentGuard() {}

    public static List<Flag> check(List<Classified> lines, int windowDays) {
        List<Flag> flags = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (Classified card : lines) {
            var t = card.transaction();
            if (t.accountType() != AccountType.CREDIT_CARD || t.amount().signum() <= 0) {
                continue;
            }
            for (Classified bank : lines) {
                var b = bank.transaction();
                if (b.accountType() == AccountType.CREDIT_CARD || used.contains(bank.id())
                        || b.amount().negate().compareTo(t.amount()) != 0
                        || Math.abs(ChronoUnit.DAYS.between(b.transactionDate(), t.transactionDate())) > windowDays) {
                    continue;
                }
                used.add(bank.id());
                if (bank.classification().gst() != GstTreatment.EXCLUDED) {
                    flags.add(Flag.of(bank.id(), Flag.Kind.DOUBLE_COUNT, "This " + b.amount().negate()
                            + " looks like a card repayment (the card received " + t.amount() + " on "
                            + t.transactionDate() + ") but is coded " + bank.classification().code()
                            + ". Keep it? Card purchases are already in the cashbook."));
                }
                break;
            }
        }
        return flags;
    }
}

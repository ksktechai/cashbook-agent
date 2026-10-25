package nz.co.ksktech.cashbook.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Whether a statement's extracted lines add up to what it prints.
 *
 * @param moneyIn sum of the extracted positive lines
 * @param moneyOut sum of the extracted negative lines, as a positive number
 * @param problems one sentence per check that failed; empty when reconciled
 */
public record Reconciliation(String file, BigDecimal moneyIn, BigDecimal moneyOut, List<String> problems) {

    public Reconciliation {
        problems = List.copyOf(problems);
    }

    public boolean reconciled() {
        return problems.isEmpty();
    }

    /**
     * Checks opening + in - out = closing (reversed for cards, where the balance is the amount owing) and
     * that the extracted totals equal the printed ones. Checks needing a figure the layout lacks are skipped.
     */
    public static Reconciliation check(StatementSummary summary, List<Transaction> lines) {
        BigDecimal in = Money.ZERO;
        BigDecimal out = Money.ZERO;
        for (Transaction t : lines) {
            if (t.amount().signum() > 0) {
                in = in.add(t.amount());
            } else {
                out = out.subtract(t.amount());
            }
        }
        List<String> problems = new ArrayList<>();
        if (summary.openingBalance() != null && summary.closingBalance() != null) {
            BigDecimal net = in.subtract(out);
            BigDecimal expected = summary.accountType() == AccountType.CREDIT_CARD
                    ? summary.openingBalance().subtract(net)
                    : summary.openingBalance().add(net);
            if (expected.compareTo(summary.closingBalance()) != 0) {
                problems.add("opening " + summary.openingBalance() + " with lines gives " + expected
                        + " but the closing balance is " + summary.closingBalance());
            }
        }
        if (summary.printedMoneyIn() != null && summary.printedMoneyIn().compareTo(in) != 0) {
            problems.add("lines total " + in + " in but the statement prints " + summary.printedMoneyIn());
        }
        if (summary.printedMoneyOut() != null && summary.printedMoneyOut().compareTo(out) != 0) {
            problems.add("lines total " + out + " out but the statement prints " + summary.printedMoneyOut());
        }
        return new Reconciliation(summary.file(), in, out, problems);
    }
}

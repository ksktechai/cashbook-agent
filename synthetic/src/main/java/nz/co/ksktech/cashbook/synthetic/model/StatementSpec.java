package nz.co.ksktech.cashbook.synthetic.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import nz.co.ksktech.cashbook.core.AccountType;

/**
 * One synthetic statement file.
 *
 * @param file path under the input folder, e.g. "BusinessEveryday-05Apr27.pdf"
 * @param openingCents opening balance (for cards: amount owing; ignored by layouts without balances)
 * @param lines in chronological order; renderers reorder as their layout requires
 * @param flattenHighlights draw highlights into the page content instead of as annotations
 * @param layoutTraps layout-level traps, e.g. "END_OF_DAY_BALANCE_ROWS"
 */
public record StatementSpec(
        String file,
        Layout layout,
        String institution,
        AccountType accountType,
        String accountName,
        String accountNumber,
        LocalDate from,
        LocalDate to,
        long openingCents,
        List<SpecLine> lines,
        boolean flattenHighlights,
        Set<String> layoutTraps) {

    public StatementSpec {
        lines = List.copyOf(lines);
        layoutTraps = Set.copyOf(layoutTraps);
    }

    public long moneyInCents() {
        return lines.stream().mapToLong(SpecLine::cents).filter(c -> c > 0).sum();
    }

    public long moneyOutCents() {
        return -lines.stream().mapToLong(SpecLine::cents).filter(c -> c < 0).sum();
    }

    /** Closing balance; for cards the amount owing goes up with purchases. */
    public long closingCents() {
        long net = moneyInCents() - moneyOutCents();
        return accountType == AccountType.CREDIT_CARD ? openingCents - net : openingCents + net;
    }
}

package nz.co.ksktech.cashbook.synthetic.model;

import java.util.List;
import nz.co.ksktech.cashbook.core.Period;

/** Everything one seed produces, before rendering. */
public record SyntheticWorld(
        long seed,
        Period period,
        List<StatementSpec> statements,
        List<InvoiceSpec> invoices,
        List<LevySpec> levies,
        List<GoldenSpec> golden,
        List<SplitRow> split) {

    public SyntheticWorld {
        statements = List.copyOf(statements);
        invoices = List.copyOf(invoices);
        levies = List.copyOf(levies);
        golden = List.copyOf(golden);
        split = List.copyOf(split);
    }
}

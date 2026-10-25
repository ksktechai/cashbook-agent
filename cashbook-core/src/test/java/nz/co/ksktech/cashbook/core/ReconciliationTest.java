package nz.co.ksktech.cashbook.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReconciliationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);

    @ParameterizedTest(name = "{0} opening {1} closing {2} -> reconciled {3}")
    @CsvSource({
        // account: opening + in - out = closing
        "CHEQUE,      100.00,  130.00, true",
        "CHEQUE,      100.00,  130.01, false",
        "CHEQUE,     -500.00, -470.00, true",
        // card: the balance is the amount owing, so purchases raise it
        "CREDIT_CARD, 100.00,   70.00, true",
        "CREDIT_CARD, 100.00,  130.00, false"
    })
    void balances(AccountType type, String opening, String closing, boolean reconciled) {
        StatementSummary summary = summary(type, opening, closing, null, null);
        List<Transaction> lines = List.of(line(type, "50.00"), line(type, "-20.00"));

        assertThat(Reconciliation.check(summary, lines).reconciled()).isEqualTo(reconciled);
    }

    @ParameterizedTest(name = "printed in {0} out {1} -> reconciled {2}")
    @CsvSource({"50.00, 20.00, true", "50.00, 20.10, false", "49.00, 20.00, false"})
    void printedTotals(String in, String out, boolean reconciled) {
        StatementSummary summary = summary(AccountType.CREDIT_CARD, null, null, in, out);
        Reconciliation r = Reconciliation.check(summary, List.of(line(AccountType.CREDIT_CARD, "50.00"),
                line(AccountType.CREDIT_CARD, "-20.00")));

        assertThat(r.reconciled()).isEqualTo(reconciled);
        assertThat(r.moneyIn()).isEqualByComparingTo("50.00");
        assertThat(r.moneyOut()).isEqualByComparingTo("20.00");
    }

    private static StatementSummary summary(AccountType type, String opening, String closing, String in, String out) {
        return new StatementSummary("s.pdf", "TEST", "Test", type, "acct", "1", DAY, DAY, money(opening),
                money(closing), money(in), money(out), 1, false);
    }

    private static Transaction line(AccountType type, String amount) {
        return new Transaction(new SourceRef("s.pdf", 1, new BoundingBox(0, 0, 1, 1), "x"), "Test", type, DAY, DAY,
                "x", Money.of(amount), null, null, false);
    }

    private static java.math.BigDecimal money(String s) {
        return s == null ? null : Money.of(s);
    }
}

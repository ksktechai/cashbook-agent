package nz.co.ksktech.cashbook.core.reconcile;

import static nz.co.ksktech.cashbook.core.reconcile.Lines.coded;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import nz.co.ksktech.cashbook.core.AccountType;
import nz.co.ksktech.cashbook.core.CashbookLine;
import nz.co.ksktech.cashbook.core.GstTreatment;
import nz.co.ksktech.cashbook.core.Period;
import org.junit.jupiter.api.Test;

class ReconcilerTest {

    private static final Reconciler RECONCILER = new Reconciler(ReconcileOptions.defaults(Period.parse("2026-10:2027-03")),
            Map.of("726", "Entertainment", "729", "Car Parking", "734", "Home Office Expenses"));

    @Test
    void monthlyCodesAreSummedPerMonthWithTheirItems() {
        Reconciler.Result r = RECONCILER.reconcile(List.of(
                coded("a", "card.pdf", AccountType.CREDIT_CARD, "2026-10-03", "-25.00", "HARBOURVIEW BISTRO", "726"),
                coded("b", "card.pdf", AccountType.CREDIT_CARD, "2026-10-21", "-40.50", "THE LUNCH ROOM", "726"),
                coded("c", "card.pdf", AccountType.CREDIT_CARD, "2026-11-02", "-10.00", "WHARF ESPRESSO", "726")), List.of(), Map.of());

        assertThat(r.lines()).extracting(CashbookLine::month, l -> l.amount().toPlainString(), l -> l.sources().size())
                .containsExactly(org.assertj.core.groups.Tuple.tuple(YearMonth.of(2026, 10), "-65.50", 2),
                        org.assertj.core.groups.Tuple.tuple(YearMonth.of(2026, 11), "-10.00", 1));
        assertThat(r.items()).hasSize(3).allSatisfy(i -> assertThat(i.section()).isEqualTo("Entertainment"));
    }

    @Test
    void mixedChargeKeepsOnlyTheBusinessPortion() {
        Classified apple = Lines.line("a", "card.pdf", AccountType.CREDIT_CARD, "2026-10-03", "2026-10-03", "-54.98",
                "APPLE.COM/BILL", "734", GstTreatment.STANDARD, "mixedCharge", null, new BigDecimal("40.00"), false);

        CashbookLine line = RECONCILER.reconcile(List.of(apple), List.of(), Map.of()).lines().getFirst();

        assertThat(line.amount()).isEqualByComparingTo("-40.00");
        assertThat(line.detail()).contains("54.98 less 14.98 personal");
    }

    @Test
    void cutOffLinesAreFlaggedKeptByDefaultAndExcludedWhenAnswered() {
        Classified domain = Lines.line("d", "visa.pdf", AccountType.CREDIT_CARD, "2026-09-30", "2026-10-01", "-35.00",
                "DOMAINSNZ", "734", GstTreatment.STANDARD, null, null, null, false);
        Classified outside = Lines.line("o", "visa.pdf", AccountType.CREDIT_CARD, "2026-09-20", "2026-09-22", "-9.00",
                "OLD", "734", GstTreatment.STANDARD, null, null, null, false);

        Reconciler.Result kept = RECONCILER.reconcile(List.of(domain, outside), List.of(), Map.of());
        assertThat(kept.lines()).singleElement().extracting(CashbookLine::month).isEqualTo(YearMonth.of(2026, 10));
        assertThat(kept.flags()).singleElement().extracting(Flag::kind).isEqualTo(Flag.Kind.PERIOD_CUT_OFF);
        assertThat(kept.notes()).anySatisfy(n -> assertThat(n).contains("o is outside the period"));

        Reconciler.Result answered = RECONCILER.reconcile(List.of(domain), List.of(),
                Map.of(Flag.idFor("d", Flag.Kind.PERIOD_CUT_OFF), false));
        assertThat(answered.lines()).isEmpty();
        assertThat(answered.flags()).isEmpty();
    }

    @Test
    void monthFollowsTheCardDateAcrossAMonthEnd() {
        Classified paper = Lines.line("p", "visa.pdf", AccountType.CREDIT_CARD, "2026-12-31", "2027-01-04", "-24.50",
                "FAKEVILLE STATIONERY", "734", GstTreatment.STANDARD, null, null, null, false);

        assertThat(RECONCILER.reconcile(List.of(paper), List.of(), Map.of()).lines()).singleElement()
                .extracting(CashbookLine::month).isEqualTo(YearMonth.of(2026, 12));
    }

    @Test
    void highValuePurchasesAreFlaggedButIncomeServicesAndExemptLinesAreNot() {
        Reconciler.Result r = RECONCILER.reconcile(List.of(
                coded("laptop", "card.pdf", AccountType.CREDIT_CARD, "2026-12-12", "-2899.00", "TECHWORLD", "734"),
                coded("small", "card.pdf", AccountType.CREDIT_CARD, "2026-12-13", "-1150.00", "AT THRESHOLD", "734"),
                coded("income", "bank.pdf", AccountType.BUSINESS, "2026-12-15", "9000.00", "CLIENT", "707"),
                coded("levy", "chq.pdf", AccountType.CHEQUE, "2026-12-20", "-3148.47", "BODY CORP", "733"),
                Lines.line("rent", "bank.pdf", AccountType.BUSINESS, "2026-12-02", "2026-12-02", "-2400.00", "RENT", "724",
                        GstTreatment.EXEMPT, null, null, null, false)), List.of(), Map.of());

        assertThat(r.flags()).singleElement().satisfies(f -> {
            assertThat(f.lineId()).isEqualTo("laptop");
            assertThat(f.kind()).isEqualTo(Flag.Kind.HIGH_VALUE);
        });
        assertThat(r.lines()).hasSize(5);
    }

    @Test
    void pendingLinesAreLeftOutAndReported() {
        Classified pending = Lines.line("u", "card.pdf", AccountType.CREDIT_CARD, "2026-11-01", "2026-11-01", "-12.00",
                "UNKNOWN SHOP", null, GstTreatment.EXCLUDED, null, null, null, true);

        Reconciler.Result r = RECONCILER.reconcile(List.of(pending), List.of(), Map.of());

        assertThat(r.lines()).isEmpty();
        assertThat(r.unreviewed()).singleElement().asString().contains("UNKNOWN SHOP");
    }
}

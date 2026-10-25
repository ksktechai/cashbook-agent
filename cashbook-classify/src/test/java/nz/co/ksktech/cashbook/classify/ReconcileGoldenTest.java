package nz.co.ksktech.cashbook.classify;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import nz.co.ksktech.cashbook.core.CashbookLine;
import nz.co.ksktech.cashbook.core.Classification;
import nz.co.ksktech.cashbook.core.ClassificationMethod;
import nz.co.ksktech.cashbook.core.GstTreatment;
import nz.co.ksktech.cashbook.core.Money;
import nz.co.ksktech.cashbook.core.reconcile.Flag;
import nz.co.ksktech.cashbook.core.reconcile.ReconcileOptions;
import nz.co.ksktech.cashbook.core.reconcile.Reconciler;
import nz.co.ksktech.cashbook.core.rules.Rules;
import nz.co.ksktech.cashbook.synthetic.Scenario;
import nz.co.ksktech.cashbook.synthetic.SyntheticGenerator;
import nz.co.ksktech.cashbook.synthetic.model.SplitRow;
import org.junit.jupiter.api.Test;

/**
 * End to end on the synthetic period: rules only, a scripted reviewer answering the queue from the truth
 * file, then reconciliation. The cashbook must equal golden/cashbook.jsonl to the cent.
 */
class ReconcileGoldenTest {

    @Test
    void reconciledCashbookEqualsTheGolden() throws IOException {
        Rules rules = RulesLoader.load(RulesAndRedactionTest.EXAMPLE);
        ClassificationService.Result classified = new ClassificationService(rules, null, 0.8)
                .classify(SyntheticPeriod.transactions());
        List<ClassifiedLine> reviewed = classified.lines().stream().map(ReconcileGoldenTest::answerFromTruth).toList();
        Map<String, String> names = new HashMap<>();
        rules.codes().forEach((k, v) -> names.put(k, v.name()));

        Reconciler.Result result = new Reconciler(ReconcileOptions.defaults(Scenario.PERIOD), names)
                .reconcile(ReconcileBridge.toClassified(reviewed), SyntheticPeriod.invoices(), Map.of());

        assertThat(result.unreviewed()).isEmpty();
        assertThat(lineKeys(result.lines())).containsExactlyInAnyOrderElementsOf(goldenKeys());
        assertThat(result.flags()).extracting(Flag::kind)
                .containsExactlyInAnyOrder(Flag.Kind.PERIOD_CUT_OFF, Flag.Kind.HIGH_VALUE);
        List<SplitRow> expected = Scenario.build(42).split();
        assertThat(result.splits()).hasSameSizeAs(expected);
        for (int i = 0; i < expected.size(); i++) {
            assertThat(Money.toCents(result.splits().get(i).power725())).isEqualTo(expected.get(i).power725Cents());
            assertThat(Money.toCents(result.splits().get(i).internet())).isEqualTo(expected.get(i).internetCents());
            assertThat(result.splits().get(i).invoiceDate()).isEqualTo(expected.get(i).invoiceDate());
        }
        // every OtherCalculation section adds up to its codes' cashbook total
        Map<String, java.math.BigDecimal> bySection = result.items().stream().collect(Collectors.groupingBy(
                i -> i.section(), Collectors.reducing(Money.ZERO, i -> i.amount(), java.math.BigDecimal::add)));
        Map<String, String> sections = ReconcileOptions.defaults(Scenario.PERIOD).sections();
        bySection.forEach((section, total) -> {
            java.math.BigDecimal codes = result.lines().stream().filter(l -> section.equals(sections.get(l.code())))
                    .map(CashbookLine::amount).reduce(Money.ZERO, java.math.BigDecimal::add).negate();
            assertThat(total).as(section).isEqualByComparingTo(codes);
        });
    }

    /** The scripted reviewer: anything left for review gets the truth file's answer. */
    private static ClassifiedLine answerFromTruth(ClassifiedLine line) {
        if (line.review().isEmpty()) {
            return line;
        }
        JsonNode expected = SyntheticPeriod.truthFor(line.transaction()).get("expected");
        String code = expected.get("code").isNull() ? null : expected.get("code").asText();
        Classification human = new Classification(code, GstTreatment.valueOf(expected.get("gst").asText()), 1.0,
                ClassificationMethod.HUMAN, ReviewSession.DECIDED_BY, "scripted from the truth file");
        return new ClassifiedLine(line.id(), line.transaction(), human, line.special(), line.aggregate(),
                line.businessAmount(), List.of());
    }

    private static List<String> lineKeys(List<CashbookLine> lines) {
        return lines.stream().map(l -> l.month() + " " + l.code() + " " + l.amount().toPlainString()).toList();
    }

    private static List<String> goldenKeys() throws IOException {
        List<String> keys = new ArrayList<>();
        for (String line : Files.readAllLines(SyntheticPeriod.OUT.resolve(SyntheticGenerator.GOLDEN_JSONL))) {
            JsonNode n = ClassifyStore.MAPPER.readTree(line);
            keys.add(n.get("month").asText() + " " + n.get("code").asText() + " "
                    + Money.normalise(n.get("amount").decimalValue()).toPlainString());
        }
        return keys;
    }
}

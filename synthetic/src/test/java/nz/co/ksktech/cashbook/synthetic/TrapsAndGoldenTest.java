package nz.co.ksktech.cashbook.synthetic;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import nz.co.ksktech.cashbook.synthetic.model.Trap;
import org.junit.jupiter.api.Test;

/** The generated period contains every trap, and the golden lines account for every business line. */
class TrapsAndGoldenTest {

    private final List<JsonNode> truth = Generated.jsonl(SyntheticGenerator.TRUTH_TRANSACTIONS);
    private final List<JsonNode> golden = Generated.jsonl(SyntheticGenerator.GOLDEN_JSONL);

    @Test
    void everyTrapAppears() {
        Set<String> seen = truth.stream()
                .flatMap(t -> StreamSupport.stream(t.get("traps").spliterator(), false))
                .map(JsonNode::asText)
                .collect(Collectors.toSet());
        assertThat(seen).containsAll(Arrays.stream(Trap.values()).map(Enum::name).toList());
    }

    @Test
    void buildPromptTrapsAreConcrete() {
        assertThat(withTrap(Trap.CARD_REPAYMENT)).hasSizeGreaterThan(20);
        List<JsonNode> refund = withTrap(Trap.REFUND_NETS_TO_ZERO);
        assertThat(refund).hasSize(2);
        assertThat(refund.stream().map(r -> r.at("/transaction/amount").decimalValue()).reduce(BigDecimal.ZERO,
                BigDecimal::add)).isEqualByComparingTo("0");
        assertThat(withTrap(Trap.PERIOD_CUT_OFF)).singleElement()
                .satisfies(t -> assertThat(t.at("/transaction/transactionDate").asText()).isEqualTo("2026-09-30"));
        assertThat(withTrap(Trap.MIXED_CHARGE)).hasSize(6)
                .allSatisfy(t -> assertThat(t.at("/transaction/amount").decimalValue()).isEqualByComparingTo("-54.98"));
        assertThat(golden.stream().filter(g -> g.get("detail").asText().contains("iCloud")))
                .hasSize(6).allSatisfy(g -> assertThat(g.get("amount").decimalValue()).isEqualByComparingTo("-40.00"));
    }

    @Test
    void utilityPaymentsSplitExactlyIntoPowerAndInternet() {
        List<JsonNode> payments = withTrap(Trap.UTILITY_SPLIT);
        assertThat(payments).hasSize(6);
        for (JsonNode payment : payments) {
            String id = payment.get("id").asText();
            BigDecimal split = golden.stream()
                    .filter(g -> sources(g).contains(id))
                    .map(g -> g.get("amount").decimalValue())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(split).as(id).isEqualByComparingTo(payment.at("/transaction/amount").decimalValue());
        }
        List<JsonNode> documents = Generated.jsonl(SyntheticGenerator.TRUTH_DOCUMENTS);
        assertThat(documents.stream().filter(d -> d.get("kind").asText().equals("UTILITY_INVOICE"))).hasSize(7);
        assertThat(documents.stream().filter(d -> d.get("kind").asText().equals("LEVY_NOTICE"))).hasSize(2);
    }

    @Test
    void goldenCoversExactlyTheBusinessLines() {
        Set<String> coded = truth.stream()
                .filter(t -> !t.at("/expected/code").isNull())
                .map(t -> t.get("id").asText())
                .collect(Collectors.toSet());
        Set<String> referenced = new HashSet<>();
        golden.forEach(g -> referenced.addAll(sources(g)));
        assertThat(referenced).isEqualTo(coded);
    }

    @Test
    void nonBusinessAccountsOnlyCodeHighlightedLines() {
        for (JsonNode t : truth) {
            boolean business = t.at("/transaction/accountType").asText().equals("BUSINESS");
            if (!business && !t.at("/expected/code").isNull()) {
                assertThat(t.at("/transaction/highlighted").asBoolean()).as(t.get("id").asText()).isTrue();
            }
        }
    }

    @Test
    void goldenTotalsPerCodeAreStable() {
        Map<String, BigDecimal> byCode = golden.stream().collect(Collectors.groupingBy(g -> g.get("code").asText(),
                Collectors.reducing(BigDecimal.ZERO, g -> g.get("amount").decimalValue(), BigDecimal::add)));
        assertThat(byCode).containsOnlyKeys("707", "710", "711", "724", "725", "726", "727", "728", "729", "730",
                "732", "733", "734", "739");
        assertThat(byCode.get("724")).isEqualByComparingTo("-14400.00");
        assertThat(byCode.get("728")).isEqualByComparingTo("-390.00");
        assertThat(byCode.get("710")).isEqualByComparingTo("-25.00");
        assertThat(byCode.get("711")).isEqualByComparingTo("-1725.00");
        assertThat(byCode.get("732")).isEqualByComparingTo("-1240.00");
        assertThat(byCode.get("730")).isEqualByComparingTo("-289.20");
    }

    private List<JsonNode> withTrap(Trap trap) {
        return truth.stream()
                .filter(t -> StreamSupport.stream(t.get("traps").spliterator(), false)
                        .anyMatch(n -> n.asText().equals(trap.name())))
                .toList();
    }

    /** A golden line's source ids, recovered from its source refs via the truth file. */
    private Set<String> sources(JsonNode goldenLine) {
        Set<String> ids = new HashSet<>();
        for (JsonNode ref : goldenLine.get("sources")) {
            truth.stream()
                    .filter(t -> t.at("/transaction/source").equals(ref))
                    .forEach(t -> ids.add(t.get("id").asText()));
        }
        return ids;
    }
}

package nz.co.ksktech.cashbook.classify;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import nz.co.ksktech.cashbook.core.ClassificationMethod;
import nz.co.ksktech.cashbook.core.GstTreatment;
import org.junit.jupiter.api.Test;

/** Rules only (no LLM): every line the example rules cover gets the truth's code and GST treatment. */
class RulesOnlyTest {

    private final ClassificationService.Result result =
            new ClassificationService(RulesLoader.load(RulesAndRedactionTest.EXAMPLE), null, 0.8)
                    .classify(SyntheticPeriod.transactions());

    @Test
    void coveredLinesMatchTheTruthAndTheRestGoToReview() {
        List<String> wrong = new ArrayList<>();
        int covered = 0;
        int unknown = 0;
        for (ClassifiedLine line : result.lines()) {
            JsonNode truth = SyntheticPeriod.truthFor(line.transaction());
            assertThat(truth).as("truth for %s", line.transaction().description()).isNotNull();
            String expectedCode = truth.at("/expected/code").isNull() ? null : truth.at("/expected/code").asText();
            GstTreatment expectedGst = GstTreatment.valueOf(truth.at("/expected/gst").asText());
            boolean notInRules = SyntheticPeriod.NOT_IN_RULES.matcher(line.transaction().description()).find()
                    && line.transaction().highlighted();
            if (notInRules) {
                unknown++;
                assertThat(line.classification().decidedBy()).as(line.transaction().description())
                        .isEqualTo(ClassificationService.UNKNOWN);
                assertThat(line.review()).isNotEmpty();
                continue;
            }
            covered++;
            String got = line.classification().code();
            if (!java.util.Objects.equals(got, expectedCode) || line.classification().gst() != expectedGst) {
                wrong.add(truth.get("id").asText() + " " + line.transaction().description() + ": got " + got + "/"
                        + line.classification().gst() + ", expected " + expectedCode + "/" + expectedGst);
            }
            assertThat(line.classification().method()).isEqualTo(ClassificationMethod.RULE);
            assertThat(line.classification().confidence()).isEqualTo(1.0);
        }
        assertThat(wrong).as("rules disagree with the truth").isEmpty();
        assertThat(covered).isGreaterThan(300);
        assertThat(unknown).isGreaterThanOrEqualTo(10);
        assertThat(result.usage().calls()).isZero();
    }

    @Test
    void specialHandlersAndPolicyQuestionsAreCarried() {
        assertThat(result.lines()).filteredOn(l -> l.transaction().description().startsWith("FIBREWAVE"))
                .isNotEmpty().allSatisfy(l -> assertThat(l.special()).isEqualTo("utilitySplit"));
        assertThat(result.lines()).filteredOn(l -> l.transaction().description().startsWith("APPLE.COM"))
                .allSatisfy(l -> assertThat(l.businessAmount()).isEqualByComparingTo("40.00"));
        assertThat(result.lines()).filteredOn(l -> l.transaction().description().startsWith("Park on Quay"))
                .allSatisfy(l -> assertThat(l.aggregate()).isEqualTo("MONTHLY"));
        assertThat(result.review()).anySatisfy(r -> assertThat(r.item().question()).contains("cafe visit for business"));
    }
}

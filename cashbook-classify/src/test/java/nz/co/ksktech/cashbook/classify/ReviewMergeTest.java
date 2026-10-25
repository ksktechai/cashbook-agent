package nz.co.ksktech.cashbook.classify;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import nz.co.ksktech.cashbook.core.Classification;
import nz.co.ksktech.cashbook.core.ClassificationMethod;
import nz.co.ksktech.cashbook.core.GstTreatment;
import nz.co.ksktech.cashbook.core.ReviewItem;
import nz.co.ksktech.cashbook.core.rules.Rules;
import org.junit.jupiter.api.Test;

class ReviewMergeTest {

    @Test
    void answersAndFlagsSurviveAFreshClassifyRun() {
        Rules rules = RulesLoader.load(RulesAndRedactionTest.EXAMPLE);
        ClassificationService.Result first = new ClassificationService(rules, null, 0.8).classify(SyntheticPeriod.transactions());
        ReviewEntry toAnswer = first.review().get(0);
        ClassifiedLine answeredLine = first.lines().stream().filter(l -> l.id().equals(toAnswer.id())).findFirst().orElseThrow();

        // the person answered one item, and reconcile added a flag that is still open
        Classification human = new Classification("726", GstTreatment.STANDARD, 1.0, ClassificationMethod.HUMAN, "review", "chosen");
        List<ClassifiedLine> previous = new ArrayList<>(first.lines());
        previous.replaceAll(l -> l.id().equals(toAnswer.id())
                ? new ClassifiedLine(l.id(), l.transaction(), human, l.special(), l.aggregate(), l.businessAmount(), List.of()) : l);
        List<ReviewEntry> queue = new ArrayList<>(first.review());
        queue.set(0, new ReviewEntry(toAnswer.id(), new ReviewItem(toAnswer.item().transaction(), toAnswer.item().proposed(),
                toAnswer.item().question(), "chosen in review: 726")));
        ReviewEntry flag = new ReviewEntry(answeredLine.id() + ":HIGH_VALUE", new ReviewItem(answeredLine.transaction(), human,
                "Keep it?", null));
        queue.add(flag);

        ClassificationService.Result again = ReviewMerge.carryOver(
                new ClassificationService(rules, null, 0.8).classify(SyntheticPeriod.transactions()), previous, queue);

        ClassifiedLine kept = again.lines().stream().filter(l -> l.id().equals(toAnswer.id())).findFirst().orElseThrow();
        assertThat(kept.classification().method()).isEqualTo(ClassificationMethod.HUMAN);
        assertThat(kept.review()).isEmpty();
        assertThat(again.review()).filteredOn(e -> e.id().equals(toAnswer.id())).singleElement()
                .satisfies(e -> assertThat(e.answered()).isTrue());
        assertThat(again.review()).extracting(ReviewEntry::id).contains(flag.id()).doesNotHaveDuplicates();
        assertThat(again.review().stream().filter(e -> !e.answered()).count()).isEqualTo(first.review().size() - 1 + 1);
    }
}

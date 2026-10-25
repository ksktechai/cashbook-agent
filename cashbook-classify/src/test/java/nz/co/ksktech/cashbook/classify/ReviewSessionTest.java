package nz.co.ksktech.cashbook.classify;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import nz.co.ksktech.cashbook.core.ClassificationMethod;
import nz.co.ksktech.cashbook.core.GstTreatment;
import nz.co.ksktech.cashbook.core.rules.Rules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The review flow round-trips: answers become HUMAN classifications, progress resumes, rules are learned. */
class ReviewSessionTest {

    @Test
    void answersAreWrittenBackResumedAndLearned(@TempDir Path dir) throws IOException {
        Path rulesFile = dir.resolve("rules.yaml");
        Files.copy(RulesAndRedactionTest.EXAMPLE, rulesFile);
        Rules rules = RulesLoader.load(rulesFile);
        ClassificationService.Result result = new ClassificationService(rules, null, 0.8)
                .classify(SyntheticPeriod.transactions());
        ClassifyStore.write(dir, result.lines(), result.review(), result.usage());
        List<ReviewEntry> queue = ClassifyStore.readReview(dir);
        assertThat(queue).hasSizeGreaterThan(5);
        String firstMerchant = ReviewSession.merchant(queue.get(0).item().transaction().description());
        List<String> codes = new ArrayList<>(rules.codes().keySet());
        int entertainment = codes.indexOf("726") + 1;

        // item 1: choose 726 and save a rule; item 2: skip; item 3: exclude; then quit
        String script = String.join("\n", "c", Integer.toString(entertainment), "y", "s", "n", "q") + "\n";
        int answered = session(dir, rulesFile, rules, script).run();

        assertThat(answered).isEqualTo(2);
        List<ReviewEntry> after = ClassifyStore.readReview(dir);
        assertThat(after.get(0).item().answer()).startsWith("chosen in review: 726");
        assertThat(after.get(1).answered()).isFalse();
        assertThat(after.get(2).item().answer()).isEqualTo("excluded in review");
        ClassifiedLine chosen = ClassifyStore.readLines(dir).stream()
                .filter(l -> l.id().equals(after.get(0).id())).findFirst().orElseThrow();
        assertThat(chosen.classification().method()).isEqualTo(ClassificationMethod.HUMAN);
        assertThat(chosen.classification().code()).isEqualTo("726");
        assertThat(chosen.review()).isEmpty();
        ClassifiedLine excluded = ClassifyStore.readLines(dir).stream()
                .filter(l -> l.id().equals(after.get(2).id())).findFirst().orElseThrow();
        assertThat(excluded.classification().gst()).isEqualTo(GstTreatment.EXCLUDED);

        // a second session resumes at the skipped item
        ByteArrayOutputStream shown = new ByteArrayOutputStream();
        new ReviewSession(dir, rulesFile, rules, new BufferedReader(new StringReader("q\n")),
                new PrintStream(shown, true, StandardCharsets.UTF_8)).run();
        assertThat(shown.toString(StandardCharsets.UTF_8)).contains("already answered")
                .contains(after.get(1).item().transaction().description());

        // the learned rule now classifies that merchant without review
        Rules learned = RulesLoader.load(rulesFile);
        assertThat(learned.vendors()).last().satisfies(v -> {
            assertThat(v.id()).startsWith("learned[");
            assertThat(v.code()).isEqualTo("726");
        });
        ClassificationService.Result again = new ClassificationService(learned, null, 0.8)
                .classify(SyntheticPeriod.transactions());
        assertThat(again.lines()).filteredOn(l -> l.transaction().description().startsWith(firstMerchant)
                        && l.transaction().highlighted())
                .isNotEmpty()
                .allSatisfy(l -> assertThat(l.classification().decidedBy()).startsWith("learned["));
    }

    private static ReviewSession session(Path dir, Path rulesFile, Rules rules, String script) {
        return new ReviewSession(dir, rulesFile, rules, new BufferedReader(new StringReader(script)),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }
}

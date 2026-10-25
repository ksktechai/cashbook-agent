package nz.co.ksktech.cashbook.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainLauncher;
import io.quarkus.test.junit.main.QuarkusMainTest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@QuarkusMainTest
class CashbookCommandTest {

    @Test
    @Launch("--help")
    void helpListsRunCommand(LaunchResult result) {
        assertThat(result.getOutput()).contains("cashbook-agent", "run", "ingest", "classify", "review", "reconcile", "export", "eval",
                "synth");
    }

    @Test
    @Launch({"run", "--help"})
    void runHelpListsAllOptions(LaunchResult result) {
        assertThat(result.getOutput()).contains("--period", "--in", "--template", "--out");
    }

    @Test
    @Launch(value = {"run", "--period", "2027-03:2026-10", "--in", "in", "--template", "t.xlsx", "--out", "o.xlsx"},
            exitCode = 2)
    void reversedPeriodIsAUsageError(LaunchResult result) {
        assertThat(result.getErrorOutput()).contains("before start");
    }

    @Test
    @Launch(value = {"run", "--in", "in", "--template", "t.xlsx", "--out", "o.xlsx"}, exitCode = 2)
    void missingPeriodIsAUsageError(LaunchResult result) {
        assertThat(result.getErrorOutput()).contains("--period");
    }

    @Test
    void synthWritesTheGoldenFilesAndManifest(QuarkusMainLauncher launcher, @TempDir Path out) {
        LaunchResult result = launcher.launch("synth", "--seed", "42", "--out", out.toString());

        assertThat(result.exitCode()).isZero();
        assertThat(out.resolve("manifest.json")).exists();
        assertThat(out.resolve("golden/cashbook.xlsx")).exists();
        assertThat(out.resolve("golden/cashbook.jsonl")).exists();
        assertThat(out.resolve("template.xlsx")).exists();
        assertThat(out.resolve("in/BusinessEveryday-05Apr27.pdf")).exists();
    }

    @Test
    void ingestReadsTheSyntheticPeriod(QuarkusMainLauncher launcher, @TempDir Path synth, @TempDir Path out) {
        assertThat(launcher.launch("synth", "--seed", "42", "--out", synth.toString()).exitCode()).isZero();

        LaunchResult result = launcher.launch("ingest", "--in", synth.resolve("in").toString(), "--out", out.toString());

        assertThat(result.exitCode()).isZero();
        assertThat(out.resolve("transactions.jsonl")).isNotEmptyFile();
        assertThat(out.resolve("statements.jsonl")).isNotEmptyFile();
        assertThat(out.resolve("documents.jsonl")).isNotEmptyFile();

        Path classified = out.resolve("classified");
        LaunchResult classify = launcher.launch("classify", "--in", out.toString(), "--out", classified.toString(),
                "--rules", "../config/rules.example.yaml", "--llm", "none");
        assertThat(classify.exitCode()).isZero();
        assertThat(classified.resolve("classifications.jsonl")).isNotEmptyFile();
        assertThat(classified.resolve("review.jsonl")).isNotEmptyFile();

        // rules only leaves lines for review, so the cashbook is written but reported incomplete (exit 1)
        Path reconciled = out.resolve("reconciled");
        LaunchResult reconcile = launcher.launch("reconcile", "--classified", classified.toString(), "--ingest",
                out.toString(), "--period", "2026-10:2027-03", "--out", reconciled.toString(), "--rules",
                "../config/rules.example.yaml");
        assertThat(reconcile.exitCode()).isEqualTo(1);
        assertThat(reconciled.resolve("cashbook.jsonl")).isNotEmptyFile();
        assertThat(reconciled.resolve("split.jsonl")).isNotEmptyFile();
    }

    @Test
    void runStopsForReviewThenFinishesTheCashbook(QuarkusMainLauncher launcher, @TempDir Path dir) throws Exception {
        Path synth = dir.resolve("synth");
        assertThat(launcher.launch("synth", "--seed", "42", "--out", synth.toString()).exitCode()).isZero();
        Path out = dir.resolve("cashbook.xlsx");
        String[] run = {"run", "--period", "2026-10:2027-03", "--in", synth.resolve("in").toString(), "--template",
            synth.resolve("template.xlsx").toString(), "--out", out.toString(), "--rules", "../config/rules.example.yaml",
            "--llm", "none"};
        Path classified = dir.resolve("cashbook.xlsx.work").resolve("classify");

        // first run: unknown merchants need review
        assertThat(launcher.launch(run).exitCode()).isEqualTo(1);
        assertThat(out).doesNotExist();
        answerEverything(classified);
        // second run: the answers are kept; reconcile may raise flags (cut-off, high value)
        int second = launcher.launch(run).exitCode();
        if (second != 0) {
            answerEverything(classified);
            assertThat(launcher.launch(run).exitCode()).isZero();
        }
        assertThat(out).isNotEmptyFile();
    }

    @Test
    void evalScoresBothModesAndReportsAnUnreachableModel(QuarkusMainLauncher launcher, @TempDir Path dir) throws Exception {
        Path synth = dir.resolve("synth");
        assertThat(launcher.launch("synth", "--seed", "42", "--out", synth.toString()).exitCode()).isZero();
        Path out = dir.resolve("eval");
        LaunchResult result = launcher.launch("eval", "--golden", synth.resolve("golden/cashbook.jsonl").toString(),
                "--period", "2026-10:2027-03", "--in", synth.resolve("in").toString(), "--template",
                synth.resolve("template.xlsx").toString(), "--rules", "../config/rules.example.yaml", "--out", out.toString(),
                "--ollama-url", "http://127.0.0.1:9");
        assertThat(result.exitCode()).isZero();
        String report = Files.readString(out.resolve("report.md"));
        assertThat(report).contains("| | rules | llm |", "**llm** not run: no Ollama server reachable at http://127.0.0.1:9");
        assertThat(report).containsPattern("Known-vendor lines correct \\| (\\d+)/\\1 \\(100\\.0%\\)");
        assertThat(out.resolve("report.json")).isNotEmptyFile();
        assertThat(out.resolve("work/rules/cashbook.xlsx")).isNotEmptyFile();
        // the finished cashbook can be scored on its own
        assertThat(launcher.launch("eval", "--golden", synth.resolve("golden/cashbook.xlsx").toString(), "--period",
                "2026-10:2027-03", "--actual", out.resolve("work/rules/cashbook.xlsx").toString(), "--work",
                out.resolve("work/rules").toString(), "--out", dir.resolve("eval-actual").toString()).exitCode()).isZero();
        assertThat(Files.readString(dir.resolve("eval-actual/report.md"))).contains("| | actual |", "Statements reconciled | 6/6");
    }

    @Test
    @Launch(value = {"eval", "--golden", "private/golden/cashbook.xlsx", "--period", "2026-04:2026-09", "--actual", "x.xlsx",
        "--out", "eval"}, exitCode = 2)
    void aReportOnPrivateDataMustStayInPrivate(LaunchResult result) {
        assertThat(result.getErrorOutput()).contains("--out must be under private/");
    }

    /** Answers every open review item: excluded (a person would choose codes; any answer completes the run). */
    private static void answerEverything(Path classified) {
        java.util.List<nz.co.ksktech.cashbook.classify.ClassifiedLine> lines =
                nz.co.ksktech.cashbook.classify.ClassifyStore.readLines(classified);
        java.util.List<nz.co.ksktech.cashbook.classify.ReviewEntry> queue = new java.util.ArrayList<>();
        java.util.Set<String> answered = new java.util.HashSet<>();
        for (var e : nz.co.ksktech.cashbook.classify.ClassifyStore.readReview(classified)) {
            if (e.answered()) {
                queue.add(e);
                continue;
            }
            answered.add(e.id());
            queue.add(new nz.co.ksktech.cashbook.classify.ReviewEntry(e.id(), new nz.co.ksktech.cashbook.core.ReviewItem(
                    e.item().transaction(), e.item().proposed(), e.item().question(), "excluded in review")));
        }
        java.util.List<nz.co.ksktech.cashbook.classify.ClassifiedLine> updated = lines.stream().map(l -> answered.contains(l.id())
                ? new nz.co.ksktech.cashbook.classify.ClassifiedLine(l.id(), l.transaction(),
                        new nz.co.ksktech.cashbook.core.Classification(null, nz.co.ksktech.cashbook.core.GstTreatment.EXCLUDED,
                                1.0, nz.co.ksktech.cashbook.core.ClassificationMethod.HUMAN, "review", "excluded in review"),
                        l.special(), l.aggregate(), l.businessAmount(), java.util.List.of())
                : l).toList();
        nz.co.ksktech.cashbook.classify.ClassifyStore.writeLines(classified, updated, queue);
    }
}

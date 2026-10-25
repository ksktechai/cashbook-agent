package nz.co.ksktech.cashbook.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainLauncher;
import io.quarkus.test.junit.main.QuarkusMainTest;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@QuarkusMainTest
class CashbookCommandTest {

    @Test
    @Launch("--help")
    void helpListsRunCommand(LaunchResult result) {
        assertThat(result.getOutput()).contains("cashbook-agent", "run", "ingest", "classify", "review", "synth");
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
    }
}

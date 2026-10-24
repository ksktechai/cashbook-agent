package nz.co.ksktech.cashbook.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.Test;

@QuarkusMainTest
class CashbookCommandTest {

    @Test
    @Launch("--help")
    void helpListsRunCommand(LaunchResult result) {
        assertThat(result.getOutput()).contains("cashbook-agent", "run");
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
}

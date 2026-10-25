package nz.co.ksktech.cashbook.classify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import nz.co.ksktech.cashbook.core.AccountType;
import nz.co.ksktech.cashbook.core.GstTreatment;
import nz.co.ksktech.cashbook.core.rules.Rules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RulesAndRedactionTest {

    static final Path EXAMPLE = Path.of("../config/rules.example.yaml");

    @Test
    void exampleRulesLoad() {
        Rules rules = RulesLoader.load(EXAMPLE);

        assertThat(rules.codes()).containsKeys("707", "725", "739");
        assertThat(rules.codes().get("710").gst()).isEqualTo(GstTreatment.EXEMPT);
        assertThat(rules.policyFor(AccountType.BUSINESS)).isEqualTo(Rules.HighlightPolicy.ALL_LINES);
        assertThat(rules.vendors()).isNotEmpty().first().extracting(Rules.Vendor::id).isEqualTo("vendors[0]");
        assertThat(rules.excludes()).isNotEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "unknown top-level key      | codes: {'707': {name: Income, gst: STANDARD}}\\nvendorz: []                       | vendorz",
        "bad GST treatment          | codes: {'707': {name: Income, gst: ZERO}}                                         | /codes/707/gst",
        "vendor without a code      | codes: {'707': {name: Income, gst: STANDARD}}\\nvendors: [{match: x}]             | /vendors/0",
        "vendor code not defined    | codes: {'707': {name: Income, gst: STANDARD}}\\nvendors: [{match: x, code: '725'}] | vendors[0].code: 725",
        "regex that does not compile | codes: {'707': {name: Income, gst: STANDARD}}\\nvendors: [{match: '(', code: '707'}] | vendors[0].match"
    })
    void invalidRulesFailWithThePath(String name, String yaml, String expected, @TempDir Path dir) throws IOException {
        Path file = dir.resolve("rules.yaml");
        Files.writeString(file, yaml.replace("\\n", "\n"));

        assertThatThrownBy(() -> RulesLoader.load(file))
                .isInstanceOf(RulesLoader.InvalidRulesException.class)
                .hasMessageContaining(expected);
    }

    @Test
    void learnedRulesAreAppendedValidatedAndLoadedAfterTheMainOnes(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("rules.yaml");
        Files.writeString(file, "codes: {'726': {name: Entertainment, gst: STANDARD}}\nvendors: [{match: '(?i)bistro', code: '726'}]\n");

        RulesLoader.appendLearned(file, Map.of("match", "(?i)^THE LUNCH ROOM", "code", "726"));

        assertThat(RulesLoader.learnedFile(file)).hasFileName("rules.learned.yaml").exists();
        Rules rules = RulesLoader.load(file);
        assertThat(rules.vendors()).extracting(Rules.Vendor::id).containsExactly("vendors[0]", "learned[0]");
        assertThatThrownBy(() -> RulesLoader.appendLearned(file, Map.of("match", "x", "code", "726", "colour", "red")))
                .isInstanceOf(RulesLoader.InvalidRulesException.class);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "IRD 999-123-456 GST                          | IRD [REDACTED] GST",
        "Park on Quay 412345****** 4821 09552          | Park on Quay [REDACTED]",
        "PAYMENT TO 99-0123-0456790-000 rent           | PAYMENT TO [REDACTED] rent",
        "Card XXXX-XXXX-XXXX-7314 at 12 Sample Street  | Card [REDACTED] at [REDACTED]",
        "Unit 4B, Fakeville                            | [REDACTED] , Fakeville",
        "Tui Mobile TM 4400123                         | Tui Mobile TM [REDACTED]",
        "CLOUDNOTE.IO SAN FRANCISCO                    | CLOUDNOTE.IO SAN FRANCISCO"
    })
    void redaction(String input, String expected) {
        assertThat(Redactor.redact(input)).isEqualTo(expected);
    }

    @Test
    void merchantForLearnedRulesStopsAtNumbersAndMasks() {
        assertThat(ReviewSession.merchant("Park on Quay 412345****** 4821")).isEqualTo("Park on Quay");
        assertThat(ReviewSession.merchant("THE LUNCH ROOM")).isEqualTo("THE LUNCH ROOM");
        assertThat(ReviewSession.merchant("FAKEVILLE STATIONERY CO NZ LTD")).isEqualTo("FAKEVILLE STATIONERY CO NZ");
    }
}

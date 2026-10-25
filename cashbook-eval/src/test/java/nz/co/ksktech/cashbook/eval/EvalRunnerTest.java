package nz.co.ksktech.cashbook.eval;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import nz.co.ksktech.cashbook.classify.llm.OllamaClassifier;
import nz.co.ksktech.cashbook.synthetic.Scenario;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Rules-only and rules-plus-LLM runs on the synthetic period; WireMock stands in for Ollama. */
class EvalRunnerTest {

    private static final String MODEL = "qwen3:30b";
    private static final Path WORK = Path.of("build", "eval-work");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final WireMockServer OLLAMA = new WireMockServer(wireMockConfig().dynamicPort());
    private static ModeResult rules;
    private static ModeResult llm;

    @BeforeAll
    static void run() {
        OLLAMA.start();
        OLLAMA.stubFor(get(urlEqualTo("/api/version")).willReturn(json("{\"version\":\"0.40.11\"}")));
        reply("HARBOURVIEW BISTRO", "726", "STANDARD");
        reply("THE LUNCH ROOM", "726", "STANDARD");
        reply("WHARF ESPRESSO", "726", "STANDARD");
        reply("TECHWORLD", "734", "STANDARD");
        reply("OFFICE SUPPLY DEPOT", "NONE", "EXCLUDED");
        // two wrong answers: the truth is 734 for both
        reply("DOMAINSNZ", "739", "NO_GST");
        reply("FAKEVILLE STATIONERY", "715", "STANDARD");
        List<EvalLine> golden = SyntheticEval.golden();
        rules = EvalRunner.run("rules", SyntheticEval.in(), null, SyntheticEval.setup(), WORK.resolve("rules"), golden);
        llm = EvalRunner.run("llm", SyntheticEval.in(), new OllamaClassifier(OLLAMA.baseUrl(), MODEL, Duration.ofSeconds(5)),
                SyntheticEval.setup(), WORK.resolve("llm"), golden);
    }

    @AfterAll
    static void stop() {
        OLLAMA.stop();
    }

    @Test
    void rulesOnlyIsRightOnEveryKnownVendorLine() {
        Score s = rules.score();
        assertThat(s.ruleDecidedLines()).isGreaterThan(50);
        assertThat(s.ruleDecidedCorrect()).isEqualTo(s.ruleDecidedLines());
        // unknown merchants are left for a person, so lines are missing but none is wrong
        assertThat(s.wrongCode()).isZero();
        assertThat(s.missing()).isPositive();
        assertThat(rules.stats().pendingLines()).isPositive();
        assertThat(rules.stats().llmCalls()).isZero();
        assertThat(rules.stats().statementsReconciled()).isEqualTo(rules.stats().statements()).isPositive();
        assertThat(rules.stats().exportFailures()).isEmpty();
        assertThat(WORK.resolve("rules").resolve(EvalRunner.CASHBOOK)).exists();
    }

    @Test
    void theModelFillsGapsAndItsMistakesShowAsWrongCodes() {
        Score s = llm.score();
        assertThat(s.matched()).isGreaterThan(rules.score().matched());
        assertThat(s.wrongCode()).isEqualTo(2);
        assertThat(llm.matching().wrongCode()).extracting(p -> p.golden().code() + ">" + p.actual().code())
                .containsExactlyInAnyOrder("734>739", "734>715");
        assertThat(llm.stats().llmCalls()).isPositive();
        assertThat(llm.stats().llmInputTokens()).isPositive();
        assertThat(llm.stats().costNzd()).isZero();
        assertThat(llm.classifier()).isEqualTo("ollama:" + MODEL);
        // every suggestion goes to review by default, so the review load is reported, not hidden
        assertThat(llm.stats().reviewItems()).isGreaterThanOrEqualTo(llm.stats().llmCalls());
        // a wrong code changes the dollars per code and the GST claimed (739 has no GST)
        assertThat(s.codes()).filteredOn(c -> c.code().equals("739")).singleElement()
                .satisfies(c -> assertThat(c.precision()).isLessThan(1.0));
        assertThat(s.gstClaimedDiff()).isNegative();
    }

    @Test
    void theReportShowsBothModesSideBySideAndMatchesItsSchema() throws IOException {
        Path dir = WORK.resolve("report");
        EvalReport report = EvalReport.of(SyntheticEval.goldenJsonl(), Scenario.PERIOD,
                List.of(rules, llm, ModeResult.notRun("llm-offline", "ollama:" + MODEL, "no Ollama server reachable")));
        report.write(dir);
        String md = Files.readString(dir.resolve(EvalReport.MARKDOWN));
        assertThat(md).contains("| | rules | llm | llm-offline |", "| Classifier | rules only | ollama:qwen3:30b |",
                "## By code", "## llm: differences", "### Wrong code (2)", "| 2026-10 | −$35.00 | 734 | 739 |",
                "**llm-offline** not run: no Ollama server reachable", "$0.00 (local)");
        assertThat(md).doesNotContain("## llm-offline: differences");
        JsonNode json = JSON.readTree(dir.resolve(EvalReport.JSON_FILE).toFile());
        try (InputStream in = EvalReport.class.getResourceAsStream("/eval-report.schema.json")) {
            Schema schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema(JSON.readTree(in));
            assertThat(schema.validate(json)).isEmpty();
        }
        assertThat(json.at("/modes/1/score/wrongCode").asInt()).isEqualTo(2);
    }

    @Test
    void aFinishedCashbookIsComparedWithItsWorkFolder() {
        ModeResult actual = EvalRunner.compare(WORK.resolve("rules").resolve(EvalRunner.CASHBOOK), Scenario.PERIOD,
                SyntheticEval.EXPORT, SyntheticEval.golden(), WORK.resolve("rules"));
        assertThat(actual.score().matched()).isEqualTo(rules.score().matched());
        assertThat(actual.score().gstClaimedDiff()).isEqualByComparingTo(rules.score().gstClaimedDiff());
        assertThat(actual.stats().reviewItems()).isEqualTo(rules.stats().reviewItems() - flags(rules));
        assertThat(actual.stats().statementsReconciled()).isEqualTo(rules.stats().statementsReconciled());
    }

    // ---------------------------------------------------------------- helpers

    /** The eval run adds reconcile flags to its review count; the work folder's review.jsonl has only classify's. */
    private static int flags(ModeResult r) {
        return r.stats().reviewItems() - (int) count(WORK.resolve("rules").resolve(EvalRunner.CLASSIFY).resolve("review.jsonl"));
    }

    private static long count(Path file) {
        try (var lines = Files.lines(file)) {
            return lines.filter(l -> !l.isBlank()).count();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static void reply(String merchant, String code, String gst) {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", MODEL);
        body.put("created_at", "2026-10-26T00:00:00Z");
        body.putObject("message").put("role", "assistant").put("content",
                "{\"code\":\"" + code + "\",\"gst\":\"" + gst + "\",\"confidence\":0.9,\"rationale\":\"scripted\"}");
        body.put("done", true);
        body.put("done_reason", "stop");
        body.put("prompt_eval_count", 120);
        body.put("eval_count", 25);
        OLLAMA.stubFor(post(urlEqualTo("/api/chat")).withRequestBody(containing(merchant)).willReturn(json(body.toString())));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }
}

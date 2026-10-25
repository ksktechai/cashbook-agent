package nz.co.ksktech.cashbook.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nz.co.ksktech.cashbook.core.LevyNotice;
import nz.co.ksktech.cashbook.core.Money;
import nz.co.ksktech.cashbook.core.Transaction;
import nz.co.ksktech.cashbook.core.UtilityInvoice;
import nz.co.ksktech.cashbook.ingest.ocr.TesseractOcr;
import nz.co.ksktech.cashbook.synthetic.SyntheticGenerator;
import org.junit.jupiter.api.Test;

/** Ingests the seed-42 synthetic period and compares every line and figure with the generator's truth. */
class RoundTripTest {

    private static final Path OUT = Path.of("build", "synth-ingest", "seed-42");
    private static final boolean OCR = TesseractOcr.available() || System.getenv("CI") != null;
    private static final Ingestor.Result RESULT = ingest();

    private static Ingestor.Result ingest() {
        SyntheticGenerator.generate(42, OUT);
        IngestOptions defaults = IngestOptions.defaults();
        IngestOptions options = new IngestOptions(defaults.highlightThreshold(), defaults.businessAccount(), OCR,
                defaults.tessdata(), defaults.ocrDpi());
        return new Ingestor(options).ingest(OUT.resolve(SyntheticGenerator.IN));
    }

    @Test
    void everyStatementLineIsExtractedExactlyOnce() {
        Map<String, List<Transaction>> extracted = new HashMap<>();
        for (Transaction t : RESULT.transactions()) {
            extracted.computeIfAbsent(key(t), k -> new ArrayList<>()).add(t);
        }
        List<JsonNode> truth = jsonl(SyntheticGenerator.TRUTH_TRANSACTIONS);
        List<String> missing = new ArrayList<>();
        List<String> wrongHighlight = new ArrayList<>();
        for (JsonNode line : truth) {
            JsonNode t = line.get("transaction");
            List<Transaction> candidates = extracted.get(key(t));
            if (candidates == null || candidates.isEmpty()) {
                missing.add(line.get("id").asText() + " " + key(t));
                continue;
            }
            Transaction got = candidates.removeFirst();
            String id = line.get("id").asText();
            assertThat(got.processedDate().toString()).as("%s processed", id).isEqualTo(t.get("processedDate").asText());
            assertThat(got.fxFee()).as("%s fee", id).isEqualByComparingTo(t.get("fxFee").decimalValue());
            assertThat(got.source().page()).as("%s page", id).isEqualTo(t.at("/source/page").asInt());
            assertThat(got.accountType().name()).as("%s account type", id).isEqualTo(t.get("accountType").asText());
            if (got.highlighted() != t.get("highlighted").asBoolean()) {
                wrongHighlight.add(id + " expected " + t.get("highlighted").asBoolean() + " (" + line.get("highlightColour").asText() + ")");
            }
        }
        List<String> extra = extracted.values().stream().flatMap(List::stream).map(RoundTripTest::key).toList();
        assertThat(missing).as("truth lines not extracted; unmatched extracted lines: %s", extra).isEmpty();
        assertThat(wrongHighlight).as("highlight detection differs from the truth").isEmpty();
        assertThat(extra).as("extracted lines with no truth line").isEmpty();
        assertThat(RESULT.transactions()).hasSameSizeAs(truth);
    }

    @Test
    void everyStatementReconcilesAndMatchesTheTruth() {
        assertThat(RESULT.skipped()).isEmpty();
        assertThat(RESULT.statements()).hasSize(6);
        Map<String, JsonNode> truth = new HashMap<>();
        jsonl(SyntheticGenerator.TRUTH_STATEMENTS).forEach(s -> truth.put(s.get("file").asText(), s));
        for (Ingestor.StatementResult s : RESULT.statements()) {
            JsonNode expected = truth.get(s.summary().file());
            assertThat(s.reconciliation().problems()).as(s.summary().file()).isEmpty();
            assertThat(s.reconciliation().moneyIn()).isEqualByComparingTo(expected.get("moneyIn").decimalValue());
            assertThat(s.reconciliation().moneyOut()).isEqualByComparingTo(expected.get("moneyOut").decimalValue());
            if (s.summary().openingBalance() != null) {
                assertThat(s.summary().openingBalance()).as("%s opening", s.summary().file())
                        .isEqualByComparingTo(expected.get("openingBalance").decimalValue());
                assertThat(s.summary().closingBalance()).as("%s closing", s.summary().file())
                        .isEqualByComparingTo(expected.get("closingBalance").decimalValue());
            }
            assertThat(s.summary().from().toString()).isEqualTo(expected.get("from").asText());
            assertThat(s.summary().to().toString()).isEqualTo(expected.get("to").asText());
            assertThat(s.summary().accountType().name()).isEqualTo(expected.get("accountType").asText());
        }
    }

    @Test
    void utilityInvoicesMatchTheTruth() {
        List<JsonNode> truth = documents("UTILITY_INVOICE");
        assertThat(RESULT.invoices()).hasSameSizeAs(truth);
        for (JsonNode doc : truth) {
            JsonNode d = doc.get("document");
            UtilityInvoice got = RESULT.invoices().stream().filter(i -> i.file().equals(doc.get("file").asText()))
                    .findFirst().orElseThrow();
            long power = d.get("dailyCents").asLong() + d.get("usageCents").asLong() - d.get("powerDiscountCents").asLong();
            long broadband = d.get("broadbandPlanCents").asLong() - d.get("broadbandDiscountCents").asLong();
            long fee = d.get("cardFeeCents").asLong();
            long credit = d.get("creditCents").asLong();
            assertThat(got.invoiceNumber()).isEqualTo(d.get("invoiceNumber").asText());
            assertThat(got.invoiceDate().toString()).isEqualTo(d.get("invoiceDate").asText());
            assertThat(got.powerSubtotal()).isEqualByComparingTo(cents(power));
            assertThat(got.broadbandSubtotal()).isEqualByComparingTo(cents(broadband));
            assertThat(got.cardFee()).isEqualByComparingTo(cents(fee));
            assertThat(got.credit()).isEqualByComparingTo(cents(credit));
            assertThat(got.due()).isEqualByComparingTo(cents(power + broadband + fee - credit));
            assertThat(got.consistent()).isTrue();
        }
    }

    @Test
    void levyNoticesMatchTheTruthIncludingTheScannedOne() {
        List<JsonNode> truth = documents("LEVY_NOTICE");
        assumeThat(OCR).as("Tesseract is not installed; the scanned levy notice cannot be read").isTrue();
        assertThat(RESULT.levies()).hasSameSizeAs(truth);
        for (JsonNode doc : truth) {
            JsonNode d = doc.get("document");
            LevyNotice got = RESULT.levies().stream().filter(l -> l.file().equals(doc.get("file").asText()))
                    .findFirst().orElseThrow();
            long net = 0;
            long gst = 0;
            for (JsonNode line : d.get("lines")) {
                net += line.get("netCents").asLong();
                gst += line.get("gstCents").asLong();
            }
            long gross = net + gst;
            long discount = Money.toCents(cents(gross).multiply(BigDecimal.valueOf(d.get("discountPercent").asInt()))
                    .divide(BigDecimal.valueOf(100), 2, Money.ROUNDING));
            assertThat(got.ocr()).as("read by OCR").isEqualTo(d.get("scanned").asBoolean());
            assertThat(got.invoiceNumber()).isEqualTo(d.get("invoiceNumber").asText());
            assertThat(got.invoiceDate().toString()).isEqualTo(d.get("invoiceDate").asText());
            assertThat(got.net()).isEqualByComparingTo(cents(net));
            assertThat(got.gst()).isEqualByComparingTo(cents(gst));
            assertThat(got.gross()).isEqualByComparingTo(cents(gross));
            assertThat(got.discount()).isEqualByComparingTo(cents(discount));
            assertThat(got.netDue()).isEqualByComparingTo(cents(gross - discount));
            assertThat(got.consistent()).isTrue();
        }
    }

    @Test
    void writesJsonl(@org.junit.jupiter.api.io.TempDir Path dir) throws IOException {
        IngestWriter.write(RESULT, dir);
        assertThat(Files.readAllLines(dir.resolve(IngestWriter.TRANSACTIONS))).hasSize(RESULT.transactions().size());
        assertThat(Files.readAllLines(dir.resolve(IngestWriter.STATEMENTS))).hasSize(6);
        assertThat(Files.readAllLines(dir.resolve(IngestWriter.DOCUMENTS)))
                .hasSize(RESULT.invoices().size() + RESULT.levies().size());
    }

    // ---------------------------------------------------------------- helpers

    private static String key(Transaction t) {
        return String.join("|", t.source().file(), t.transactionDate().toString(), t.amount().toPlainString(),
                t.description(), t.foreign() == null ? "-" : t.foreign().amount().toPlainString() + t.foreign().currency());
    }

    private static String key(JsonNode t) {
        JsonNode f = t.get("foreign");
        return String.join("|", t.at("/source/file").asText(), t.get("transactionDate").asText(),
                Money.normalise(t.get("amount").decimalValue()).toPlainString(), t.get("description").asText(),
                f == null || f.isNull() ? "-"
                        : Money.normalise(f.get("amount").decimalValue()).toPlainString() + f.get("currency").asText());
    }

    private static BigDecimal cents(long c) {
        return Money.ofCents(c);
    }

    private static List<JsonNode> documents(String kind) {
        return jsonl(SyntheticGenerator.TRUTH_DOCUMENTS).stream().filter(d -> d.get("kind").asText().equals(kind)).toList();
    }

    private static List<JsonNode> jsonl(String relative) {
        try {
            List<JsonNode> out = new ArrayList<>();
            for (String line : Files.readAllLines(OUT.resolve(relative))) {
                out.add(IngestWriter.MAPPER.readTree(line));
            }
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

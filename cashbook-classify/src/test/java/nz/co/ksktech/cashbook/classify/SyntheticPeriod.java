package nz.co.ksktech.cashbook.classify;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import nz.co.ksktech.cashbook.core.Money;
import nz.co.ksktech.cashbook.core.Transaction;
import nz.co.ksktech.cashbook.ingest.IngestOptions;
import nz.co.ksktech.cashbook.ingest.Ingestor;
import nz.co.ksktech.cashbook.synthetic.SyntheticGenerator;

/** The seed-42 synthetic period, ingested (no OCR needed for statements), with its truth by line. */
final class SyntheticPeriod {

    static final Path OUT = Path.of("build", "synth-classify", "seed-42");
    private static List<Transaction> transactions;
    private static List<nz.co.ksktech.cashbook.core.UtilityInvoice> invoices;
    private static Map<String, JsonNode> truth;

    /** Merchants config/rules.example.yaml leaves out on purpose (and the refund pair, settled in M4). */
    static final Pattern NOT_IN_RULES = Pattern.compile(
            "HARBOURVIEW BISTRO|THE LUNCH ROOM|WHARF ESPRESSO|DOMAINSNZ|FAKEVILLE STATIONERY|TECHWORLD|OFFICE SUPPLY DEPOT");

    private SyntheticPeriod() {}

    static synchronized List<Transaction> transactions() {
        if (transactions == null) {
            SyntheticGenerator.generate(42, OUT);
            IngestOptions d = IngestOptions.defaults();
            Ingestor.Result r = new Ingestor(new IngestOptions(d.highlightThreshold(), d.businessAccount(), false, "", 300))
                    .ingest(OUT.resolve(SyntheticGenerator.IN));
            transactions = r.transactions();
            invoices = r.invoices();
        }
        return transactions;
    }

    static synchronized List<nz.co.ksktech.cashbook.core.UtilityInvoice> invoices() {
        transactions();
        return invoices;
    }

    /** Truth record for an extracted transaction, matched by file, date, amount and description. */
    static synchronized JsonNode truthFor(Transaction t) {
        if (truth == null) {
            transactions();
            truth = new HashMap<>();
            try {
                for (String line : Files.readAllLines(OUT.resolve(SyntheticGenerator.TRUTH_TRANSACTIONS))) {
                    JsonNode n = ClassifyStore.MAPPER.readTree(line);
                    JsonNode tx = n.get("transaction");
                    truth.put(key(tx.at("/source/file").asText(), tx.get("transactionDate").asText(),
                            Money.normalise(tx.get("amount").decimalValue()).toPlainString(),
                            tx.get("description").asText()), n);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return truth.get(key(t.source().file(), t.transactionDate().toString(), t.amount().toPlainString(),
                t.description()));
    }

    private static String key(String... parts) {
        return String.join("|", parts);
    }
}

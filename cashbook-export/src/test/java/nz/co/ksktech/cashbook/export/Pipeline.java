package nz.co.ksktech.cashbook.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nz.co.ksktech.cashbook.classify.ClassificationService;
import nz.co.ksktech.cashbook.classify.ClassifiedLine;
import nz.co.ksktech.cashbook.classify.ReconcileBridge;
import nz.co.ksktech.cashbook.classify.RulesLoader;
import nz.co.ksktech.cashbook.core.Classification;
import nz.co.ksktech.cashbook.core.ClassificationMethod;
import nz.co.ksktech.cashbook.core.GstTreatment;
import nz.co.ksktech.cashbook.core.Money;
import nz.co.ksktech.cashbook.core.Transaction;
import nz.co.ksktech.cashbook.core.reconcile.ReconcileOptions;
import nz.co.ksktech.cashbook.core.reconcile.Reconciler;
import nz.co.ksktech.cashbook.core.rules.Rules;
import nz.co.ksktech.cashbook.ingest.IngestOptions;
import nz.co.ksktech.cashbook.ingest.Ingestor;
import nz.co.ksktech.cashbook.synthetic.Scenario;
import nz.co.ksktech.cashbook.synthetic.SyntheticGenerator;

/** The synthetic period run through ingest, classify (rules + truth-scripted review) and reconcile. */
final class Pipeline {

    static final Path OUT = Path.of("build", "synth-export", "seed-42");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static ExportInput input;

    private Pipeline() {}

    static synchronized ExportInput reconciled() {
        if (input != null) {
            return input;
        }
        SyntheticGenerator.generate(42, OUT);
        IngestOptions d = IngestOptions.defaults();
        Ingestor.Result ingested = new Ingestor(new IngestOptions(d.highlightThreshold(), d.businessAccount(), false, "", 300))
                .ingest(OUT.resolve(SyntheticGenerator.IN));
        Rules rules = RulesLoader.load(Path.of("../config/rules.example.yaml"));
        List<ClassifiedLine> lines = new ClassificationService(rules, null, 0.8).classify(ingested.transactions()).lines();
        Map<String, JsonNode> truth = truth();
        List<ClassifiedLine> reviewed = lines.stream().map(l -> l.review().isEmpty() ? l : answer(l, truth)).toList();
        Map<String, String> names = new HashMap<>();
        rules.codes().forEach((k, v) -> names.put(k, v.name()));
        Reconciler.Result r = new Reconciler(ReconcileOptions.defaults(Scenario.PERIOD), names)
                .reconcile(ReconcileBridge.toClassified(reviewed), ingested.invoices(), Map.of());
        input = new ExportInput(r.lines(), r.items(), r.splits());
        return input;
    }

    static Path template() {
        return OUT.resolve(SyntheticGenerator.TEMPLATE);
    }

    static Path golden() {
        return OUT.resolve(SyntheticGenerator.GOLDEN_XLSX);
    }

    private static ClassifiedLine answer(ClassifiedLine line, Map<String, JsonNode> truth) {
        JsonNode e = truth.get(key(line.transaction())).get("expected");
        String code = e.get("code").isNull() ? null : e.get("code").asText();
        Classification human = new Classification(code, GstTreatment.valueOf(e.get("gst").asText()), 1.0,
                ClassificationMethod.HUMAN, "review", "scripted");
        return new ClassifiedLine(line.id(), line.transaction(), human, line.special(), line.aggregate(),
                line.businessAmount(), List.of());
    }

    private static Map<String, JsonNode> truth() {
        Map<String, JsonNode> map = new HashMap<>();
        try {
            for (String l : Files.readAllLines(OUT.resolve(SyntheticGenerator.TRUTH_TRANSACTIONS))) {
                JsonNode n = JSON.readTree(l);
                JsonNode t = n.get("transaction");
                map.put(String.join("|", t.at("/source/file").asText(), t.get("transactionDate").asText(),
                        Money.normalise(t.get("amount").decimalValue()).toPlainString(), t.get("description").asText()), n);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return map;
    }

    private static String key(Transaction t) {
        return String.join("|", t.source().file(), t.transactionDate().toString(), t.amount().toPlainString(),
                t.description());
    }
}

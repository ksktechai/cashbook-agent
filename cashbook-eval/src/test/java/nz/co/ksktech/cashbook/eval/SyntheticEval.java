package nz.co.ksktech.cashbook.eval;

import java.nio.file.Path;
import java.util.List;
import nz.co.ksktech.cashbook.classify.RulesLoader;
import nz.co.ksktech.cashbook.core.reconcile.ReconcileOptions;
import nz.co.ksktech.cashbook.export.ExportOptions;
import nz.co.ksktech.cashbook.ingest.IngestOptions;
import nz.co.ksktech.cashbook.synthetic.Scenario;
import nz.co.ksktech.cashbook.synthetic.SyntheticGenerator;

/** The seed-42 synthetic period and the setup an evaluation run uses for it. */
final class SyntheticEval {

    static final Path OUT = Path.of("build", "synth-eval", "seed-42");
    static final ExportOptions EXPORT = ExportOptions.defaults();
    private static boolean generated;

    private SyntheticEval() {}

    static synchronized Path dir() {
        if (!generated) {
            SyntheticGenerator.generate(42, OUT);
            generated = true;
        }
        return OUT;
    }

    static Path in() {
        return dir().resolve(SyntheticGenerator.IN);
    }

    static Path goldenJsonl() {
        return dir().resolve(SyntheticGenerator.GOLDEN_JSONL);
    }

    static Path goldenXlsx() {
        return dir().resolve(SyntheticGenerator.GOLDEN_XLSX);
    }

    static List<EvalLine> golden() {
        return Cashbooks.read(goldenJsonl(), Scenario.PERIOD, EXPORT);
    }

    static EvalRunner.Setup setup() {
        IngestOptions d = IngestOptions.defaults();
        return new EvalRunner.Setup(new IngestOptions(d.highlightThreshold(), d.businessAccount(), false, "", 300),
                RulesLoader.load(Path.of("../config/rules.example.yaml")), 0.8, true,
                ReconcileOptions.defaults(Scenario.PERIOD), EXPORT, dir().resolve(SyntheticGenerator.TEMPLATE));
    }
}

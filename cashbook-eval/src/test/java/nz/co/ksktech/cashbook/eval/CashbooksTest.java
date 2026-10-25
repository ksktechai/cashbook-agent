package nz.co.ksktech.cashbook.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import nz.co.ksktech.cashbook.synthetic.Scenario;
import org.junit.jupiter.api.Test;

class CashbooksTest {

    @Test
    void theGoldenXlsxAndJsonlReadAsTheSameCashbook() {
        List<EvalLine> jsonl = SyntheticEval.golden();
        List<EvalLine> xlsx = Cashbooks.read(SyntheticEval.goldenXlsx(), Scenario.PERIOD, SyntheticEval.EXPORT);
        assertThat(xlsx).isNotEmpty();
        assertThat(keys(xlsx)).containsExactlyInAnyOrderElementsOf(keys(jsonl));
        LineMatcher.Matching m = LineMatcher.match(jsonl, xlsx);
        Score s = Score.of(jsonl, xlsx, m);
        assertThat(s.accuracy()).isEqualTo(1.0);
        // the spreadsheet's own GST formulas agree with 3/23 of each STANDARD line
        assertThat(s.gstCollectedDiff()).isEqualByComparingTo("0");
        assertThat(s.gstClaimedDiff()).isEqualByComparingTo("0");
        assertThat(xlsx).allSatisfy(l -> assertThat(l.source()).startsWith("row "));
    }

    private static List<String> keys(List<EvalLine> lines) {
        return lines.stream().map(l -> l.month() + " " + l.code() + " " + l.amount().toPlainString()).toList();
    }
}

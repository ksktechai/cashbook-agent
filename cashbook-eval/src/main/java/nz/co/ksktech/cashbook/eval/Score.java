package nz.co.ksktech.cashbook.eval;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import nz.co.ksktech.cashbook.core.Money;

/**
 * The scores for one run against the golden cashbook.
 *
 * @param accuracy matched lines / golden lines (0-1)
 * @param ruleDecidedLines actual lines whose every transaction a rule settled (known vendors)
 * @param ruleDecidedCorrect how many of those matched a golden line
 * @param gstCollectedDiff actual minus golden; {@code gstClaimedDiff} likewise, as positive amounts
 */
public record Score(
        int goldenLines,
        int actualLines,
        int matched,
        int wrongCode,
        int missing,
        int extra,
        double accuracy,
        int ruleDecidedLines,
        int ruleDecidedCorrect,
        BigDecimal goldenGstCollected,
        BigDecimal actualGstCollected,
        BigDecimal gstCollectedDiff,
        BigDecimal goldenGstClaimed,
        BigDecimal actualGstClaimed,
        BigDecimal gstClaimedDiff,
        boolean gstWithinTolerance,
        List<CodeScore> codes) {

    /** GST collected and claimed must each be within this of the golden cashbook (phase 1 exit criterion). */
    public static final BigDecimal GST_TOLERANCE = new BigDecimal("1.00");

    /**
     * @param precision null when the run produced no line with this code
     * @param recall null when the golden cashbook has no line with this code
     */
    public record CodeScore(String code, int goldenLines, int actualLines, int matched, Double precision, Double recall,
            BigDecimal goldenAmount, BigDecimal actualAmount, BigDecimal difference) {}

    public static Score of(List<EvalLine> golden, List<EvalLine> actual, LineMatcher.Matching m) {
        TreeSet<String> codes = new TreeSet<>();
        golden.forEach(l -> codes.add(l.code()));
        actual.forEach(l -> codes.add(l.code()));
        Map<String, Integer> matchedByCode = new TreeMap<>();
        m.matched().forEach(p -> matchedByCode.merge(p.golden().code(), 1, Integer::sum));
        List<CodeScore> perCode = codes.stream().map(code -> {
            int g = (int) golden.stream().filter(l -> l.code().equals(code)).count();
            int a = (int) actual.stream().filter(l -> l.code().equals(code)).count();
            int ok = matchedByCode.getOrDefault(code, 0);
            BigDecimal ga = sum(golden, code);
            BigDecimal aa = sum(actual, code);
            return new CodeScore(code, g, a, ok, a == 0 ? null : (double) ok / a, g == 0 ? null : (double) ok / g, ga, aa,
                    aa.subtract(ga));
        }).toList();
        int ruleLines = (int) actual.stream().filter(EvalLine::ruleDecided).count();
        int ruleCorrect = (int) m.matched().stream().filter(p -> p.actual().ruleDecided()).count();
        BigDecimal gc = total(golden, true);
        BigDecimal ac = total(actual, true);
        BigDecimal gcl = total(golden, false);
        BigDecimal acl = total(actual, false);
        BigDecimal dc = ac.subtract(gc);
        BigDecimal dcl = acl.subtract(gcl);
        return new Score(golden.size(), actual.size(), m.matched().size(), m.wrongCode().size(), m.missing().size(),
                m.extra().size(), golden.isEmpty() ? 0 : (double) m.matched().size() / golden.size(), ruleLines,
                ruleCorrect, gc, ac, dc, gcl, acl, dcl,
                dc.abs().compareTo(GST_TOLERANCE) <= 0 && dcl.abs().compareTo(GST_TOLERANCE) <= 0, perCode);
    }

    private static BigDecimal sum(List<EvalLine> lines, String code) {
        return lines.stream().filter(l -> l.code().equals(code)).map(EvalLine::amount).reduce(Money.ZERO, BigDecimal::add);
    }

    /** GST collected, or GST claimed as a positive amount, rounded once like the spreadsheet's totals. */
    private static BigDecimal total(List<EvalLine> lines, boolean collected) {
        BigDecimal t = lines.stream().map(l -> collected ? l.gstCollected() : l.gstClaimed().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return t.setScale(Money.SCALE, RoundingMode.HALF_UP);
    }
}

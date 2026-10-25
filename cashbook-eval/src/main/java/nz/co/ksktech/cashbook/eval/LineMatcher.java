package nz.co.ksktech.cashbook.eval;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pairs golden and actual lines. First pass: same month, same code, amount within a cent. Second pass, on
 * what is left: same month and amount with a different code, which is a misclassification. Anything still
 * unpaired is missing (golden only) or extra (actual only). Among candidates the closest amount, then the
 * closest date, wins.
 */
public final class LineMatcher {

    static final BigDecimal TOLERANCE = new BigDecimal("0.01");
    private static final long NO_DATE = Long.MAX_VALUE / 2;

    /** A golden line and the actual line it was paired with. */
    public record Pair(EvalLine golden, EvalLine actual) {}

    public record Matching(List<Pair> matched, List<Pair> wrongCode, List<EvalLine> missing, List<EvalLine> extra) {}

    private LineMatcher() {}

    public static Matching match(List<EvalLine> golden, List<EvalLine> actual) {
        List<EvalLine> unmatchedGolden = new ArrayList<>(golden);
        List<EvalLine> unmatchedActual = new ArrayList<>(actual);
        List<Pair> matched = pass(unmatchedGolden, unmatchedActual, true);
        List<Pair> wrongCode = pass(unmatchedGolden, unmatchedActual, false);
        return new Matching(matched, wrongCode, unmatchedGolden, unmatchedActual);
    }

    /** Pairs and removes lines from both lists. */
    private static List<Pair> pass(List<EvalLine> golden, List<EvalLine> actual, boolean sameCode) {
        List<Pair> pairs = new ArrayList<>();
        for (EvalLine g : List.copyOf(golden)) {
            actual.stream()
                    .filter(a -> a.month().equals(g.month()) && a.code().equals(g.code()) == sameCode
                            && a.amount().subtract(g.amount()).abs().compareTo(TOLERANCE) <= 0)
                    .min(Comparator.<EvalLine, BigDecimal>comparing(a -> a.amount().subtract(g.amount()).abs())
                            .thenComparingLong(a -> days(a, g)))
                    .ifPresent(a -> {
                        pairs.add(new Pair(g, a));
                        golden.remove(g);
                        actual.remove(a);
                    });
        }
        return pairs;
    }

    private static long days(EvalLine a, EvalLine b) {
        return a.date() == null || b.date() == null ? NO_DATE : Math.abs(ChronoUnit.DAYS.between(a.date(), b.date()));
    }
}

package nz.co.ksktech.cashbook.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class LineMatcherTest {

    private static final YearMonth OCT = YearMonth.of(2026, 10);
    private static final YearMonth NOV = YearMonth.of(2026, 11);

    @Test
    void matchesOnMonthCodeAndAmountWithinACent() {
        LineMatcher.Matching m = LineMatcher.match(
                List.of(line(OCT, 3, "734", "-35.00"), line(OCT, 9, "726", "-41.00")),
                List.of(line(OCT, 3, "734", "-35.01"), line(OCT, 9, "726", "-41.02")));
        assertThat(m.matched()).hasSize(1);
        assertThat(m.matched().getFirst().golden().code()).isEqualTo("734");
        // 2 cents off is not a match, nor a wrong code: it is missing and extra
        assertThat(m.wrongCode()).isEmpty();
        assertThat(m.missing()).extracting(EvalLine::code).containsExactly("726");
        assertThat(m.extra()).extracting(EvalLine::amount).containsExactly(new BigDecimal("-41.02"));
    }

    @Test
    void sameAmountWithAnotherCodeIsAMisclassification() {
        LineMatcher.Matching m = LineMatcher.match(List.of(line(OCT, 3, "734", "-35.00")),
                List.of(line(OCT, 3, "739", "-35.00")));
        assertThat(m.matched()).isEmpty();
        assertThat(m.wrongCode()).singleElement()
                .satisfies(p -> assertThat(p.golden().code() + ">" + p.actual().code()).isEqualTo("734>739"));
        assertThat(m.missing()).isEmpty();
        assertThat(m.extra()).isEmpty();
    }

    @Test
    void theMonthMustAgree() {
        LineMatcher.Matching m = LineMatcher.match(List.of(line(OCT, 30, "734", "-35.00")),
                List.of(line(NOV, 1, "734", "-35.00")));
        assertThat(m.missing()).hasSize(1);
        assertThat(m.extra()).hasSize(1);
    }

    @Test
    void equalAmountsPairByNearestDateAndAnExactCodeWinsOverAWrongOne() {
        LineMatcher.Matching m = LineMatcher.match(
                List.of(line(OCT, 2, "726", "-20.00"), line(OCT, 20, "726", "-20.00"), line(OCT, 25, "734", "-20.00")),
                List.of(line(OCT, 21, "726", "-20.00"), line(OCT, 3, "726", "-20.00"), line(OCT, 25, "726", "-20.00")));
        assertThat(m.matched()).extracting(p -> p.golden().date().getDayOfMonth() + ">" + p.actual().date().getDayOfMonth())
                .containsExactly("2>3", "20>21");
        assertThat(m.wrongCode()).extracting(p -> p.golden().code() + ">" + p.actual().code()).containsExactly("734>726");
    }

    @Test
    void scoresPrecisionRecallDollarsAndGst() {
        List<EvalLine> golden = List.of(line(OCT, 1, "734", "-23.00"), line(OCT, 2, "734", "-46.00"),
                line(OCT, 3, "707", "230.00"));
        List<EvalLine> actual = List.of(line(OCT, 1, "734", "-23.00"), line(OCT, 2, "739", "-46.00"),
                line(OCT, 3, "707", "230.00"), line(OCT, 4, "710", "-5.00"));
        Score s = Score.of(golden, actual, LineMatcher.match(golden, actual));
        assertThat(s.matched()).isEqualTo(2);
        assertThat(s.accuracy()).isEqualTo(2.0 / 3);
        Score.CodeScore c734 = s.codes().stream().filter(c -> c.code().equals("734")).findFirst().orElseThrow();
        assertThat(c734.precision()).isEqualTo(1.0);
        assertThat(c734.recall()).isEqualTo(0.5);
        assertThat(c734.difference()).isEqualByComparingTo("46.00");
        Score.CodeScore c739 = s.codes().stream().filter(c -> c.code().equals("739")).findFirst().orElseThrow();
        assertThat(c739.precision()).isEqualTo(0.0);
        assertThat(c739.recall()).isNull();
        assertThat(s.goldenGstCollected()).isEqualByComparingTo("30.00");
        assertThat(s.goldenGstClaimed()).isEqualByComparingTo("9.00");
        assertThat(s.actualGstClaimed()).isEqualByComparingTo("9.65");
        assertThat(s.gstWithinTolerance()).isTrue();
    }

    private static EvalLine line(YearMonth month, int day, String code, String amount) {
        BigDecimal a = new BigDecimal(amount);
        BigDecimal gst = a.multiply(BigDecimal.valueOf(3)).divide(BigDecimal.valueOf(23), 4, java.math.RoundingMode.HALF_UP);
        BigDecimal zero = BigDecimal.ZERO.setScale(4);
        return new EvalLine(month, LocalDate.of(month.getYear(), month.getMonth(), day), code, "line", a,
                gst.signum() > 0 ? gst : zero, gst.signum() < 0 ? gst : zero, "test", false);
    }
}

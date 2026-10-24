package nz.co.ksktech.cashbook.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class PeriodTest {

    @Test
    void parsesSixMonthPeriodAcrossYearEnd() {
        Period period = Period.parse("2026-10:2027-03");

        assertThat(period.start()).isEqualTo(YearMonth.of(2026, 10));
        assertThat(period.end()).isEqualTo(YearMonth.of(2027, 3));
        assertThat(period.firstDay()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(period.lastDay()).isEqualTo(LocalDate.of(2027, 3, 31));
        assertThat(period).hasToString("2026-10:2027-03");
    }

    @Test
    void containsIsInclusiveAndExcludesDayBeforeStart() {
        Period period = Period.parse("2026-10:2027-03");

        assertThat(period.contains(LocalDate.of(2026, 9, 30))).isFalse();
        assertThat(period.contains(LocalDate.of(2026, 10, 1))).isTrue();
        assertThat(period.contains(LocalDate.of(2027, 3, 31))).isTrue();
        assertThat(period.contains(LocalDate.of(2027, 4, 1))).isFalse();
    }

    @Test
    void rejectsReversedRange() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Period.parse("2027-03:2026-10"))
                .withMessageContaining("before start");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "2026-10", "2026-10:2027-03:2027-04", "2026-13:2027-03", "Oct-2026:Mar-2027"})
    void rejectsMalformedInput(String text) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Period.parse(text))
                .withMessageContaining("YYYY-MM:YYYY-MM");
    }
}

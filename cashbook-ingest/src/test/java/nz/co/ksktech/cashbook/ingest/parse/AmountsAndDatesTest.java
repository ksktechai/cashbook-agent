package nz.co.ksktech.cashbook.ingest.parse;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AmountsAndDatesTest {

    @ParameterizedTest(name = "''{0}'' -> {1} credit={2} currency={3}")
    @CsvSource(delimiter = '|', value = {
        "$1,234.56       | 1234.56  | false | ",
        "- $1,234.56     | -1234.56 | false | ",
        "CR $236.74      | 236.74   | true  | ",
        "– $55.00   | -55.00   | false | ",
        "+ $600.00       | 600.00   | false | ",
        "-$12.34 NZD     | -12.34   | false | NZD",
        "$20.00 USD      | 20.00    | false | USD",
        "(credit) $25.00 | 25.00    | true  | ",
        "1,234.56*       | 1234.56  | false | ",
        "$ 3,073.95      | 3073.95  | false | ",
        "4140.00         | 4140.00  | false | "
    })
    void amounts(String text, String value, boolean credit, String currency) {
        assertThat(Amounts.parse(text)).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo(value));
        assertThat(Amounts.isCredit(text)).isEqualTo(credit);
        assertThat(Amounts.currency(text).orElse(null)).isEqualTo(currency);
    }

    @ParameterizedTest(name = "''{0}'' -> {1}")
    @CsvSource(delimiter = '|', value = {
        "5 Apr 2027      | 2027-04-05",
        "1 October 2026  | 2026-10-01",
        "30/09/2026      | 2026-09-30",
        "01/10/26        | 2026-10-01",
        "2027-03-31      | 2027-03-31"
    })
    void dates(String text, String expected) {
        assertThat(Dates.parse(text)).contains(LocalDate.parse(expected));
    }

    @ParameterizedTest(name = "''{0}'' within Oct 2026 - Mar 2027 -> {1}")
    @CsvSource(delimiter = '|', value = {"9 Oct 14:32 | 2026-10-09", "12 Jan 18:42 | 2027-01-12", "31 Mar | 2027-03-31"})
    void datesWithoutYearTakeTheYearInsideThePeriod(String text, String expected) {
        assertThat(Dates.parseWithin(text, LocalDate.of(2026, 10, 1), LocalDate.of(2027, 3, 31)))
                .contains(LocalDate.parse(expected));
    }
}

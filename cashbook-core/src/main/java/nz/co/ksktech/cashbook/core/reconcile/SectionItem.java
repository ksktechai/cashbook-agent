package nz.co.ksktech.cashbook.core.reconcile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * One itemised row for the OtherCalculation sheet.
 *
 * @param amount the cost as a positive number
 * @param note where it came from, e.g. the source file
 */
public record SectionItem(YearMonth month, String section, String code, LocalDate date, String description,
        BigDecimal amount, String note) {}

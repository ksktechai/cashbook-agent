package nz.co.ksktech.cashbook.synthetic.model;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import nz.co.ksktech.cashbook.core.GstTreatment;

/**
 * One expected cashbook line, plus its itemised detail for the OtherCalculation sheet.
 *
 * @param cents signed bank amount, negative = expense
 * @param sourceIds ids of the {@link SpecLine}s it comes from
 * @param items itemised detail shown in {@code section}, empty when the section is NONE
 */
public record GoldenSpec(
        YearMonth month,
        String code,
        String detail,
        long cents,
        GstTreatment gst,
        List<String> sourceIds,
        Section section,
        List<Item> items) {

    /** The OtherCalculation section a line is itemised in. */
    public enum Section {
        NONE,
        ENTERTAINMENT,
        INSURANCE,
        NO_GST_SUBSCRIPTIONS,
        CAR_PARKS,
        BUSINESS_EXPENSES
    }

    /** One itemised row; cents are positive costs. */
    public record Item(LocalDate date, String description, long cents, String note) {}

    public GoldenSpec {
        sourceIds = List.copyOf(sourceIds);
        items = List.copyOf(items);
    }
}

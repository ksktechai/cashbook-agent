package nz.co.ksktech.cashbook.export;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Names and orderings the export needs that belong to the template, not to the code.
 *
 * @param rowOrder codes in the order lines are written within a month block (others follow, by code)
 * @param sectionOrder OtherCalculation sections written after the entertainment rows, in order
 * @param monthlySubtotalSections sections itemised per month with a subtotal each
 * @param noGstSections sections headed "NO GST"
 * @param sectionCodes section -> the codes whose Cashbook totals it must equal
 */
public record ExportOptions(
        String cashbookSheet,
        String cashPositionSheet,
        String otherSheet,
        String entertainmentCode,
        String entertainmentLabel,
        String splitTitle,
        List<String> rowOrder,
        List<String> sectionOrder,
        Set<String> monthlySubtotalSections,
        Set<String> noGstSections,
        Map<String, Set<String>> sectionCodes) {

    public ExportOptions {
        rowOrder = List.copyOf(rowOrder);
        sectionOrder = List.copyOf(sectionOrder);
        monthlySubtotalSections = Set.copyOf(monthlySubtotalSections);
        noGstSections = Set.copyOf(noGstSections);
        sectionCodes = Map.copyOf(sectionCodes);
    }

    /** The accountant's template as seen in the redacted probe. */
    public static ExportOptions defaults() {
        return new ExportOptions("Cashbook", "Cash Pos", "OtherCalculation", "726", "Business lunch/coffee",
                "Utility - Power / Internet split",
                List.of("707", "725", "727", "728", "729", "734", "724", "710", "711", "733", "739", "730", "732"),
                List.of("Insurance", "Internet Subscriptions", "Car Park Fees", "Business Expenses"),
                Set.of("Internet Subscriptions", "Car Park Fees", "Business Expenses"),
                Set.of("Internet Subscriptions"),
                Map.of("Entertainment", Set.of("726"), "Insurance", Set.of("730", "732"),
                        "Internet Subscriptions", Set.of("739"), "Car Park Fees", Set.of("729"),
                        "Business Expenses", Set.of("734")));
    }
}

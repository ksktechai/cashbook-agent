package nz.co.ksktech.cashbook.core.reconcile;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import nz.co.ksktech.cashbook.core.Period;

/**
 * Settings for reconciliation.
 *
 * @param capitaliseThresholdExclGst flag purchases above this (GST-exclusive), e.g. 1000.00
 * @param capitaliseCodes codes whose purchases can be assets (equipment, not services, levies or insurance)
 * @param monthlyCodes codes summed to one cashbook line per month (e.g. entertainment, car parks)
 * @param sections code -> OtherCalculation section name, for itemised detail
 * @param repaymentWindowDays how far apart a bank repayment and the card's "payment received" may be
 */
public record ReconcileOptions(
        Period period,
        BigDecimal capitaliseThresholdExclGst,
        Set<String> capitaliseCodes,
        Set<String> monthlyCodes,
        Map<String, String> sections,
        int repaymentWindowDays) {

    public ReconcileOptions {
        capitaliseCodes = Set.copyOf(capitaliseCodes);
        monthlyCodes = Set.copyOf(monthlyCodes);
        sections = Map.copyOf(sections);
    }

    /** The defaults agreed for phase 1. */
    public static ReconcileOptions defaults(Period period) {
        return new ReconcileOptions(period, new BigDecimal("1000.00"), Set.of("734", "709"), Set.of("726", "729"),
                Map.of("726", "Entertainment", "730", "Insurance", "732", "Insurance", "739", "Internet Subscriptions",
                        "729", "Car Park Fees", "734", "Business Expenses"),
                3);
    }
}

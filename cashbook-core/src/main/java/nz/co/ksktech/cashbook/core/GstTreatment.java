package nz.co.ksktech.cashbook.core;

/** How GST applies to a cashbook line. */
public enum GstTreatment {
    /** GST at the standard rate, 3/23 of the GST-inclusive amount. */
    STANDARD,
    /** Exempt supply, e.g. bank fees or residential rent. */
    EXEMPT,
    /** No GST charged, e.g. offshore subscriptions. */
    NO_GST,
    /** Not a cashbook line at all, e.g. transfers and card repayments. */
    EXCLUDED
}

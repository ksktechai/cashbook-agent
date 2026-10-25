package nz.co.ksktech.cashbook.synthetic.truth;

/**
 * Ground truth for a supplier document (utility invoice or levy notice).
 *
 * @param kind "UTILITY_INVOICE" or "LEVY_NOTICE"
 * @param document the generator's spec for it, with every figure printed on it
 */
public record DocumentTruth(String file, String kind, int pages, Object document) {}

package nz.co.ksktech.cashbook.synthetic.model;

/**
 * Original-currency details of a foreign charge.
 *
 * @param rate conversion rate as printed, e.g. "0.5812" (NZD 1 = USD 0.5812)
 * @param currencyName as printed on card statements, e.g. "US DOLLAR"
 */
public record ForeignSpec(long foreignCents, String currency, String rate, String currencyName) {}

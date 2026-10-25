package nz.co.ksktech.cashbook.core;

/** The kind of account a statement line came from; drives the highlight policy. */
public enum AccountType {
    BUSINESS,
    CHEQUE,
    CREDIT_CARD,
    SPEND
}

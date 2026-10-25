package nz.co.ksktech.cashbook.synthetic.model;

/** Situations the pipeline must handle; each synthetic line lists the traps it exercises. */
public enum Trap {
    CARD_REPAYMENT,
    TRANSFER,
    SAVINGS_SWEEP,
    SPEND_TOP_UP,
    TAX_PAYMENT,
    GST_REFUND,
    ATM_CASH,
    REFUND_NETS_TO_ZERO,
    PERIOD_CUT_OFF,
    MONTH_BOUNDARY,
    UTILITY_SPLIT,
    UTILITY_CREDIT,
    FOREIGN_CURRENCY,
    OFFSHORE_MARGIN,
    FX_CONVERSION_FEE,
    MIXED_CHARGE,
    HIGH_VALUE,
    POLICY_QUESTION,
    MONTHLY_AGGREGATE,
    CREDIT_NOT_REPAYMENT,
    LEVY_DISCOUNT,
    HIGHLIGHT_BLEED,
    FLATTENED_HIGHLIGHT,
    WRAPPED_TEXT,
    REDACTION_TARGET
}

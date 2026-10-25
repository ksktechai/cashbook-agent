package nz.co.ksktech.cashbook.synthetic.model;

/** Statement layouts, each modelled on a real one (see docs: Westpac, ASB, Sharesies). */
public enum Layout {
    /** Westpac-style transaction search export for an account: newest first, end-of-day balance rows. */
    KAURI_ACCOUNT_EXPORT,
    /** Westpac-style export for a credit card: card and reference columns, no balance column. */
    KAURI_CARD_EXPORT,
    /** ASB-style Visa statement printed from the browser: oldest first, CR credits, running balance. */
    HARBOUR_VISA,
    /** ASB-style account statement: en-dash/plus amounts, descriptions straddling the amount line. */
    HARBOUR_ACCOUNT,
    /** Sharesies-style spend account statement: currency exchange and fee columns. */
    SHAREPOT_SPEND
}

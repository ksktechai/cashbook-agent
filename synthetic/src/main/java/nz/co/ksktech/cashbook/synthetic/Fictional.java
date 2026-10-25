package nz.co.ksktech.cashbook.synthetic;

/**
 * Every name, number and address in the synthetic data. All of it is invented: bank code 99 is not
 * issued in New Zealand, and the institutions only borrow the real statements' layouts.
 */
public final class Fictional {

    public static final String COMPANY = "Tuatara Lane Consulting Ltd";
    public static final String COMPANY_UPPER = "TUATARA LANE CONSULTING LIMITED";
    public static final String DIRECTOR = "Alex Example";
    public static final String DIRECTOR_INITIALS = "MR A L EXAMPLE";
    public static final String UNIT = "Unit 4B";
    public static final String STREET = "12 Sample Street";
    public static final String SUBURB = "Fakeville";
    public static final String CITY_POSTCODE = "Auckland 0999";
    public static final String EMAIL = "alex@example.com";
    public static final String IRD_NUMBER = "999-123-456";
    public static final String SYNTHETIC_FOOTER = "SYNTHETIC TEST DATA - NOT A REAL STATEMENT";

    public static final String KAURI = "Kauri Bank";
    public static final String HARBOURSIDE = "Harbourside Bank";
    public static final String SHAREPOT = "Sharepot";
    public static final String FIBREWAVE = "Fibrewave";
    public static final String BODY_CORP_MANAGER = "Kowhai Body Corporate Management Ltd";

    public static final String BUSINESS_ACCOUNT_NAME = "Business Everyday";
    public static final String BUSINESS_ACCOUNT = "99 0123 0456789 000";
    public static final String CHEQUE_ACCOUNT_NAME = "Cheque Account";
    public static final String CHEQUE_ACCOUNT = "99 0123 0456790 000";
    public static final String KAURI_CARD_NAME = "Kauri Rewards Mastercard";
    public static final String KAURI_CARD_LAST4 = "4821";
    public static final String HARBOUR_VISA_LAST4 = "7314";
    public static final String HARBOUR_ACCOUNT = "99-1234-0567890-00";
    public static final String SPEND_ACCOUNT = "99-9100-0001234-001";
    public static final String FIBREWAVE_ACCOUNT = "30123456";
    public static final String FIBREWAVE_GST = "99-123-456";
    public static final String BODY_CORP_GST = "99123456";
    public static final String BODY_CORP_NUMBER = "999001";

    private Fictional() {}

    public static String addressLine() {
        return UNIT + ", " + STREET + ", " + SUBURB + ", " + CITY_POSTCODE;
    }
}

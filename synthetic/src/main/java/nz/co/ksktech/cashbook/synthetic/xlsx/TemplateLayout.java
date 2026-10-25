package nz.co.ksktech.cashbook.synthetic.xlsx;

import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import nz.co.ksktech.cashbook.synthetic.Codes;

/**
 * The structure of the accountant's template as found by the redacted probe: which code each analysis
 * column tests, the six fixed month blocks, and the code each carried-forward row uses. Row numbers are
 * 1-based, as in Excel.
 */
public final class TemplateLayout {

    public static final String CASHBOOK = "Cashbook";
    public static final String CASH_POSITION = "Cash Pos";
    public static final String BUSINESS_USE = "% for Business Expenses";
    public static final String OTHER = "OtherCalculation";
    public static final String INSTRUCTIONS = "Instructions";

    public static final int HEADER_CODES_ROW = 4;
    public static final int HEADER_LABELS_ROW = 5;
    public static final int FIRST_FORMULA_ROW = 6;
    public static final int LAST_DATA_ROW = 104;
    public static final int TOTALS_ROW = 105;
    public static final String LAST_ANALYSIS_COLUMN = "AV";

    /**
     * Column -> the code its formula tests in the data rows (7-104). Note AG tests 705 while its header says
     * 735, and AH and AV both test 735 (unused, so harmless).
     */
    public static final Map<String, String> FORMULA_CODES = codes(
            "J", "707", "K", "706", "L", "703", "M", "704", "N", "709", "O", "738", "P", "739", "Q", "714",
            "R", "710", "S", "711", "T", "712", "U", "713", "V", "715", "W", "724", "X", "725", "Y", "726",
            "Z", "727", "AA", "728", "AB", "729", "AC", "730", "AD", "732", "AE", "733", "AF", "734", "AG", "705",
            "AH", "735", "AI", "736", "AJ", "737", "AN", "718", "AO", "719",
            "AP", "717", "AQ", "722", "AR", "708", "AS", "701", "AT", "702", "AU", "731", "AV", "735");

    /**
     * Row 6 (the opening row, which never holds a transaction) also has formulas in AK, AL and AM, and AV
     * tests 730 there. They add nothing, but they are part of the real template's structure.
     */
    public static final Map<String, String> OPENING_ROW_EXTRA_CODES = codes("AK", "725", "AL", "735", "AM", "727", "AV", "730");

    /** Row 5 labels (the real header's "Entertaintment" typo is kept). */
    public static final Map<String, String> HEADER_LABELS = codes(
            "A", "Date", "B", "Code", "C", "Detail", "D", "C/Book Balance", "E", "Ref", "G", "Bank",
            "H", "GST Income", "I", "GST Expense", "J", "Income", "K", "S/Holder Contribution", "N", "Purchases",
            "O", "ACC Levy", "P", "No GST Purchases\n(online tech. subscriptions)", "Q", "Interest", "R", "Bank Fees",
            "S", "Accountancy", "T", "Legal Fees", "U", "Vehicle Expenses", "V", "Postage & Stationery",
            "W", "Apartment Rent", "X", "Power", "Y", "Entertaintment", "Z", "Internet",
            "AA", "Mobile Plan - Tui Mobile", "AB", "Car Parking", "AC", "Contents Insurance",
            "AD", "Indemnity Insurance", "AE", "Fakeville Council Rates/Body Corp Fees", "AF", "Home Office Expenses");

    /** One month's fixed block of rows and the code each row carries forward from last period. */
    public record MonthBlock(YearMonth month, int firstRow, int lastRow, Map<Integer, String> rowCodes,
            int entertainmentRow) {}

    public static final List<MonthBlock> MONTHS = List.of(
            block(YearMonth.of(2026, 10), 7, 22, 9, "707", "725", "726", "727", "728", "729", "734", "734", "734",
                    "724", "710", "711", "733", "739", "739", "730"),
            block(YearMonth.of(2026, 11), 24, 37, 26, "707", "725", "726", "727", "728", "729", "734", "724", "710",
                    "730", "711", "732", "739", "733"),
            block(YearMonth.of(2026, 12), 39, 54, 40, "707", "726", "725", "733", "727", "728", "729", "734", "734",
                    "734", "734", "724", "710", "739", "739", "711"),
            block(YearMonth.of(2027, 1), 56, 70, 59, "707", "707", "725", "726", "727", "728", "729", "734", "734",
                    "734", "734", "710", "739", "739", "733"),
            block(YearMonth.of(2027, 2), 72, 86, 74, "707", "725", "726", "730", "727", "728", "729", "733", "734",
                    "734", "734", "724", "710", "711", "739"),
            block(YearMonth.of(2027, 3), 88, 100, 90, "707", "725", "726", "727", "728", "729", "734", "724", "710",
                    "733", "734", "739", "711"));

    /** The March rent row whose Bank cell links to an empty OtherCalculation cell (a stray link). */
    public static final int STRAY_LINK_ROW = 95;
    public static final String STRAY_LINK_TARGET = "OtherCalculation!C15";

    /** Carried-forward detail labels, by code. */
    public static final Map<String, String> ROW_LABELS = codes(
            Codes.POWER, "Power - Fibrewave",
            Codes.ENTERTAINMENT, "Entertainment (Business Lunch/Coffee/Meals/Drinks)",
            Codes.INTERNET, "Internet - Fibrewave",
            Codes.MOBILE, "Mobile Plan - Tui Mobile NZ",
            Codes.CAR_PARKING, "Car Park Fees",
            Codes.HOME_OFFICE, "Home Office Expenses",
            Codes.APARTMENT_RENT, "Apartment Rent",
            Codes.BANK_FEES, "Bank Fees",
            Codes.ACCOUNTANCY, "Accountant Fee (Accountancy)",
            Codes.RATES_BODY_CORP, "Body Corp Fees / Council Rates",
            Codes.NO_GST_PURCHASES, "Online Tech Subscriptions (NO GST)",
            Codes.CONTENTS_INSURANCE, "Home Contents Insurance",
            Codes.INDEMNITY_INSURANCE, "Indemnity Insurance - Shield (annual)");

    private TemplateLayout() {}

    /** Exempt rows have no GST Expense formula; no-GST rows have a typed 0 instead. */
    public static boolean exempt(String code) {
        return code.equals(Codes.APARTMENT_RENT) || code.equals(Codes.BANK_FEES);
    }

    public static boolean noGst(String code) {
        return code.equals(Codes.NO_GST_PURCHASES);
    }

    private static MonthBlock block(YearMonth month, int first, int last, int entertainment, String... rowCodes) {
        if (rowCodes.length != last - first + 1) {
            throw new IllegalArgumentException("block " + month + " needs " + (last - first + 1) + " codes");
        }
        Map<Integer, String> codes = new LinkedHashMap<>();
        for (int i = 0; i < rowCodes.length; i++) {
            codes.put(first + i, rowCodes[i]);
        }
        return new MonthBlock(month, first, last, codes, entertainment);
    }

    private static Map<String, String> codes(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return java.util.Collections.unmodifiableMap(map);
    }
}

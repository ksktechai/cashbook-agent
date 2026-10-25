package nz.co.ksktech.cashbook.synthetic.xlsx;

import static nz.co.ksktech.cashbook.synthetic.xlsx.TemplateLayout.CASHBOOK;
import static nz.co.ksktech.cashbook.synthetic.xlsx.TemplateLayout.FORMULA_CODES;

import java.util.List;
import java.util.Map;
import nz.co.ksktech.cashbook.synthetic.Fictional;
import nz.co.ksktech.cashbook.synthetic.xlsx.TemplateLayout.MonthBlock;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Builds the sanitised template: the real template's structure (sheets, month blocks, formulas and their
 * quirks) with a fictional company and vendors, data cells blank and amounts zero, as when the accountant
 * rolls the workbook forward to a new period.
 */
public final class TemplateBuilder {

    private final XSSFWorkbook wb = new XSSFWorkbook();
    private final CellStyle bold;
    private final CellStyle money;
    private final CellStyle wrap;
    private final CellStyle percent;

    private TemplateBuilder() {
        Font font = wb.createFont();
        font.setBold(true);
        bold = wb.createCellStyle();
        bold.setFont(font);
        money = wb.createCellStyle();
        money.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
        wrap = wb.createCellStyle();
        wrap.setWrapText(true);
        wrap.setFont(font);
        percent = wb.createCellStyle();
        percent.setDataFormat(wb.createDataFormat().getFormat("0%"));
    }

    /** A fresh in-memory template; the caller owns (and closes) the workbook. */
    public static XSSFWorkbook build() {
        TemplateBuilder b = new TemplateBuilder();
        b.instructions();
        b.cashbook();
        b.cashPosition();
        b.businessUse();
        b.otherCalculation();
        Workbooks.fixProperties(b.wb);
        return b.wb;
    }

    private void instructions() {
        XSSFSheet s = wb.createSheet(TemplateLayout.INSTRUCTIONS);
        set(s, "A1", "   ");
        set(s, "A9", "How to fill in your Business Cashbook").setCellStyle(bold);
        set(s, "A11", "Columns:");
        String[][] steps = {
            {"Column A", "In Column A enter the date of each transaction from the bank statement"},
            {"Column B", "Code - the codes at the top of the Income & Expense columns are Account Analysis codes."},
            {"", "Enter the code number of the column the transaction is for (e.g. Bank Fees is code 710)"},
            {"Column C", "Detail - write the particulars of the transaction, e.g. the client name for income"},
            {"Column D", "Cashbook Balance - this column calculates itself, do not type in it"},
            {"Column E", "Ref. - the cheque number or the transaction's reference"},
            {"Column G", "Bank - the amount of the transaction: income is positive, an expense is negative ( - )"},
            {"Column H/I", "GST Income/Expense - calculated from the bank amount; leave blank where there is no GST,"},
            {"", "e.g. interest, bank fees, shareholder contributions and offshore subscriptions"},
            {"Row 108", "Bank Balance - the closing balance shown on your bank statement."},
            {"Row 110", "Unpresented deposits - deposits not yet on the bank statement."},
            {"Row 113", "Unpresented cheques - payments not yet on the bank statement."}
        };
        int row = 13;
        for (String[] step : steps) {
            set(s, "A" + row, step[0]);
            set(s, "B" + row, step[1]);
            row += 2;
        }
        set(s, "A74", Fictional.SYNTHETIC_FOOTER);
    }

    private void cashbook() {
        XSSFSheet s = wb.createSheet(CASHBOOK);
        set(s, "A1", "Company Name: " + Fictional.COMPANY_UPPER).setCellStyle(bold);
        set(s, "A3", "Cashbook for period ending 31/03/2027").setCellStyle(bold);
        set(s, "K3", "*");
        set(s, "S3", "*");
        // Header codes as the real template stores them: some as text, some as numbers, and AG's header
        // (735) disagreeing with the code its formulas test (705).
        for (Map.Entry<String, String> e : FORMULA_CODES.entrySet()) {
            String col = e.getKey();
            int index = CellReference.convertColStringToIndex(col);
            if (index > CellReference.convertColStringToIndex("AG")) {
                break;
            }
            XSSFCell cell = cell(s, col + TemplateLayout.HEADER_CODES_ROW);
            if (col.equals("AG")) {
                cell.setCellValue(735);
            } else if (index >= CellReference.convertColStringToIndex("X")
                    && index <= CellReference.convertColStringToIndex("AC")) {
                cell.setCellValue(Integer.parseInt(e.getValue()));
            } else {
                cell.setCellValue(e.getValue());
            }
            cell.setCellStyle(bold);
        }
        TemplateLayout.HEADER_LABELS.forEach((col, label) -> set(s, col + TemplateLayout.HEADER_LABELS_ROW, label)
                .setCellStyle(label.contains("\n") ? wrap : bold));

        int row = TemplateLayout.FIRST_FORMULA_ROW;
        analysisFormulas(s, row, false);
        for (row = 7; row <= TemplateLayout.LAST_DATA_ROW; row++) {
            String code = codeAt(row);
            standardFormulas(s, row, code);
            analysisFormulas(s, row, true);
            if (code != null) {
                cell(s, "B" + row).setCellValue(Integer.parseInt(code));
                set(s, "C" + row, label(code, row));
                XSSFCell bank = cell(s, "G" + row);
                bank.setCellStyle(money);
                bank.setCellValue(0);
            } else if (row > 100) {
                cell(s, "G" + row).setCellValue(0);
            }
        }
        for (MonthBlock block : TemplateLayout.MONTHS) {
            int link = 5 + TemplateLayout.MONTHS.indexOf(block);
            cell(s, "G" + block.entertainmentRow()).setCellFormula("-(" + TemplateLayout.OTHER + "!C" + link + ")");
        }
        cell(s, "G" + TemplateLayout.STRAY_LINK_ROW).setCellFormula("-(" + TemplateLayout.STRAY_LINK_TARGET + ")");

        int totals = TemplateLayout.TOTALS_ROW;
        for (int c = col("G"); c <= col(TemplateLayout.LAST_ANALYSIS_COLUMN); c++) {
            String letter = CellReference.convertNumToColString(c);
            XSSFCell cell = cell(s, letter + totals);
            cell.setCellFormula("SUM(" + letter + "6:" + letter + TemplateLayout.LAST_DATA_ROW + ")");
            cell.setCellStyle(money);
        }
        set(s, "C106", "Bank Reconciliation:").setCellStyle(bold);
        set(s, "C108", "Closing Bank Balance @ ??");
        set(s, "C110", "Add: Unpresented Deposits");
        set(s, "E112", "Cheque Number:");
        set(s, "C113", "Less: Unpresented Cheques");
        cell(s, "D117").setCellFormula("SUM(D108:D116)");
        set(s, "C119", "Cashbook Balance");
        cell(s, "D119").setCellFormula("D104");
        cell(s, "D120").setCellFormula("D119-D117");
        set(s, "C124", "Note:");
        set(s, "C126", "Exclude any GST Payments or Refund Received in this GST period");
        s.setColumnWidth(col("C"), 48 * 256);
        s.createFreezePane(3, 5);
    }

    private static String codeAt(int row) {
        for (MonthBlock block : TemplateLayout.MONTHS) {
            String code = block.rowCodes().get(row);
            if (code != null) {
                return code;
            }
        }
        return null;
    }

    private static String label(String code, int row) {
        if (code.equals("707")) {
            MonthBlock block = TemplateLayout.MONTHS.stream().filter(b -> b.rowCodes().containsKey(row)).findFirst()
                    .orElseThrow();
            String month = block.month().getMonth().getDisplayName(java.time.format.TextStyle.FULL,
                    java.util.Locale.ENGLISH);
            return "Income - " + month + " - Client (50% progress payment received)";
        }
        return TemplateLayout.ROW_LABELS.get(code);
    }

    /** D running balance, F row check, H/I GST, as in the real template. */
    private void standardFormulas(XSSFSheet s, int row, String code) {
        cell(s, "D" + row).setCellFormula("D" + (row - 1) + "+G" + row);
        cell(s, "F" + row).setCellFormula("SUM(H" + row + ":" + TemplateLayout.LAST_ANALYSIS_COLUMN + row + ")-G" + row);
        cell(s, "H" + row).setCellFormula("IF(G" + row + "/23*3>0,SUM(G" + row + "/23*3),\"\")");
        if (code != null && TemplateLayout.exempt(code)) {
            return; // the real template has no GST Expense formula on exempt rows
        }
        if (code != null && TemplateLayout.noGst(code)) {
            cell(s, "I" + row).setCellValue(0); // typed zero on no-GST rows
            return;
        }
        cell(s, "I" + row).setCellFormula("IF(G" + row + "/23*3<0,SUM(G" + row + "/23*3),\"\")");
    }

    /** One formula per analysis column: =IF($B=code, G-(H+I), ""). Row 6 has no Income formula. */
    static void analysisFormulas(XSSFSheet s, int row, boolean includeIncome) {
        for (Map.Entry<String, String> e : FORMULA_CODES.entrySet()) {
            if (!includeIncome && e.getKey().equals("J")) {
                continue;
            }
            XSSFCell cell = cell(s, e.getKey() + row);
            cell.setCellFormula(analysisFormula(e.getValue(), row));
        }
    }

    static String analysisFormula(String code, int row) {
        return "IF($B" + row + "=" + code + ",SUM($G" + row + "-(SUM($H" + row + ":$I" + row + "))),\"\")";
    }

    private void cashPosition() {
        XSSFSheet s = wb.createSheet(TemplateLayout.CASH_POSITION);
        set(s, "A1", Fictional.COMPANY_UPPER).setCellStyle(bold);
        set(s, "A2", "Cash Position as at 31 Mar 2027").setCellStyle(bold);
        s.addMergedRegion(CellRangeAddress.valueOf("A1:F1"));
        s.addMergedRegion(CellRangeAddress.valueOf("A2:F2"));
        set(s, "B5", "Cash Receipts").setCellStyle(bold);
        set(s, "B7", "Income Received");
        cell(s, "E7").setCellFormula("Cashbook!J105");
        set(s, "B8", "Shareholder Contribution");
        cell(s, "E8").setCellFormula("Cashbook!K105");
        set(s, "B9", "GST Income");
        cell(s, "E9").setCellFormula("Cashbook!H105");
        set(s, "B11", "Total Cash Receipts").setCellStyle(bold);
        cell(s, "E11").setCellFormula("SUM(E7:E10)");
        set(s, "B14", "Cash Payments").setCellStyle(bold);
        // Rows 16-34: label from the Cashbook header (or typed), amount from the totals row.
        String[] columns = {"N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "AA", "AB", "AC", "AD",
            "AE", "AF"};
        Map<String, String> typedLabels = Map.of("Q", "Interest", "T", "Legal Fees", "W", "Apartment Rent");
        for (int i = 0; i < columns.length; i++) {
            int row = 16 + i;
            String c = columns[i];
            if (typedLabels.containsKey(c)) {
                set(s, "B" + row, typedLabels.get(c));
            } else {
                cell(s, "B" + row).setCellFormula("Cashbook!" + c + "5");
            }
            cell(s, "E" + row).setCellFormula("Cashbook!" + c + "105");
        }
        set(s, "G25", "Unit 4B: 12 Sample Street, Fakeville  25% for Home Office");
        // The real sheet labels this row from H5 ("GST Income") while the amount is GST Expense (I105).
        cell(s, "B35").setCellFormula("Cashbook!H5");
        cell(s, "E35").setCellFormula("Cashbook!I105");
        set(s, "B37", "Total Cash Payments").setCellStyle(bold);
        cell(s, "E37").setCellFormula("SUM(E16:E36)");
        set(s, "B40", "Total Cash Receipts");
        cell(s, "E40").setCellFormula("E11");
        set(s, "B41", "Total Cash Payments");
        cell(s, "E41").setCellFormula("E37");
        cell(s, "E42").setCellFormula("SUM(E40:E41)");
        set(s, "B44", "Represented By:").setCellStyle(bold);
        set(s, "B45", "Current Account");
        cell(s, "E45").setCellFormula("Cashbook!G105");
        cell(s, "E46").setCellFormula("SUM(E45:E45)");
        set(s, "D48", "Difference").setCellStyle(bold);
        cell(s, "E48").setCellFormula("E46-E42");
        set(s, "G48", "Check this is zero");
    }

    private void businessUse() {
        XSSFSheet s = wb.createSheet(TemplateLayout.BUSINESS_USE);
        set(s, "A1", "Percentages for business use, for reference.\nThe accountant applies these to the expense and "
                + "GST Amount for business use").setCellStyle(wrap);
        set(s, "A4", "Code").setCellStyle(bold);
        set(s, "B4", "Description").setCellStyle(bold);
        set(s, "C4", "% to use for business expenses").setCellStyle(bold);
        Object[][] rows = {
            {739, "No GST Purchases\n(online tech. subscriptions)", 1.0},
            {710, "Bank Fees", 1.0},
            {711, "Accountancy", 1.0},
            {725, "Power", 0.25},
            {726, "Entertaintment", 0.5},
            {727, "Internet", 1.0},
            {728, "Mobile Plan - Tui Mobile", 0.8},
            {729, "Car Parking", 1.0},
            {730, "Contents Insurance", 0.25},
            {732, "Indemnity Insurance", 1.0},
            {733, "Fakeville Council Rates/Body Corp Fees", 0.25},
            {734, "Home Office Expenses", 1.0}
        };
        for (int i = 0; i < rows.length; i++) {
            int row = 6 + i;
            cell(s, "A" + row).setCellValue((Integer) rows[i][0]);
            set(s, "B" + row, (String) rows[i][1]);
            XSSFCell pct = cell(s, "C" + row);
            pct.setCellValue((Double) rows[i][2]);
            pct.setCellStyle(percent);
        }
    }

    private void otherCalculation() {
        XSSFSheet s = wb.createSheet(TemplateLayout.OTHER);
        set(s, "A1", "Other Calculations").setCellStyle(bold);
        set(s, "A2", Fictional.COMPANY);
        s.addMergedRegion(CellRangeAddress.valueOf("A1:F1"));
        s.addMergedRegion(CellRangeAddress.valueOf("A2:F2"));
        set(s, "A4", "Entertainment").setCellStyle(bold);
        for (int i = 0; i < TemplateLayout.MONTHS.size(); i++) {
            int row = 1 + i;
            var month = TemplateLayout.MONTHS.get(i).month();
            cell(s, "H" + row).setCellFormula("DATE(" + month.getYear() + ", " + month.getMonthValue() + ", 1)");
            cell(s, "G" + row).setCellFormula("TEXT(H" + row + ", \"mmm\") & \"'\" & YEAR(H" + row + ")");
            cell(s, "A" + (5 + i)).setCellFormula("G" + row);
            set(s, "B" + (5 + i), "Business Lunch/Coffee/Meals");
            XSSFCell amount = cell(s, "C" + (5 + i));
            amount.setCellValue(0);
            amount.setCellStyle(money);
        }
        for (Object[] section : List.<Object[]>of(new Object[] {12, "Indemnity Insurance"},
                new Object[] {16, "Internet Subscriptions"}, new Object[] {36, "Car Park Fees"},
                new Object[] {56, "Business Expenses"}, new Object[] {80, "Fibrewave - Power / Internet split"})) {
            set(s, "A" + section[0], (String) section[1]).setCellStyle(bold);
        }
        set(s, "C16", "NO GST");
    }

    // ---------------------------------------------------------------- cell helpers

    static XSSFCell cell(XSSFSheet s, String ref) {
        CellReference r = new CellReference(ref);
        XSSFRow row = s.getRow(r.getRow());
        if (row == null) {
            row = s.createRow(r.getRow());
        }
        XSSFCell cell = row.getCell(r.getCol());
        return cell == null ? row.createCell(r.getCol()) : cell;
    }

    static XSSFCell set(XSSFSheet s, String ref, String value) {
        XSSFCell c = cell(s, ref);
        c.setCellValue(value);
        return c;
    }

    static int col(String letters) {
        return CellReference.convertColStringToIndex(letters);
    }
}

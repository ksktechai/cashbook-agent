package nz.co.ksktech.cashbook.export;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Evaluates every formula and checks the filled workbook: each row check (F) is 0, the cash position
 * difference is 0, each OtherCalculation section total equals its codes' Cashbook total, and each utility
 * split row checks to 0. Returns one message per failure; empty means the workbook balances.
 */
public final class ExportChecks {

    private static final double CENT = 0.005;
    private static final String DIFFERENCE = "Difference";
    private static final String TOTAL = "Total";

    private ExportChecks() {}

    public static List<String> run(XSSFWorkbook wb, ExportOptions options) {
        XSSFFormulaEvaluator eval = wb.getCreationHelper().createFormulaEvaluator();
        eval.evaluateAll();
        List<String> failures = new ArrayList<>();
        XSSFSheet cashbook = wb.getSheet(options.cashbookSheet());
        TemplateModel model = TemplateModel.inspect(cashbook, options.entertainmentCode());
        for (int r = model.firstDataRow; r < model.totalsRow; r++) {
            Cell f = TemplateModel.cell(cashbook, r, TemplateModel.COL_F);
            double v = number(eval, f);
            if (Math.abs(v) >= CENT) {
                failures.add("row check F" + (r + 1) + " is " + round(v) + ", not 0");
            }
        }
        cashPosition(wb.getSheet(options.cashPositionSheet()), eval, failures);
        sections(wb.getSheet(options.otherSheet()), cashbook, model, eval, options, failures);
        return failures;
    }

    private static void cashPosition(XSSFSheet s, XSSFFormulaEvaluator eval, List<String> failures) {
        for (Row row : s) {
            for (Cell c : row) {
                if (c.getCellType() == CellType.STRING && c.getStringCellValue().strip().equals(DIFFERENCE)) {
                    double v = number(eval, row.getCell(c.getColumnIndex() + 1));
                    if (Math.abs(v) >= CENT) {
                        failures.add(s.getSheetName() + " difference is " + round(v) + ", not 0");
                    }
                    return;
                }
            }
        }
        failures.add(s.getSheetName() + " has no '" + DIFFERENCE + "' cell to check");
    }

    private static void sections(XSSFSheet other, XSSFSheet cashbook, TemplateModel model, XSSFFormulaEvaluator eval,
            ExportOptions options, List<String> failures) {
        Map<String, Set<String>> sectionCodes = options.sectionCodes();
        // entertainment: the cells the Cashbook's entertainment rows link to
        double entertainment = 0;
        for (TemplateModel.Block b : model.blocks) {
            if (b.entertainmentRow() >= 0) {
                entertainment -= number(eval, TemplateModel.cell(cashbook, b.entertainmentRow(), TemplateModel.COL_G));
            }
        }
        compare("Entertainment", entertainment, codeTotal(cashbook, model, eval, sectionCodes.get("Entertainment")), failures);
        String current = null;
        for (Row row : other) {
            Cell a = row.getCell(TemplateModel.COL_A);
            Cell b = row.getCell(TemplateModel.COL_B);
            if (a != null && a.getCellType() == CellType.STRING && sectionCodes.containsKey(a.getStringCellValue().strip())) {
                current = a.getStringCellValue().strip();
            }
            if (current != null && b != null && b.getCellType() == CellType.STRING && b.getStringCellValue().equals(TOTAL)) {
                compare(current, number(eval, row.getCell(TemplateModel.COL_D)),
                        codeTotal(cashbook, model, eval, sectionCodes.get(current)), failures);
                current = null;
            }
            Cell h = row.getCell(TemplateModel.COL_H);
            if (h != null && h.getCellType() == CellType.FORMULA && h.getCellFormula().startsWith("ROUND(")
                    && Math.abs(number(eval, h)) >= CENT) {
                failures.add("utility split check H" + (row.getRowNum() + 1) + " is " + round(number(eval, h)));
            }
        }
    }

    /** Minus the Bank total of rows coded with any of {@code codes} (sections hold positive costs). */
    private static double codeTotal(XSSFSheet s, TemplateModel model, XSSFFormulaEvaluator eval, Set<String> codes) {
        double total = 0;
        for (int r = model.firstDataRow; r < model.totalsRow; r++) {
            Cell b = TemplateModel.cell(s, r, TemplateModel.COL_B);
            if (b != null && TemplateModel.text(b).filter(codes::contains).isPresent()) {
                total -= number(eval, TemplateModel.cell(s, r, TemplateModel.COL_G));
            }
        }
        return total;
    }

    private static void compare(String section, double sectionTotal, double codeTotal, List<String> failures) {
        if (Math.abs(sectionTotal - codeTotal) >= CENT) {
            failures.add(section + " section total " + round(sectionTotal) + " does not equal its Cashbook total "
                    + round(codeTotal));
        }
    }

    private static double number(XSSFFormulaEvaluator eval, Cell cell) {
        if (cell == null) {
            return 0;
        }
        CellValue v = eval.evaluate(cell);
        return v == null || v.getCellType() != CellType.NUMERIC ? 0 : v.getNumberValue();
    }

    private static String round(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }
}

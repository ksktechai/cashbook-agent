package nz.co.ksktech.cashbook.export;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import nz.co.ksktech.cashbook.core.Money;
import nz.co.ksktech.cashbook.core.Period;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Reads a filled cashbook back into lines, for the evaluation: every data row in a month block with a code
 * and a non-zero Bank amount. Formulas are evaluated, so GST is what the accountant's own formulas give
 * (H: GST Income, I: GST Expense). The layout is found the same way the export finds it.
 */
public final class CashbookReader {

    /** GST values keep four places so that totals round once, as the spreadsheet's do. */
    public static final int GST_SCALE = 4;
    private static final double CENT = 0.005;
    private static final DateTimeFormatter NUMERIC = DateTimeFormatter.ofPattern("d/M/yyyy", Locale.ROOT);

    /**
     * One cashbook row.
     *
     * @param date null when the Date cell is empty or not a date
     * @param gstCollected GST Income (H), zero when blank
     * @param gstClaimed GST Expense (I), negative, zero when blank
     * @param row the 1-based spreadsheet row
     */
    public record Line(YearMonth month, LocalDate date, String code, String detail, BigDecimal amount,
            BigDecimal gstCollected, BigDecimal gstClaimed, int row) {}

    private CashbookReader() {}

    public static List<Line> read(Path xlsx, Period period, ExportOptions options) {
        try (InputStream in = Files.newInputStream(xlsx); XSSFWorkbook wb = new XSSFWorkbook(in)) {
            return read(wb, period, options);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + xlsx, e);
        }
    }

    public static List<Line> read(XSSFWorkbook wb, Period period, ExportOptions options) {
        XSSFSheet s = wb.getSheet(options.cashbookSheet());
        if (s == null) {
            throw new TemplateModel.TemplateException("no sheet named " + options.cashbookSheet());
        }
        TemplateModel model = TemplateModel.inspect(s, options.entertainmentCode());
        int months = (int) (period.end().getYear() * 12L + period.end().getMonthValue()
                - period.start().getYear() * 12L - period.start().getMonthValue()) + 1;
        if (model.blocks.size() != months) {
            throw new TemplateModel.TemplateException("the cashbook has " + model.blocks.size() + " month blocks but the"
                    + " period " + period + " has " + months + " months");
        }
        XSSFFormulaEvaluator eval = wb.getCreationHelper().createFormulaEvaluator();
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < model.blocks.size(); i++) {
            TemplateModel.Block b = model.blocks.get(i);
            YearMonth month = period.start().plusMonths(i);
            for (int r = b.firstRow(); r <= b.lastRow(); r++) {
                double amount = number(eval, TemplateModel.cell(s, r, TemplateModel.COL_G));
                String code = text(s, r, TemplateModel.COL_B);
                if (Math.abs(amount) < CENT || code.isEmpty()) {
                    continue;
                }
                lines.add(new Line(month, date(TemplateModel.cell(s, r, TemplateModel.COL_A)), code,
                        text(s, r, TemplateModel.COL_C), Money.normalise(BigDecimal.valueOf(amount)),
                        gst(eval, TemplateModel.cell(s, r, TemplateModel.COL_H)),
                        gst(eval, TemplateModel.cell(s, r, TemplateModel.COL_I)), r + 1));
            }
        }
        return lines;
    }

    private static String text(XSSFSheet s, int r, int c) {
        Cell cell = TemplateModel.cell(s, r, c);
        return cell == null ? "" : TemplateModel.text(cell).orElse("");
    }

    private static LocalDate date(Cell c) {
        if (c == null) {
            return null;
        }
        if (c.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(c)) {
            return c.getLocalDateTimeCellValue().toLocalDate();
        }
        if (c.getCellType() == CellType.STRING) {
            try {
                return LocalDate.parse(c.getStringCellValue().strip(), NUMERIC);
            } catch (DateTimeParseException e) {
                return null;
            }
        }
        return null;
    }

    private static BigDecimal gst(XSSFFormulaEvaluator eval, Cell c) {
        return BigDecimal.valueOf(number(eval, c)).setScale(GST_SCALE, RoundingMode.HALF_UP);
    }

    private static double number(XSSFFormulaEvaluator eval, Cell cell) {
        if (cell == null) {
            return 0;
        }
        CellValue v = eval.evaluate(cell);
        return v == null || v.getCellType() != CellType.NUMERIC ? 0 : v.getNumberValue();
    }
}

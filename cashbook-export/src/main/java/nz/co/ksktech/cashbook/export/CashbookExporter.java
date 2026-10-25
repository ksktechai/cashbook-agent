package nz.co.ksktech.cashbook.export;

import static nz.co.ksktech.cashbook.export.TemplateModel.COL_A;
import static nz.co.ksktech.cashbook.export.TemplateModel.COL_B;
import static nz.co.ksktech.cashbook.export.TemplateModel.COL_C;
import static nz.co.ksktech.cashbook.export.TemplateModel.COL_D;
import static nz.co.ksktech.cashbook.export.TemplateModel.COL_G;
import static nz.co.ksktech.cashbook.export.TemplateModel.COL_I;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import nz.co.ksktech.cashbook.core.CashbookLine;
import nz.co.ksktech.cashbook.core.GstTreatment;
import nz.co.ksktech.cashbook.core.Period;
import nz.co.ksktech.cashbook.core.reconcile.SectionItem;
import nz.co.ksktech.cashbook.core.reconcile.UtilitySplit;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Fills the accountant's template without changing its layout: each month's lines go into that month's
 * block (rows are inserted inside the block when it is too small, and POI shifts every reference to the
 * rows below, other sheets included), the running balance is rewritten, OtherCalculation gets the itemised
 * sections and the utility split table, and the period labels are updated. {@link ExportChecks} then
 * evaluates the formulas.
 */
public final class CashbookExporter {

    private static final Pattern RELATIVE_ROW = Pattern.compile("(?<![A-Za-z_!])(\\$?)([A-Z]{1,3})(\\$?)(\\d+)(?![\\d(])");
    private static final Pattern DATE_FORMULA = Pattern.compile("^DATE\\(\\s*\\d{4}\\s*,\\s*\\d{1,2}\\s*,\\s*1\\s*\\)$");
    private static final Pattern PERIOD_ENDING = Pattern.compile("(?i)^(.*period ending\\s*).*$");
    private static final Pattern AS_AT = Pattern.compile("(?i)^(.*\\bas at\\s*).*$");
    private static final DateTimeFormatter NUMERIC = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT);
    private static final DateTimeFormatter AS_AT_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM", Locale.ROOT);
    private static final String TOTAL = "Total";
    private static final String NO_GST = "NO GST";
    private static final String CAR_PARK_SECTION = "Car Park Fees";
    private static final int MONTH_LABEL_ROWS = 12;

    private final XSSFWorkbook wb;
    private final ExportOptions options;
    private final Period period;
    private final List<String> notes = new ArrayList<>();
    private final CellStyle dateStyle;
    private final CellStyle moneyStyle;
    private final CellStyle boldStyle;
    private int insertedRows;

    private CashbookExporter(XSSFWorkbook wb, ExportOptions options, Period period) {
        this.wb = wb;
        this.options = options;
        this.period = period;
        dateStyle = wb.createCellStyle();
        dateStyle.setDataFormat(wb.createDataFormat().getFormat("dd/mm/yyyy"));
        moneyStyle = wb.createCellStyle();
        moneyStyle.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
        Font bold = wb.createFont();
        bold.setBold(true);
        boldStyle = wb.createCellStyle();
        boldStyle.setFont(bold);
    }

    /** What the export did, beyond filling cells: notes about the template, and rows inserted. */
    public record Result(List<String> notes, int insertedRows) {

        public Result {
            notes = List.copyOf(notes);
        }
    }

    public static Result fill(XSSFWorkbook wb, ExportInput input, Period period, ExportOptions options) {
        return new CashbookExporter(wb, options, period).fill(input);
    }

    /** The outcome of exporting to a file: template notes, assertion failures (empty = balances), rows inserted. */
    public record Report(List<String> notes, List<String> failures, int insertedRows) {

        public Report {
            notes = List.copyOf(notes);
            failures = List.copyOf(failures);
        }
    }

    /**
     * Copies the template to {@code out}, fills it, evaluates and checks it, and saves it (even when a check
     * fails, so the result can be inspected). The template file itself is never modified.
     */
    public static Report export(java.nio.file.Path template, ExportInput input, Period period, ExportOptions options,
            java.nio.file.Path out) {
        try (java.io.InputStream in = java.nio.file.Files.newInputStream(template); XSSFWorkbook wb = new XSSFWorkbook(in)) {
            Result result = fill(wb, input, period, options);
            List<String> failures = ExportChecks.run(wb, options);
            java.nio.file.Path parent = out.toAbsolutePath().getParent();
            java.nio.file.Files.createDirectories(parent);
            try (java.io.OutputStream os = java.nio.file.Files.newOutputStream(out)) {
                wb.write(os);
            }
            return new Report(result.notes(), failures, result.insertedRows());
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("cannot export " + template + " to " + out, e);
        }
    }

    private Result fill(ExportInput input) {
        XSSFSheet cashbook = sheet(options.cashbookSheet());
        TemplateModel model = TemplateModel.inspect(cashbook, options.entertainmentCode());
        notes.addAll(model.notes);
        List<YearMonth> months = months();
        if (model.blocks.size() != months.size()) {
            throw new TemplateModel.TemplateException("the template has " + model.blocks.size() + " month blocks but the"
                    + " period " + period + " has " + months.size() + " months");
        }
        Map<YearMonth, List<CashbookLine>> byMonth = input.lines().stream()
                .collect(Collectors.groupingBy(CashbookLine::month));
        // bottom-up, so rows inserted into a later month do not move the earlier blocks
        for (int i = model.blocks.size() - 1; i >= 0; i--) {
            fillMonth(cashbook, model.blocks.get(i), byMonth.getOrDefault(months.get(i), List.of()), input.items(), months.get(i));
        }
        TemplateModel after = TemplateModel.inspect(cashbook, options.entertainmentCode());
        for (int r = after.firstDataRow; r < after.totalsRow; r++) {
            replace(cashbook, r, COL_D).setCellFormula("D" + r + "+G" + (r + 1));
        }
        otherCalculation(sheet(options.otherSheet()), input, months);
        labels(cashbook, sheet(options.cashPositionSheet()), sheet(options.otherSheet()), months);
        return new Result(notes, insertedRows);
    }

    // ---------------------------------------------------------------- Cashbook

    private void fillMonth(XSSFSheet s, TemplateModel.Block block, List<CashbookLine> lines, List<SectionItem> items,
            YearMonth month) {
        List<CashbookLine> regular = new ArrayList<>(lines.stream()
                .filter(l -> block.entertainmentRow() < 0 || !l.code().equals(options.entertainmentCode()))
                .sorted(Comparator.comparingInt(this::order).thenComparing(CashbookLine::code))
                .toList());
        List<Integer> rows = new ArrayList<>();
        for (int r = block.firstRow(); r <= block.lastRow(); r++) {
            if (r != block.entertainmentRow()) {
                rows.add(r);
            }
        }
        int missing = regular.size() - rows.size();
        if (missing > 0) {
            insertRows(s, block.lastRow(), missing);
            for (int k = 1; k <= missing; k++) {
                rows.add(block.lastRow() + k);
            }
            notes.add(month + ": inserted " + missing + " row(s) below row " + (block.lastRow() + 1));
        }
        for (int i = 0; i < rows.size(); i++) {
            int r = rows.get(i);
            if (i < regular.size()) {
                writeLine(s, r, regular.get(i));
            } else {
                clearRow(s, r);
            }
        }
        if (block.entertainmentRow() >= 0) {
            writeEntertainment(s, block, lines, items, month);
        }
    }

    private void writeLine(XSSFSheet s, int r, CashbookLine line) {
        Cell g = TemplateModel.cell(s, r, COL_G);
        if (g != null && g.getCellType() == CellType.FORMULA) {
            notes.add("G" + (r + 1) + " held a formula (" + g.getCellFormula() + "); replaced with the line's amount");
        }
        dateCell(s, r, COL_A, line.date());
        replace(s, r, COL_B).setCellValue(Integer.parseInt(line.code()));
        replace(s, r, COL_C).setCellValue(line.detail());
        money(s, r, COL_G, line.amount());
        if (line.gst() == GstTreatment.EXEMPT) {
            remove(s, r, COL_I);
        } else if (line.gst() == GstTreatment.NO_GST) {
            replace(s, r, COL_I).setCellValue(0);
        } else {
            replace(s, r, COL_I).setCellFormula("IF(G" + (r + 1) + "/23*3<0,SUM(G" + (r + 1) + "/23*3),\"\")");
        }
    }

    private void clearRow(XSSFSheet s, int r) {
        Cell g = TemplateModel.cell(s, r, COL_G);
        if (g != null && g.getCellType() == CellType.FORMULA) {
            notes.add("G" + (r + 1) + " held a formula (" + g.getCellFormula() + "); set to 0 as the row is unused");
        }
        remove(s, r, COL_A);
        remove(s, r, COL_B);
        remove(s, r, COL_C);
        replace(s, r, COL_G).setCellValue(0);
    }

    private void writeEntertainment(XSSFSheet s, TemplateModel.Block block, List<CashbookLine> lines,
            List<SectionItem> items, YearMonth month) {
        List<CashbookLine> fun = lines.stream().filter(l -> l.code().equals(options.entertainmentCode())).toList();
        BigDecimal total = fun.stream().map(CashbookLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        int r = block.entertainmentRow();
        if (!fun.isEmpty()) {
            dateCell(s, r, COL_A, fun.getFirst().date());
            replace(s, r, COL_C).setCellValue(fun.getFirst().detail());
        }
        Matcher link = TemplateModel.OTHER_LINK.matcher(block.entertainmentLink());
        if (!link.matches()) {
            return;
        }
        XSSFSheet other = sheet(link.group(1));
        int row = Integer.parseInt(link.group(3)) - 1;
        int col = CellReference.convertColStringToIndex(link.group(2));
        String dates = items.stream()
                .filter(i -> i.month().equals(month) && i.code().equals(options.entertainmentCode()))
                .map(i -> i.date().format(DAY_MONTH)).collect(Collectors.joining(", "));
        if (!dates.isEmpty()) {
            replace(other, row, col - 1).setCellValue(options.entertainmentLabel() + " - " + dates);
        }
        money(other, row, col, total.negate());
    }

    /** Inserts rows below {@code afterRow}, copying its styles and formulas (shifted to the new rows). */
    private void insertRows(XSSFSheet s, int afterRow, int count) {
        s.shiftRows(afterRow + 1, s.getLastRowNum(), count, true, false);
        XSSFRow source = s.getRow(afterRow);
        for (int k = 1; k <= count; k++) {
            XSSFRow row = s.createRow(afterRow + k);
            row.setHeight(source.getHeight());
            for (Cell c : source) {
                XSSFCell copy = row.createCell(c.getColumnIndex());
                copy.setCellStyle(c.getCellStyle());
                if (c.getCellType() == CellType.FORMULA) {
                    copy.setCellFormula(shiftRows(c.getCellFormula(), k));
                }
            }
        }
        insertedRows += count;
    }

    /** Moves every relative row reference in a formula down by {@code delta} (absolute rows stay). */
    static String shiftRows(String formula, int delta) {
        Matcher m = RELATIVE_ROW.matcher(formula);
        StringBuilder b = new StringBuilder();
        while (m.find()) {
            String row = m.group(3).isEmpty() ? Integer.toString(Integer.parseInt(m.group(4)) + delta) : m.group(4);
            m.appendReplacement(b, Matcher.quoteReplacement(m.group(1) + m.group(2) + m.group(3) + row));
        }
        return m.appendTail(b).toString();
    }

    private int order(CashbookLine line) {
        int i = options.rowOrder().indexOf(line.code());
        return i < 0 ? Integer.MAX_VALUE : i;
    }

    // ---------------------------------------------------------------- OtherCalculation

    private void otherCalculation(XSSFSheet s, ExportInput input, List<YearMonth> months) {
        int start = firstSectionRow(s);
        for (int r = s.getLastRowNum(); r >= start; r--) {
            XSSFRow row = s.getRow(r);
            if (row != null) {
                s.removeRow(row);
            }
        }
        int r = start;
        for (String section : options.sectionOrder()) {
            r = section(s, r, section, input.items(), months) + 1;
        }
        splitTable(s, r + 1, input.splits());
    }

    /** The first row below the entertainment block: rows that link to Cashbook stay, everything after is rewritten. */
    private int firstSectionRow(XSSFSheet s) {
        int last = 0;
        for (int r = 0; r <= Math.min(s.getLastRowNum(), MONTH_LABEL_ROWS); r++) {
            XSSFRow row = s.getRow(r);
            if (row != null && row.getCell(COL_C) != null && row.getCell(COL_A) != null
                    && row.getCell(COL_A).getCellType() == CellType.FORMULA) {
                last = r;
            }
        }
        return last + 2;
    }

    private int section(XSSFSheet s, int r, String section, List<SectionItem> all, List<YearMonth> months) {
        List<SectionItem> items = all.stream().filter(i -> i.section().equals(section)).toList();
        boolean monthly = options.monthlySubtotalSections().contains(section);
        text(s, r, COL_A, section).setCellStyle(boldStyle);
        if (options.noGstSections().contains(section)) {
            text(s, r, COL_C, NO_GST);
        }
        r++;
        int start = r;
        List<String> subtotals = new ArrayList<>();
        for (int m = 0; m < months.size(); m++) {
            YearMonth month = months.get(m);
            List<SectionItem> inMonth = items.stream().filter(i -> i.month().equals(month))
                    .sorted(Comparator.comparing(SectionItem::date)).toList();
            if (inMonth.isEmpty()) {
                continue;
            }
            int first = r;
            for (int i = 0; i < inMonth.size(); i++) {
                SectionItem item = inMonth.get(i);
                if (i == 0 && monthly) {
                    replace(s, r, COL_A).setCellFormula(monthLabelRef(m));
                } else {
                    dateCell(s, r, COL_A, item.date());
                }
                if (section.equals(CAR_PARK_SECTION)) {
                    dateCell(s, r, COL_B, item.date());
                } else {
                    text(s, r, COL_B, item.description());
                }
                money(s, r, COL_C, item.amount());
                text(s, r, 4, item.note());
                r++;
            }
            if (monthly) {
                Cell sub = replace(s, r, COL_D);
                sub.setCellFormula("SUM(C" + (first + 1) + ":C" + r + ")");
                sub.setCellStyle(moneyStyle);
                subtotals.add("D" + (r + 1));
                r++;
            }
        }
        text(s, r, COL_B, TOTAL).setCellStyle(boldStyle);
        Cell total = replace(s, r, COL_D);
        total.setCellFormula(monthly ? (subtotals.isEmpty() ? "0" : String.join("+", subtotals))
                : "SUM(C" + (start + 1) + ":C" + Math.max(start + 1, r) + ")");
        total.setCellStyle(moneyStyle);
        return r + 1;
    }

    private void splitTable(XSSFSheet s, int r, List<UtilitySplit> splits) {
        text(s, r, COL_A, options.splitTitle()).setCellStyle(boldStyle);
        r++;
        String[] headers = {"Card date", "Invoice paid", "Card amount", "Power", "Card fee", "Power (725)",
            "Internet (727)", "Check"};
        for (int i = 0; i < headers.length; i++) {
            text(s, r, i, headers[i]).setCellStyle(boldStyle);
        }
        r++;
        int first = r;
        List<String> footnotes = new ArrayList<>();
        for (UtilitySplit split : splits.stream().sorted(Comparator.comparing(UtilitySplit::cardDate)).toList()) {
            int x = r + 1;
            dateCell(s, r, 0, split.cardDate());
            text(s, r, 1, split.invoiceDate().format(NUMERIC) + (split.footnote() == null ? "" : " *"));
            money(s, r, 2, split.card());
            money(s, r, 3, split.power());
            money(s, r, 4, split.fee());
            replace(s, r, 5).setCellFormula("D" + x + "+E" + x);
            money(s, r, 6, split.internet());
            replace(s, r, 7).setCellFormula("ROUND(C" + x + "-F" + x + "-G" + x + ",2)");
            if (split.footnote() != null) {
                footnotes.add(split.footnote());
            }
            r++;
        }
        for (int c = 2; c <= 7 && r > first; c++) {
            String col = CellReference.convertNumToColString(c);
            Cell sum = replace(s, r, c);
            sum.setCellFormula("SUM(" + col + (first + 1) + ":" + col + r + ")");
            sum.setCellStyle(moneyStyle);
        }
        r += 2;
        for (String note : footnotes) {
            text(s, r++, COL_A, note);
        }
        text(s, r, COL_A, "Card fee = the card payment surcharge, grouped with Power.");
    }

    /** The cell holding month {@code m}'s label (e.g. G1 = TEXT(H1, ...)), found from the DATE cells in H. */
    private String monthLabelRef(int m) {
        XSSFSheet s = sheet(options.otherSheet());
        int found = 0;
        for (int r = 0; r <= MONTH_LABEL_ROWS; r++) {
            Cell h = TemplateModel.cell(s, r, TemplateModel.COL_H);
            if (h != null && h.getCellType() == CellType.FORMULA && DATE_FORMULA.matcher(h.getCellFormula()).matches()) {
                if (found == m) {
                    return "G" + (r + 1);
                }
                found++;
            }
        }
        return "\"" + months().get(m) + "\"";
    }

    // ---------------------------------------------------------------- period labels

    private void labels(XSSFSheet cashbook, XSSFSheet cashPosition, XSSFSheet other, List<YearMonth> months) {
        LocalDate end = period.lastDay();
        relabel(cashbook, PERIOD_ENDING, end.format(NUMERIC));
        relabel(cashPosition, AS_AT, end.format(AS_AT_DATE));
        int m = 0;
        for (int r = 0; r <= MONTH_LABEL_ROWS && m < months.size(); r++) {
            Cell h = TemplateModel.cell(other, r, TemplateModel.COL_H);
            if (h != null && h.getCellType() == CellType.FORMULA && DATE_FORMULA.matcher(h.getCellFormula()).matches()) {
                YearMonth month = months.get(m++);
                h.setCellFormula("DATE(" + month.getYear() + ", " + month.getMonthValue() + ", 1)");
            }
        }
    }

    private void relabel(XSSFSheet s, Pattern pattern, String date) {
        for (int r = 0; r <= Math.min(s.getLastRowNum(), MONTH_LABEL_ROWS); r++) {
            XSSFRow row = s.getRow(r);
            if (row == null) {
                continue;
            }
            for (Cell c : row) {
                if (c.getCellType() == CellType.STRING) {
                    Matcher m = pattern.matcher(c.getStringCellValue());
                    if (m.matches()) {
                        c.setCellValue(m.group(1) + date);
                        return;
                    }
                }
            }
        }
        notes.add("no '" + pattern.pattern() + "' label found on " + s.getSheetName() + "; left as it is");
    }

    // ---------------------------------------------------------------- helpers

    private List<YearMonth> months() {
        List<YearMonth> months = new ArrayList<>();
        for (YearMonth m = period.start(); !m.isAfter(period.end()); m = m.plusMonths(1)) {
            months.add(m);
        }
        return months;
    }

    private XSSFSheet sheet(String name) {
        XSSFSheet s = wb.getSheet(name);
        if (s == null) {
            throw new TemplateModel.TemplateException("the template has no sheet named '" + name + "'");
        }
        return s;
    }

    private void money(XSSFSheet s, int r, int c, BigDecimal amount) {
        Cell cell = replace(s, r, c);
        cell.setCellValue(amount.doubleValue());
        cell.setCellStyle(moneyStyle);
    }

    private void dateCell(XSSFSheet s, int r, int c, LocalDate date) {
        if (date == null) {
            remove(s, r, c);
            return;
        }
        Cell cell = replace(s, r, c);
        cell.setCellValue(date);
        cell.setCellStyle(dateStyle);
    }

    private static Cell text(XSSFSheet s, int r, int c, String value) {
        Cell cell = replace(s, r, c);
        cell.setCellValue(value == null ? "" : value);
        return cell;
    }

    /** Removes any existing cell (so a formula does not survive) and returns a fresh one with the old style. */
    private static Cell replace(XSSFSheet s, int r, int c) {
        XSSFRow row = s.getRow(r) == null ? s.createRow(r) : s.getRow(r);
        XSSFCell old = row.getCell(c);
        CellStyle style = old == null ? null : old.getCellStyle();
        if (old != null) {
            row.removeCell(old);
        }
        XSSFCell cell = row.createCell(c);
        if (style != null) {
            cell.setCellStyle(style);
        }
        return cell;
    }

    private static void remove(XSSFSheet s, int r, int c) {
        XSSFRow row = s.getRow(r);
        if (row != null && row.getCell(c) != null) {
            row.removeCell(row.getCell(c));
        }
    }
}

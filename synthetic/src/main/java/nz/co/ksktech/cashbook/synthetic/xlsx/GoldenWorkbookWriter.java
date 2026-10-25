package nz.co.ksktech.cashbook.synthetic.xlsx;

import static nz.co.ksktech.cashbook.synthetic.xlsx.TemplateBuilder.cell;
import static nz.co.ksktech.cashbook.synthetic.xlsx.TemplateBuilder.set;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import nz.co.ksktech.cashbook.core.GstTreatment;
import nz.co.ksktech.cashbook.synthetic.Cents;
import nz.co.ksktech.cashbook.synthetic.Codes;
import nz.co.ksktech.cashbook.synthetic.Fictional;
import nz.co.ksktech.cashbook.synthetic.model.GoldenSpec;
import nz.co.ksktech.cashbook.synthetic.model.GoldenSpec.Item;
import nz.co.ksktech.cashbook.synthetic.model.GoldenSpec.Section;
import nz.co.ksktech.cashbook.synthetic.model.SpecLine;
import nz.co.ksktech.cashbook.synthetic.model.SplitRow;
import nz.co.ksktech.cashbook.synthetic.model.SyntheticWorld;
import nz.co.ksktech.cashbook.synthetic.xlsx.TemplateLayout.MonthBlock;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Writes the golden cashbook: the sanitised template filled in by hand-style rules, independent of the
 * M5 exporter. Duplicate analysis columns are cleared so every row check is 0, and OtherCalculation holds
 * the itemised sections and the utility split table, as in the real hand-checked cashbook.
 */
public final class GoldenWorkbookWriter {

    /** Section headers in OtherCalculation; each section ends with a "Total" row (amount in column D). */
    public static final Map<Section, String> SECTION_TITLES = Map.of(
            Section.INSURANCE, "Insurance",
            Section.NO_GST_SUBSCRIPTIONS, "Internet Subscriptions",
            Section.CAR_PARKS, "Car Park Fees",
            Section.BUSINESS_EXPENSES, "Business Expenses");
    public static final String TOTAL_LABEL = "Total";
    public static final String SPLIT_TITLE = "Fibrewave - Power / Internet split (paid on Kauri Rewards Mastercard)";

    private static final List<String> ROW_ORDER = List.of(Codes.INCOME, Codes.POWER, Codes.INTERNET, Codes.MOBILE,
            Codes.CAR_PARKING, Codes.HOME_OFFICE, Codes.APARTMENT_RENT, Codes.BANK_FEES, Codes.ACCOUNTANCY,
            Codes.RATES_BODY_CORP, Codes.NO_GST_PURCHASES, Codes.CONTENTS_INSURANCE, Codes.INDEMNITY_INSURANCE);
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM", Locale.ROOT);
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT);
    private static final int FIRST_SECTION_ROW = 12;

    private final SyntheticWorld world;
    private final Map<String, SpecLine> lines;
    private final XSSFWorkbook wb;
    private final CellStyle date;
    private final CellStyle money;
    private final CellStyle bold;

    private GoldenWorkbookWriter(SyntheticWorld world, Map<String, SpecLine> lines, XSSFWorkbook wb) {
        this.world = world;
        this.lines = lines;
        this.wb = wb;
        date = wb.createCellStyle();
        date.setDataFormat(wb.createDataFormat().getFormat("dd/mm/yyyy"));
        money = wb.createCellStyle();
        money.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
        Font font = wb.createFont();
        font.setBold(true);
        bold = wb.createCellStyle();
        bold.setFont(font);
    }

    public static void write(SyntheticWorld world, Map<String, SpecLine> lines, Path file) {
        try (XSSFWorkbook wb = TemplateBuilder.build()) {
            GoldenWorkbookWriter w = new GoldenWorkbookWriter(world, lines, wb);
            XSSFSheet cashbook = wb.getSheet(TemplateLayout.CASHBOOK);
            w.clearDuplicateColumns(cashbook);
            for (MonthBlock block : TemplateLayout.MONTHS) {
                w.fillMonth(cashbook, block);
            }
            w.otherCalculation(wb.getSheet(TemplateLayout.OTHER));
            Workbooks.save(wb, file);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private void clearDuplicateColumns(XSSFSheet s) {
        for (String col : TemplateLayout.DUPLICATE_COLUMNS) {
            for (int row = TemplateLayout.FIRST_FORMULA_ROW; row <= TemplateLayout.TOTALS_ROW; row++) {
                remove(s, col + row);
            }
        }
    }

    private void fillMonth(XSSFSheet s, MonthBlock block) {
        List<GoldenSpec> month = world.golden().stream().filter(g -> g.month().equals(block.month())).toList();
        List<GoldenSpec> regular = month.stream()
                .filter(g -> g.section() != Section.ENTERTAINMENT)
                .sorted(Comparator.comparingInt(g -> ROW_ORDER.indexOf(g.code())))
                .toList();
        List<Integer> rows = IntStream.rangeClosed(block.firstRow(), block.lastRow())
                .filter(r -> r != block.entertainmentRow()).boxed().toList();
        if (regular.size() > rows.size()) {
            throw new IllegalStateException(block.month() + " has " + regular.size() + " lines but the template block "
                    + "has " + rows.size() + " rows");
        }
        for (int i = 0; i < rows.size(); i++) {
            int row = rows.get(i);
            if (i < regular.size()) {
                writeLine(s, row, regular.get(i));
            } else {
                remove(s, "A" + row);
                remove(s, "B" + row);
                remove(s, "C" + row);
                replace(s, "G" + row).setCellValue(0);
            }
        }
        GoldenSpec fun = month.stream().filter(g -> g.section() == Section.ENTERTAINMENT).findFirst().orElseThrow();
        int row = block.entertainmentRow();
        dateCell(s, "A" + row, firstDate(fun));
        set(s, "C" + row, fun.detail());
        int link = 5 + TemplateLayout.MONTHS.indexOf(block);
        XSSFSheet other = wb.getSheet(TemplateLayout.OTHER);
        String dates = fun.items().stream().map(it -> it.date().format(DAY_MONTH)).collect(Collectors.joining(", "));
        set(other, "B" + link, "Business lunch/coffee - " + dates);
        amount(other, "C" + link, -fun.cents());
    }

    private void writeLine(XSSFSheet s, int row, GoldenSpec g) {
        dateCell(s, "A" + row, firstDate(g));
        replace(s, "B" + row).setCellValue(Integer.parseInt(g.code()));
        set(s, "C" + row, g.detail());
        amount(s, "G" + row, g.cents());
        if (g.gst() == GstTreatment.EXEMPT) {
            remove(s, "I" + row);
        } else if (g.gst() == GstTreatment.NO_GST) {
            replace(s, "I" + row).setCellValue(0);
        } else {
            replace(s, "I" + row).setCellFormula("IF(G" + row + "/23*3<0,SUM(G" + row + "/23*3),\"\")");
        }
    }

    private LocalDate firstDate(GoldenSpec g) {
        return g.sourceIds().stream().map(lines::get).map(SpecLine::date).min(Comparator.naturalOrder()).orElseThrow();
    }

    // ---------------------------------------------------------------- OtherCalculation

    private void otherCalculation(XSSFSheet s) {
        for (int r = s.getLastRowNum(); r >= FIRST_SECTION_ROW - 1; r--) {
            XSSFRow row = s.getRow(r);
            if (row != null) {
                s.removeRow(row);
            }
        }
        int r = FIRST_SECTION_ROW;
        r = section(s, r, Section.INSURANCE, false) + 1;
        set(s, "C" + r, "NO GST");
        r = section(s, r, Section.NO_GST_SUBSCRIPTIONS, true) + 1;
        r = section(s, r, Section.CAR_PARKS, true) + 1;
        r = section(s, r, Section.BUSINESS_EXPENSES, true) + 2;
        splitTable(s, r);
    }

    /** Writes one section; returns the row after it. Car-park items show the date in column B. */
    private int section(XSSFSheet s, int r, Section section, boolean monthlySubtotals) {
        set(s, "A" + r, SECTION_TITLES.get(section)).setCellStyle(bold);
        r++;
        List<String> subtotalCells = new ArrayList<>();
        int sectionStart = r;
        for (int m = 0; m < TemplateLayout.MONTHS.size(); m++) {
            YearMonth month = TemplateLayout.MONTHS.get(m).month();
            List<Item> items = world.golden().stream()
                    .filter(g -> g.section() == section && g.month().equals(month))
                    .flatMap(g -> g.items().stream())
                    .toList();
            if (items.isEmpty()) {
                continue;
            }
            int first = r;
            for (int i = 0; i < items.size(); i++) {
                Item item = items.get(i);
                if (i == 0 && monthlySubtotals) {
                    cell(s, "A" + r).setCellFormula("G" + (m + 1)); // the month label, e.g. Oct'2026
                } else {
                    dateCell(s, "A" + r, item.date());
                }
                if (section == Section.CAR_PARKS) {
                    dateCell(s, "B" + r, item.date());
                } else {
                    set(s, "B" + r, item.description());
                }
                amount(s, "C" + r, item.cents());
                set(s, "E" + r, item.note());
                r++;
            }
            if (monthlySubtotals) {
                XSSFCell sub = cell(s, "D" + r);
                sub.setCellFormula("SUM(C" + first + ":C" + (r - 1) + ")");
                sub.setCellStyle(money);
                subtotalCells.add("D" + r);
                r++;
            }
        }
        set(s, "B" + r, TOTAL_LABEL).setCellStyle(bold);
        XSSFCell total = cell(s, "D" + r);
        total.setCellFormula(monthlySubtotals ? String.join("+", subtotalCells)
                : "SUM(C" + sectionStart + ":C" + (r - 1) + ")");
        total.setCellStyle(money);
        return r + 1;
    }

    private void splitTable(XSSFSheet s, int r) {
        set(s, "A" + r, SPLIT_TITLE).setCellStyle(bold);
        r++;
        String[] headers = {"Card date", "Invoice paid", "Card amount", "Power", "Card fee", "Power (725)",
            "Internet (727)", "Check"};
        for (int i = 0; i < headers.length; i++) {
            set(s, CellReference.convertNumToColString(i) + r, headers[i]).setCellStyle(bold);
        }
        r++;
        int first = r;
        List<String> footnotes = new ArrayList<>();
        for (SplitRow row : world.split()) {
            dateCell(s, "A" + r, row.cardDate());
            set(s, "B" + r, row.invoiceDate().format(FULL) + (row.footnote() == null ? "" : " *"));
            amount(s, "C" + r, row.cardCents());
            amount(s, "D" + r, row.powerCents());
            amount(s, "E" + r, row.feeCents());
            cell(s, "F" + r).setCellFormula("D" + r + "+E" + r);
            amount(s, "G" + r, row.internetCents());
            cell(s, "H" + r).setCellFormula("ROUND(C" + r + "-F" + r + "-G" + r + ",2)");
            if (row.footnote() != null) {
                footnotes.add(row.footnote());
            }
            r++;
        }
        for (String col : List.of("C", "D", "E", "F", "G", "H")) {
            XSSFCell sum = cell(s, col + r);
            sum.setCellFormula("SUM(" + col + first + ":" + col + (r - 1) + ")");
            sum.setCellStyle(money);
        }
        r += 2;
        for (String note : footnotes) {
            set(s, "A" + r++, note);
        }
        set(s, "A" + r, "Card fee = " + Fictional.FIBREWAVE + " card payment fee, billed on the next invoice and "
                + "grouped with Power.");
    }

    // ---------------------------------------------------------------- cell helpers

    private void amount(XSSFSheet s, String ref, long cents) {
        XSSFCell c = replace(s, ref);
        c.setCellValue(Cents.toMoney(cents).doubleValue());
        c.setCellStyle(money);
    }

    private void dateCell(XSSFSheet s, String ref, LocalDate value) {
        XSSFCell c = replace(s, ref);
        c.setCellValue(value);
        c.setCellStyle(date);
    }

    /** Removes any existing cell (so a formula does not survive) and returns a fresh one. */
    private static XSSFCell replace(XSSFSheet s, String ref) {
        remove(s, ref);
        return cell(s, ref);
    }

    private static void remove(XSSFSheet s, String ref) {
        CellReference r = new CellReference(ref);
        XSSFRow row = s.getRow(r.getRow());
        if (row != null && row.getCell(r.getCol()) != null) {
            row.removeCell(row.getCell(r.getCol()));
        }
    }
}

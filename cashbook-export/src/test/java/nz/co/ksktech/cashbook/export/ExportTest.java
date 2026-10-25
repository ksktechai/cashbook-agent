package nz.co.ksktech.cashbook.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import nz.co.ksktech.cashbook.core.CashbookLine;
import nz.co.ksktech.cashbook.core.GstTreatment;
import nz.co.ksktech.cashbook.core.Period;
import nz.co.ksktech.cashbook.synthetic.Scenario;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExportTest {

    private static final ExportOptions OPTIONS = ExportOptions.defaults();

    @Test
    void syntheticPeriodExportsBalancesAndMatchesTheGolden() throws IOException {
        try (XSSFWorkbook wb = open(Pipeline.template()); XSSFWorkbook golden = open(Pipeline.golden())) {
            CashbookExporter.Result result = CashbookExporter.fill(wb, Pipeline.reconciled(), Scenario.PERIOD, OPTIONS);

            assertThat(ExportChecks.run(wb, OPTIONS)).as("assertion failures").isEmpty();
            assertThat(result.insertedRows()).isZero();
            assertThat(result.notes()).anySatisfy(n -> assertThat(n).contains("AG4 says 735 but column AG collects code 705"));
            assertThat(result.notes()).anySatisfy(n -> assertThat(n).contains("G95 held a formula"));
            assertThat(blocks(wb)).isEqualTo(blocks(golden));
        }
    }

    @Test
    void rowsAreInsertedWhenAMonthOverflowsAndEveryReferenceFollows() throws IOException {
        List<CashbookLine> lines = new ArrayList<>(Pipeline.reconciled().lines());
        CashbookLine template = lines.stream().filter(l -> l.code().equals("734")).findFirst().orElseThrow();
        for (int i = 1; i <= 4; i++) {
            lines.add(new CashbookLine(YearMonth.of(2026, 11), LocalDate.of(2026, 11, i), "734", "Extra item " + i,
                    new BigDecimal("-" + (10 * i) + ".00"), template.sources(), GstTreatment.STANDARD));
        }
        ExportInput bigger = new ExportInput(lines, Pipeline.reconciled().items(), Pipeline.reconciled().splits());
        try (XSSFWorkbook wb = open(Pipeline.template())) {
            String before = wb.getSheet("Cash Pos").getRow(6).getCell(4).getCellFormula();

            CashbookExporter.Result result = CashbookExporter.fill(wb, bigger, Scenario.PERIOD, OPTIONS);

            assertThat(result.insertedRows()).isEqualTo(4);
            assertThat(before).isEqualTo("Cashbook!J105");
            assertThat(wb.getSheet("Cash Pos").getRow(6).getCell(4).getCellFormula()).isEqualTo("Cashbook!J109");
            // the Business Expenses section won't include the extra lines, so only that check may fail
            assertThat(ExportChecks.run(wb, OPTIONS)).allSatisfy(f -> assertThat(f).startsWith("Business Expenses"));
        }
    }

    @Test
    void layoutIsUnchangedApartFromTheFilledCells() throws IOException {
        try (XSSFWorkbook template = open(Pipeline.template()); XSSFWorkbook wb = open(Pipeline.template())) {
            CashbookExporter.fill(wb, Pipeline.reconciled(), Scenario.PERIOD, OPTIONS);

            assertThat(names(wb)).isEqualTo(names(template));
            for (int i = 0; i < template.getNumberOfSheets(); i++) {
                XSSFSheet a = template.getSheetAt(i);
                XSSFSheet b = wb.getSheetAt(i);
                assertThat(regions(b)).as(a.getSheetName() + " merged regions").isEqualTo(regions(a));
                for (int c = 0; c < 48; c++) {
                    assertThat(b.getColumnWidth(c)).as(a.getSheetName() + " column " + c).isEqualTo(a.getColumnWidth(c));
                }
            }
            XSSFSheet a = template.getSheet("Cashbook");
            XSSFSheet b = wb.getSheet("Cashbook");
            for (int r : new int[] {0, 3, 4, 5}) {
                for (Cell c : a.getRow(r)) {
                    assertThat(String.valueOf(b.getRow(r).getCell(c.getColumnIndex())))
                            .as("Cashbook %s", c.getAddress()).isEqualTo(String.valueOf(c));
                }
            }
            assertThat(b.getLastRowNum()).isEqualTo(a.getLastRowNum());
        }
    }

    @Test
    void periodLabelsFollowTheExportedPeriod() throws IOException {
        Period aprSep = Period.parse("2026-04:2026-09");
        ExportInput shifted = new ExportInput(Pipeline.reconciled().lines().stream()
                .map(l -> new CashbookLine(l.month().minusMonths(6), l.date().minusMonths(6), l.code(), l.detail(), l.amount(),
                        l.sources(), l.gst())).toList(), List.of(), List.of());
        try (XSSFWorkbook wb = open(Pipeline.template())) {
            CashbookExporter.fill(wb, shifted, aprSep, OPTIONS);

            assertThat(wb.getSheet("Cashbook").getRow(2).getCell(0).getStringCellValue())
                    .isEqualTo("Cashbook for period ending 30/09/2026");
            assertThat(wb.getSheet("Cash Pos").getRow(1).getCell(0).getStringCellValue())
                    .isEqualTo("Cash Position as at 30 Sep 2026");
            assertThat(wb.getSheet("OtherCalculation").getRow(0).getCell(7).getCellFormula()).isEqualTo("DATE(2026, 4, 1)");
            assertThat(wb.getSheet("OtherCalculation").getRow(5).getCell(7).getCellFormula()).isEqualTo("DATE(2026, 9, 1)");
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Each month block as a sorted list of "code amount", evaluated. */
    private static List<List<String>> blocks(XSSFWorkbook wb) {
        XSSFFormulaEvaluator eval = wb.getCreationHelper().createFormulaEvaluator();
        XSSFSheet s = wb.getSheet("Cashbook");
        TemplateModel model = TemplateModel.inspect(s, OPTIONS.entertainmentCode());
        List<List<String>> out = new ArrayList<>();
        for (TemplateModel.Block b : model.blocks) {
            List<String> rows = new ArrayList<>();
            for (int r = b.firstRow(); r <= b.lastRow(); r++) {
                Cell code = TemplateModel.cell(s, r, TemplateModel.COL_B);
                CellValue g = eval.evaluate(TemplateModel.cell(s, r, TemplateModel.COL_G));
                double amount = g == null || g.getCellType() != CellType.NUMERIC ? 0 : g.getNumberValue();
                if (code != null && Math.abs(amount) >= 0.005) {
                    rows.add(TemplateModel.text(code).orElse("?") + " " + String.format(java.util.Locale.ROOT, "%.2f", amount));
                }
            }
            out.add(rows.stream().sorted().collect(Collectors.toList()));
        }
        return out;
    }

    private static List<String> names(XSSFWorkbook wb) {
        List<String> names = new ArrayList<>();
        wb.forEach(s -> names.add(s.getSheetName()));
        return names;
    }

    private static List<String> regions(XSSFSheet s) {
        return s.getMergedRegions().stream().map(CellRangeAddress::formatAsString).sorted().toList();
    }

    private static XSSFWorkbook open(Path file) throws IOException {
        Pipeline.reconciled();
        try (InputStream in = Files.newInputStream(file)) {
            return new XSSFWorkbook(in);
        }
    }
}

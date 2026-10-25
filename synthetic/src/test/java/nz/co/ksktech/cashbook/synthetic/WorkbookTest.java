package nz.co.ksktech.cashbook.synthetic;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import nz.co.ksktech.cashbook.synthetic.model.GoldenSpec.Section;
import nz.co.ksktech.cashbook.synthetic.xlsx.GoldenWorkbookWriter;
import nz.co.ksktech.cashbook.synthetic.xlsx.TemplateLayout;
import nz.co.ksktech.cashbook.synthetic.xlsx.Workbooks;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class WorkbookTest {

    private static final double CENT = 0.005;

    @Test
    void committedTemplateMatchesTheGenerator() throws IOException {
        Path committed = Path.of("src/test/resources/template.xlsx");
        assertThat(committed).as("run ./gradlew :synthetic:updateTemplate").exists();
        assertThat(entries(committed)).as("template drifted: run ./gradlew :synthetic:updateTemplate")
                .isEqualTo(entries(Generated.out().resolve(SyntheticGenerator.TEMPLATE)));
    }

    @Test
    void templateKeepsTheRealTemplatesQuirks() throws IOException {
        try (XSSFWorkbook wb = Workbooks.open(Generated.out().resolve(SyntheticGenerator.TEMPLATE))) {
            XSSFSheet s = wb.getSheet(TemplateLayout.CASHBOOK);
            assertThat(wb.getSheetAt(0).getSheetName()).isEqualTo("Instructions");
            assertThat(cell(s, "J4").getCellType()).isEqualTo(CellType.STRING);
            assertThat(cell(s, "X4").getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(cell(s, "AG4").getNumericCellValue()).isEqualTo(735);
            assertThat(cell(s, "AG7").getCellFormula()).contains("=705");
            assertThat(cell(s, "AK7").getCellFormula()).contains("=725");
            assertThat(cell(s, "I16")).as("no GST Expense formula on the exempt rent row").isNull();
            assertThat(cell(s, "I20").getNumericCellValue()).as("typed zero on no-GST rows").isZero();
            assertThat(cell(s, "G95").getCellFormula()).isEqualTo("-(" + TemplateLayout.STRAY_LINK_TARGET + ")");
            assertThat(cell(s, "Y5").getStringCellValue()).isEqualTo("Entertaintment");
            assertThat(cell(wb.getSheet(TemplateLayout.CASH_POSITION), "B35").getCellFormula()).isEqualTo("Cashbook!H5");
        }
    }

    @Test
    void goldenWorkbookBalances() throws IOException {
        try (XSSFWorkbook wb = Workbooks.open(Generated.out().resolve(SyntheticGenerator.GOLDEN_XLSX))) {
            XSSFFormulaEvaluator eval = wb.getCreationHelper().createFormulaEvaluator();
            eval.evaluateAll();
            XSSFSheet cb = wb.getSheet(TemplateLayout.CASHBOOK);
            for (int row = 7; row <= TemplateLayout.LAST_DATA_ROW; row++) {
                Cell check = cell(cb, "F" + row);
                if (check != null) {
                    assertThat(number(eval, check)).as("row check F%d", row).isCloseTo(0, within());
                }
            }
            assertThat(number(eval, cell(wb.getSheet(TemplateLayout.CASH_POSITION), "E48")))
                    .as("cash position difference").isCloseTo(0, within());

            Map<String, Double> codeTotals = codeTotals(eval, cb);
            XSSFSheet other = wb.getSheet(TemplateLayout.OTHER);
            Map<String, Double> sections = sectionTotals(eval, other);
            assertThat(sections.get("Insurance")).isCloseTo(-(codeTotals.get("730") + codeTotals.get("732")), within());
            assertThat(sections.get("Internet Subscriptions")).isCloseTo(-codeTotals.get("739"), within());
            assertThat(sections.get("Car Park Fees")).isCloseTo(-codeTotals.get("729"), within());
            assertThat(sections.get("Business Expenses")).isCloseTo(-codeTotals.get("734"), within());
            double entertainment = 0;
            for (int row = 5; row <= 10; row++) {
                entertainment += number(eval, cell(other, "C" + row));
            }
            assertThat(entertainment).isCloseTo(-codeTotals.get("726"), within());
        }
    }

    @Test
    void sectionTitlesAreAllWritten() throws IOException {
        try (XSSFWorkbook wb = Workbooks.open(Generated.out().resolve(SyntheticGenerator.GOLDEN_XLSX))) {
            Map<String, Double> sections = sectionTotals(wb.getCreationHelper().createFormulaEvaluator(),
                    wb.getSheet(TemplateLayout.OTHER));
            assertThat(sections.keySet()).containsAll(GoldenWorkbookWriter.SECTION_TITLES.entrySet().stream()
                    .filter(e -> e.getKey() != Section.NONE).map(Map.Entry::getValue).toList());
        }
    }

    // ---------------------------------------------------------------- helpers

    /** GST-inclusive Bank (G) totals by the code in column B. */
    private static Map<String, Double> codeTotals(XSSFFormulaEvaluator eval, XSSFSheet cb) {
        Map<String, Double> totals = new HashMap<>();
        for (int row = 7; row <= TemplateLayout.LAST_DATA_ROW; row++) {
            Cell code = cell(cb, "B" + row);
            if (code != null && code.getCellType() == CellType.NUMERIC) {
                totals.merge(Integer.toString((int) code.getNumericCellValue()), number(eval, cell(cb, "G" + row)),
                        Double::sum);
            }
        }
        return totals;
    }

    /** Section title -> the amount on its "Total" row. */
    private static Map<String, Double> sectionTotals(XSSFFormulaEvaluator eval, XSSFSheet other) {
        Map<String, Double> totals = new LinkedHashMap<>();
        String current = null;
        for (Row row : other) {
            Cell a = row.getCell(0);
            Cell b = row.getCell(1);
            if (a != null && a.getCellType() == CellType.STRING
                    && GoldenWorkbookWriter.SECTION_TITLES.containsValue(a.getStringCellValue())) {
                current = a.getStringCellValue();
            }
            if (current != null && b != null && b.getCellType() == CellType.STRING
                    && b.getStringCellValue().equals(GoldenWorkbookWriter.TOTAL_LABEL)) {
                totals.put(current, number(eval, row.getCell(3)));
                current = null;
            }
        }
        return totals;
    }

    private static double number(XSSFFormulaEvaluator eval, Cell cell) {
        if (cell == null) {
            return 0;
        }
        CellValue v = eval.evaluate(cell);
        return v == null || v.getCellType() != CellType.NUMERIC ? 0 : v.getNumberValue();
    }

    private static Cell cell(XSSFSheet s, String ref) {
        CellReference r = new CellReference(ref);
        Row row = s.getRow(r.getRow());
        return row == null ? null : row.getCell(r.getCol());
    }

    private static org.assertj.core.data.Offset<Double> within() {
        return org.assertj.core.data.Offset.offset(CENT);
    }

    /** Zip entry name -> SHA-256 of its content (entry order and timestamps are not compared). */
    private static Map<String, String> entries(Path xlsx) throws IOException {
        Map<String, String> entries = new java.util.TreeMap<>();
        try (InputStream in = Files.newInputStream(xlsx); ZipInputStream zip = new ZipInputStream(in)) {
            for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                entries.put(e.getName(), SyntheticGenerator.sha256(zip.readAllBytes()));
            }
        }
        return entries;
    }
}

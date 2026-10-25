package nz.co.ksktech.cashbook.export;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFSheet;

/**
 * The structure of the Cashbook sheet, read from the sheet itself rather than assumed: the analysis-code
 * header row, the totals row, the month blocks and each block's entertainment row (its Bank cell links to
 * OtherCalculation). Row numbers are 0-based POI indexes.
 */
public final class TemplateModel {

    static final Pattern CODE = Pattern.compile("^\\d{3}$");
    static final Pattern TESTED_CODE = Pattern.compile("\\$B\\d+=(\\d{3})");
    static final Pattern OTHER_LINK = Pattern.compile("^-\\(?(\\w+)!\\$?([A-Z]+)\\$?(\\d+)\\)?$");
    private static final int HEADER_SEARCH_ROWS = 15;
    private static final int MIN_HEADER_CODES = 5;
    static final int COL_A = 0;
    static final int COL_B = 1;
    static final int COL_C = 2;
    static final int COL_D = 3;
    static final int COL_F = 5;
    static final int COL_G = 6;
    static final int COL_H = 7;
    static final int COL_I = 8;

    /** One month's rows. {@code entertainmentRow} is -1 when the block has none. */
    public record Block(int firstRow, int lastRow, int entertainmentRow, String entertainmentLink) {

        Block shifted(int lastRowDelta) {
            return new Block(firstRow, lastRow + lastRowDelta, entertainmentRow, entertainmentLink);
        }
    }

    final int headerRow;
    final Map<String, Integer> headerCodes;
    final int totalsRow;
    final int firstDataRow;
    final List<Block> blocks;
    final List<String> notes;

    private TemplateModel(int headerRow, Map<String, Integer> headerCodes, int totalsRow, int firstDataRow,
            List<Block> blocks, List<String> notes) {
        this.headerRow = headerRow;
        this.headerCodes = headerCodes;
        this.totalsRow = totalsRow;
        this.firstDataRow = firstDataRow;
        this.blocks = blocks;
        this.notes = notes;
    }

    /** Thrown when the sheet does not look like the cashbook template. */
    public static final class TemplateException extends RuntimeException {
        TemplateException(String message) {
            super(message);
        }
    }

    public static TemplateModel inspect(XSSFSheet s, String entertainmentCode) {
        int header = -1;
        Map<String, Integer> codes = new LinkedHashMap<>();
        for (int r = 0; r < HEADER_SEARCH_ROWS && header < 0; r++) {
            Map<String, Integer> found = new LinkedHashMap<>();
            Row row = s.getRow(r);
            if (row == null) {
                continue;
            }
            for (Cell c : row) {
                text(c).filter(t -> CODE.matcher(t).matches()).ifPresent(t -> found.putIfAbsent(t, c.getColumnIndex()));
            }
            if (found.size() >= MIN_HEADER_CODES) {
                header = r;
                codes = found;
            }
        }
        if (header < 0) {
            throw new TemplateException("no header row of analysis codes in the first " + HEADER_SEARCH_ROWS + " rows");
        }
        int totals = -1;
        for (int r = header + 1; r <= s.getLastRowNum() && totals < 0; r++) {
            Cell g = cell(s, r, COL_G);
            if (g != null && g.getCellType() == CellType.FORMULA && g.getCellFormula().startsWith("SUM(")) {
                totals = r;
            }
        }
        if (totals < 0) {
            throw new TemplateException("no totals row (a SUM in the Bank column) below the header");
        }
        // data rows carry the running balance formula D{r} = D{r-1} + G{r}
        int firstData = -1;
        for (int r = header + 1; r < totals && firstData < 0; r++) {
            Cell d = cell(s, r, COL_D);
            if (d != null && d.getCellType() == CellType.FORMULA && d.getCellFormula().contains("+G")) {
                firstData = r;
            }
        }
        if (firstData < 0) {
            throw new TemplateException("no data rows with a running balance formula in column D");
        }
        List<Block> blocks = new ArrayList<>();
        int start = -1;
        for (int r = firstData; r <= totals; r++) {
            boolean used = r < totals && (hasValue(s, r, COL_B) || hasValue(s, r, COL_C));
            if (used && start < 0) {
                start = r;
            } else if (!used && start >= 0) {
                blocks.add(block(s, start, r - 1, entertainmentCode));
                start = -1;
            }
        }
        List<String> notes = new ArrayList<>();
        TemplateModel model = new TemplateModel(header, codes, totals, firstData, blocks, notes);
        model.checkCodes(s);
        return model;
    }

    private static Block block(XSSFSheet s, int first, int last, String entertainmentCode) {
        for (int r = first; r <= last; r++) {
            Cell g = cell(s, r, COL_G);
            Cell b = cell(s, r, COL_B);
            if (g != null && g.getCellType() == CellType.FORMULA && OTHER_LINK.matcher(g.getCellFormula()).matches()
                    && b != null && entertainmentCode.equals(text(b).orElse(""))) {
                return new Block(first, last, r, g.getCellFormula());
            }
        }
        return new Block(first, last, -1, null);
    }

    /**
     * Notes, across every data row, columns whose header disagrees with the code they collect, columns that
     * collect different codes in different rows, and codes collected by more than one column. Changes nothing.
     */
    private void checkCodes(XSSFSheet s) {
        // column -> code -> rows (1-based) where that column's formula tests that code
        Map<Integer, Map<String, List<Integer>>> byColumn = new java.util.TreeMap<>();
        for (int r = firstDataRow; r < totalsRow; r++) {
            Row row = s.getRow(r);
            if (row == null) {
                continue;
            }
            for (Cell c : row) {
                if (c.getCellType() == CellType.FORMULA) {
                    Matcher m = TESTED_CODE.matcher(c.getCellFormula());
                    if (m.find()) {
                        byColumn.computeIfAbsent(c.getColumnIndex(), k -> new java.util.TreeMap<>())
                                .computeIfAbsent(m.group(1), k -> new ArrayList<>()).add(r + 1);
                    }
                }
            }
        }
        Map<String, TreeSet<String>> columnsByCode = new LinkedHashMap<>();
        byColumn.forEach((index, codes) -> {
            String col = CellReference.convertNumToColString(index);
            codes.keySet().forEach(code -> columnsByCode.computeIfAbsent(code, k -> new TreeSet<>(this::byColumn)).add(col));
            if (codes.size() > 1) {
                List<String> parts = new ArrayList<>();
                codes.forEach((code, rows) -> parts.add(code + " in rows " + ranges(rows)));
                notes.add("column " + col + " collects different codes in different rows: " + String.join(", ", parts));
            }
            Cell h = cell(s, headerRow, index);
            Optional<String> label = h == null ? Optional.empty() : text(h);
            if (label.isPresent() && CODE.matcher(label.get()).matches() && !codes.containsKey(label.get())) {
                notes.add(col + (headerRow + 1) + " says " + label.get() + " but column " + col + " collects code "
                        + String.join(" and ", codes.keySet()));
            } else if (label.isPresent() && CODE.matcher(label.get()).matches() && codes.size() > 1) {
                notes.add(col + (headerRow + 1) + " says " + label.get() + " but only some of column " + col
                        + "'s rows collect it");
            }
        });
        columnsByCode.forEach((code, cols) -> {
            if (cols.size() > 1) {
                notes.add("code " + code + " is collected by " + String.join(", ", cols)
                        + " (in some rows at least); a line with that code would fail its row check");
            }
        });
    }

    private static String ranges(List<Integer> rows) {
        StringBuilder b = new StringBuilder();
        int start = rows.getFirst();
        int prev = start;
        for (int i = 1; i <= rows.size(); i++) {
            int r = i < rows.size() ? rows.get(i) : -1;
            if (r != prev + 1) {
                b.append(b.isEmpty() ? "" : ", ").append(start == prev ? Integer.toString(start) : start + "-" + prev);
                start = r;
            }
            prev = r;
        }
        return b.toString();
    }

    private int byColumn(String a, String b) {
        return Integer.compare(CellReference.convertColStringToIndex(a), CellReference.convertColStringToIndex(b));
    }

    static Cell cell(XSSFSheet s, int row, int col) {
        Row r = s.getRow(row);
        return r == null ? null : r.getCell(col);
    }

    private static boolean hasValue(XSSFSheet s, int row, int col) {
        Cell c = cell(s, row, col);
        return c != null && text(c).filter(t -> !t.isBlank()).isPresent();
    }

    /** A cell's text: strings as-is, whole numbers without ".0"; empty for blanks and formulas. */
    static Optional<String> text(Cell c) {
        return switch (c.getCellType()) {
            case STRING -> Optional.of(c.getStringCellValue().strip());
            case NUMERIC -> {
                double v = c.getNumericCellValue();
                yield Optional.of(v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v));
            }
            default -> Optional.empty();
        };
    }
}

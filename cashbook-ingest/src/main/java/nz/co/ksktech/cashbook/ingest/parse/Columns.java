package nz.co.ksktech.cashbook.ingest.parse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import nz.co.ksktech.cashbook.ingest.text.TextLine;
import nz.co.ksktech.cashbook.ingest.text.TextLine.Segment;
import nz.co.ksktech.cashbook.ingest.text.Word;

/**
 * Table columns read from a header line. Column i spans from its header's left edge to the next header's;
 * each cell (a {@link Segment}) goes to the column it overlaps most, which handles right-aligned amounts
 * that start a little left of their header.
 */
public final class Columns {

    public record Column(String name, float left) {}

    private final List<Column> columns;

    private Columns(List<Column> columns) {
        this.columns = List.copyOf(columns);
    }

    /** Finds every header name (single or multi-word) on the line, or empty if any is missing. */
    public static Optional<Columns> find(TextLine line, List<String> names) {
        List<Column> found = new ArrayList<>();
        for (String name : names) {
            Optional<Float> left = locate(line.words(), name.split(" "));
            if (left.isEmpty()) {
                return Optional.empty();
            }
            found.add(new Column(name, left.get()));
        }
        found.sort((a, b) -> Float.compare(a.left(), b.left()));
        return Optional.of(new Columns(found));
    }

    /** The first line on the list that carries every header name. */
    public static Optional<Columns> findIn(List<TextLine> lines, List<String> names) {
        return lines.stream().map(l -> find(l, names)).flatMap(Optional::stream).findFirst();
    }

    private static Optional<Float> locate(List<Word> words, String[] tokens) {
        for (int i = 0; i + tokens.length <= words.size(); i++) {
            boolean match = true;
            for (int j = 0; j < tokens.length && match; j++) {
                match = words.get(i + j).text().equals(tokens[j]);
            }
            if (match) {
                return Optional.of(words.get(i).box().left());
            }
        }
        return Optional.empty();
    }

    /**
     * Column name -> the line's text in that column ("" when empty). Cells in one column are joined. A cell
     * that mostly straddles two columns (two cells printed too close together) is split word by word.
     */
    public Map<String, String> cells(TextLine line) {
        Map<String, String> cells = new LinkedHashMap<>();
        columns.forEach(c -> cells.put(c.name(), ""));
        for (Segment s : line.segments()) {
            int best = bestColumn(s.box().left(), s.box().right());
            if (overlap(best, s.box().left(), s.box().right()) >= MOSTLY_ONE_COLUMN * s.box().width()) {
                append(cells, columns.get(best).name(), s.text());
                continue;
            }
            for (Word w : line.words()) {
                float centre = (w.box().left() + w.box().right()) / 2;
                if (centre >= s.box().left() && centre <= s.box().right()) {
                    append(cells, columns.get(bestColumn(centre, centre + 0.01f)).name(), w.text());
                }
            }
        }
        return cells;
    }

    /** Share of a cell's width that must fall in one column for it to stay whole. */
    private static final float MOSTLY_ONE_COLUMN = 0.6f;

    private static void append(Map<String, String> cells, String column, String text) {
        String existing = cells.get(column);
        cells.put(column, existing.isEmpty() ? text : existing + " " + text);
    }

    private int bestColumn(float left, float right) {
        int best = 0;
        float bestOverlap = -Float.MAX_VALUE;
        for (int i = 0; i < columns.size(); i++) {
            float o = overlap(i, left, right);
            if (o > bestOverlap) {
                bestOverlap = o;
                best = i;
            }
        }
        return best;
    }

    private float overlap(int i, float left, float right) {
        float start = i == 0 ? Float.NEGATIVE_INFINITY : columns.get(i).left();
        float end = i + 1 < columns.size() ? columns.get(i + 1).left() : Float.POSITIVE_INFINITY;
        return Math.min(end, right) - Math.max(start, left);
    }
}

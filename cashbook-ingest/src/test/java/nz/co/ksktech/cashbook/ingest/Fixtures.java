package nz.co.ksktech.cashbook.ingest;

import java.util.ArrayList;
import java.util.List;
import nz.co.ksktech.cashbook.core.BoundingBox;
import nz.co.ksktech.cashbook.ingest.text.Lines;
import nz.co.ksktech.cashbook.ingest.text.PageText;
import nz.co.ksktech.cashbook.ingest.text.TextLine;
import nz.co.ksktech.cashbook.ingest.text.Word;

/**
 * Builds pages from hand-placed text for parser tests: {@code cell(x, "text")} places a cell whose words
 * are laid out with Helvetica-like widths (0.55 em per character, 0.28 em spaces).
 */
public final class Fixtures {

    private Fixtures() {}

    public record Cell(float x, String text) {}

    public static Cell cell(float x, String text) {
        return new Cell(x, text);
    }

    /** One line's words at a baseline. */
    public static List<Word> row(float baseline, float size, Cell... cells) {
        List<Word> words = new ArrayList<>();
        for (Cell c : cells) {
            float x = c.x();
            for (String token : c.text().split(" ")) {
                float width = token.length() * 0.55f * size;
                float height = 0.7f * size;
                words.add(new Word(token, new BoundingBox(x, baseline - height, width, height), size));
                x += width + 0.28f * size;
            }
        }
        return words;
    }

    @SafeVarargs
    public static PageText page(int number, List<Word>... rows) {
        List<Word> all = new ArrayList<>();
        for (List<Word> r : rows) {
            all.addAll(r);
        }
        List<TextLine> lines = Lines.group(number, all);
        return new PageText(number, 595, 842, lines, false);
    }
}

package nz.co.ksktech.cashbook.ingest.text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import nz.co.ksktech.cashbook.core.BoundingBox;

/**
 * Groups words into lines. Two words share a line when their boxes overlap vertically by at least half
 * the smaller height; this works for PDF text and for OCR boxes (which include descenders), and keeps
 * wrapped or straddling lines a few points apart separate.
 */
public final class Lines {

    private static final float MIN_OVERLAP = 0.5f;

    private Lines() {}

    public static List<TextLine> group(int page, List<Word> words) {
        List<Word> sorted = words.stream()
                .sorted(Comparator.comparingDouble((Word w) -> w.box().top()).thenComparingDouble(w -> w.box().left()))
                .toList();
        List<List<Word>> groups = new ArrayList<>();
        List<BoundingBox> spans = new ArrayList<>();
        for (Word w : sorted) {
            int match = -1;
            for (int i = groups.size() - 1; i >= Math.max(0, groups.size() - 3) && match < 0; i--) {
                if (sameLine(spans.get(i), w.box())) {
                    match = i;
                }
            }
            if (match < 0) {
                groups.add(new ArrayList<>(List.of(w)));
                spans.add(w.box());
            } else {
                groups.get(match).add(w);
                spans.set(match, TextLine.union(List.of(spans.get(match), w.box())));
            }
        }
        return groups.stream()
                .map(g -> TextLine.of(page, g))
                .sorted(Comparator.comparingDouble(TextLine::baseline))
                .toList();
    }

    private static boolean sameLine(BoundingBox line, BoundingBox word) {
        float overlap = Math.min(line.bottom(), word.bottom()) - Math.max(line.top(), word.top());
        return overlap >= MIN_OVERLAP * Math.min(line.height(), word.height());
    }
}

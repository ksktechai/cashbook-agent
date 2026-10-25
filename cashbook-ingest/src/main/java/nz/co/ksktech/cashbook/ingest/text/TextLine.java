package nz.co.ksktech.cashbook.ingest.text;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import nz.co.ksktech.cashbook.core.BoundingBox;

/**
 * Words sharing a baseline, left to right. Segments are runs of words separated by no more than about a
 * space, so a table cell ("Money out", "CR $236.74") stays together while columns separate.
 */
public record TextLine(int page, BoundingBox box, List<Word> words) {

    /** A gap wider than this many font sizes starts a new segment. */
    private static final float SEGMENT_GAP_EM = 0.4f;

    public TextLine {
        words = List.copyOf(words);
    }

    public static TextLine of(int page, List<Word> words) {
        List<Word> sorted = words.stream().sorted((a, b) -> Float.compare(a.box().left(), b.box().left())).toList();
        return new TextLine(page, union(sorted.stream().map(Word::box).toList()), sorted);
    }

    public String text() {
        return words.stream().map(Word::text).collect(Collectors.joining(" "));
    }

    public float baseline() {
        return box.bottom();
    }

    /** Runs of words, each with its own box. */
    public List<Segment> segments() {
        List<Segment> segments = new ArrayList<>();
        List<Word> current = new ArrayList<>();
        for (Word w : words) {
            if (!current.isEmpty()) {
                Word last = current.getLast();
                float gap = w.box().left() - last.box().right();
                if (gap > SEGMENT_GAP_EM * Math.max(last.size(), w.size())) {
                    segments.add(Segment.of(current));
                    current = new ArrayList<>();
                }
            }
            current.add(w);
        }
        if (!current.isEmpty()) {
            segments.add(Segment.of(current));
        }
        return segments;
    }

    /** A run of words read as one cell. */
    public record Segment(String text, BoundingBox box) {

        static Segment of(List<Word> words) {
            return new Segment(words.stream().map(Word::text).collect(Collectors.joining(" ")),
                    union(words.stream().map(Word::box).toList()));
        }
    }

    public static BoundingBox union(List<BoundingBox> boxes) {
        float left = Float.MAX_VALUE;
        float top = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        float bottom = -Float.MAX_VALUE;
        for (BoundingBox b : boxes) {
            left = Math.min(left, b.left());
            top = Math.min(top, b.top());
            right = Math.max(right, b.right());
            bottom = Math.max(bottom, b.bottom());
        }
        return new BoundingBox(left, top, right - left, bottom - top);
    }
}

package nz.co.ksktech.cashbook.ingest.parse;

import static nz.co.ksktech.cashbook.ingest.Fixtures.cell;
import static nz.co.ksktech.cashbook.ingest.Fixtures.page;
import static nz.co.ksktech.cashbook.ingest.Fixtures.row;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import nz.co.ksktech.cashbook.core.BoundingBox;
import nz.co.ksktech.cashbook.ingest.text.Lines;
import nz.co.ksktech.cashbook.ingest.text.PageText;
import nz.co.ksktech.cashbook.ingest.text.TextLine;
import nz.co.ksktech.cashbook.ingest.text.Word;
import org.junit.jupiter.api.Test;

class LinesAndColumnsTest {

    @Test
    void descriptionLinesStraddlingARowStaySeparateLines() {
        PageText p = page(1,
                row(194, 10, cell(102, "AUTOMATIC PAYMENT J SAMPLE")),
                row(200, 10, cell(38, "1 Oct 2026"), cell(440, "+ $600.00")),
                row(206, 10, cell(102, "FLATSHARE UNIT4B")));

        assertThat(p.lines()).extracting(TextLine::text)
                .containsExactly("AUTOMATIC PAYMENT J SAMPLE", "1 Oct 2026 + $600.00", "FLATSHARE UNIT4B");
    }

    @Test
    void ocrWordsWithDescendersStayOnTheirLine() {
        // OCR boxes: "Gross" and "pay" (descender) have different bottoms but overlap vertically.
        List<Word> words = List.of(
                new Word("Gross", new BoundingBox(50, 100, 30, 8), 8),
                new Word("Amount", new BoundingBox(84, 100, 40, 8), 8),
                new Word("pay", new BoundingBox(130, 102, 18, 9), 9));

        assertThat(Lines.group(1, words)).singleElement().extracting(TextLine::text).isEqualTo("Gross Amount pay");
    }

    @Test
    void rightAlignedAmountsLandInTheirColumnAndTouchingCellsAreSplit() {
        TextLine header = page(1, row(232, 10, cell(32, "Card"), cell(60, "Date"), cell(124, "Description"),
                cell(381, "Amount"), cell(431, "Date Processed"), cell(513, "Balance"))).lines().getFirst();
        Columns columns = Columns.find(header, List.of("Card", "Date", "Description", "Amount", "Date Processed",
                "Balance")).orElseThrow();
        // "CR $236.74" starts left of the Amount header; "30 Sep 2026" runs right up to the description
        TextLine line = page(1, row(251, 10, cell(29, "7314"), cell(58, "30 Sep 2026"), cell(124, "PAYMENT RECEIVED visa"),
                cell(370, "CR $236.74"), cell(450, "1 Oct 2026"), cell(513, "$677.18"))).lines().getFirst();

        Map<String, String> cells = columns.cells(line);

        assertThat(cells).containsEntry("Card", "7314").containsEntry("Date", "30 Sep 2026")
                .containsEntry("Description", "PAYMENT RECEIVED visa").containsEntry("Amount", "CR $236.74")
                .containsEntry("Date Processed", "1 Oct 2026").containsEntry("Balance", "$677.18");
    }
}

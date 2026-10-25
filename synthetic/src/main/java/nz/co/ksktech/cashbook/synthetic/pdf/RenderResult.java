package nz.co.ksktech.cashbook.synthetic.pdf;

import java.util.List;

/** What a renderer produced for one file. */
public record RenderResult(String file, int pages, List<RenderedLine> lines) {

    public RenderResult {
        lines = List.copyOf(lines);
    }
}

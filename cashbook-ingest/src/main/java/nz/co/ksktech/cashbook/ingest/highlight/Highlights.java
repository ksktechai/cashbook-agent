package nz.co.ksktech.cashbook.ingest.highlight;

import java.util.List;
import nz.co.ksktech.cashbook.core.BoundingBox;

/** Decides whether a line, given its box and its cells' boxes, is highlighted. */
@FunctionalInterface
public interface Highlights {

    boolean highlighted(int page, BoundingBox line, List<BoundingBox> cells);
}

package nz.co.ksktech.cashbook.classify;

import nz.co.ksktech.cashbook.core.ReviewItem;

/** One line of review.jsonl: the classified line's id and the question. {@code item.answer} is null until answered. */
public record ReviewEntry(String id, ReviewItem item) {

    public boolean answered() {
        return item.answer() != null;
    }
}

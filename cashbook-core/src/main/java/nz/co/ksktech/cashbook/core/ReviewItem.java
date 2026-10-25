package nz.co.ksktech.cashbook.core;

import java.util.Objects;

/** A question for the human reviewer. {@code answer} is null until answered. */
public record ReviewItem(Transaction transaction, Classification proposed, String question, String answer) {

    public ReviewItem {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(question, "question");
    }
}

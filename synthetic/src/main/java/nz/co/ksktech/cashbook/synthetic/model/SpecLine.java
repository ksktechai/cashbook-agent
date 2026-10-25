package nz.co.ksktech.cashbook.synthetic.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/**
 * One synthetic statement line and its ground truth.
 *
 * @param date transaction (card) date
 * @param processed processed date; equals {@code date} for accounts that show one date
 * @param details second text column (Westpac "Details"), or "" when the layout has none
 * @param type Westpac "Type" column, or "" when the layout has none
 * @param cents signed NZD amount, negative = money out
 * @param foreign original currency, or null
 * @param fxFeeCents NZD conversion fee carried on this line, 0 when none
 * @param highlight highlighter colour, or null when not highlighted
 */
public record SpecLine(
        String id,
        LocalDate date,
        LocalDate processed,
        String description,
        String details,
        String type,
        long cents,
        ForeignSpec foreign,
        long fxFeeCents,
        HighlightColour highlight,
        Expected expected,
        Set<Trap> traps) {

    public SpecLine {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(processed, "processed");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(details, "details");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(expected, "expected");
        traps = Set.copyOf(traps);
    }

    public boolean highlighted() {
        return highlight != null;
    }
}

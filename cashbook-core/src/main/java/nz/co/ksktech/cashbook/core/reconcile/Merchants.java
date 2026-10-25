package nz.co.ksktech.cashbook.core.reconcile;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** The merchant part of a statement description. */
public final class Merchants {

    private static final Pattern STOP = Pattern.compile(".*[\\d*/].*");
    private static final int MAX_WORDS = 4;

    private Merchants() {}

    /** Leading words, stopping at one with digits, masks or slashes ("Park on Quay 412345****** 4821" -> "Park on Quay"). */
    public static String name(String description) {
        List<String> words = new ArrayList<>();
        for (String w : description.strip().split("\\s+")) {
            if (STOP.matcher(w).matches() || words.size() == MAX_WORDS) {
                break;
            }
            words.add(w);
        }
        return String.join(" ", words);
    }

    /** The merchant name, or the whole description when it starts with a number. */
    public static String label(String description) {
        String name = name(description);
        return name.isEmpty() ? description.strip() : name;
    }
}

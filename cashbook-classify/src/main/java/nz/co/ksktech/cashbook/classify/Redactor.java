package nz.co.ksktech.cashbook.classify;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Removes identifying numbers and addresses from text before it goes to an LLM: card and account numbers,
 * IRD/GST-style numbers, street addresses, and any run of five or more digits (references, customer numbers).
 */
public final class Redactor {

    public static final String MASK = "[REDACTED]";

    private static final List<Pattern> PATTERNS = List.of(
            // masked or full card numbers: "**** **** **** 1234", "XXXX-XXXX-XXXX-7314", "412345****** 4821"
            Pattern.compile("(?i)(?:[*X\\d]{4}[ -]?){3}[*X\\d]{1,4}"),
            Pattern.compile("\\d{4,6}\\*{4,}\\s*\\d{0,4}"),
            Pattern.compile("\\*{4,}\\s*\\d{4}"),
            // NZ bank accounts: 12-3456-7890123-00 / 12 3456 7890123 000
            Pattern.compile("\\b\\d{2}[- ]\\d{4}[- ]\\d{7}[- ]\\d{2,3}\\b"),
            // IRD and GST numbers: 123-456-789, 12-345-678
            Pattern.compile("\\b\\d{2,3}-\\d{3}-\\d{3}\\b"),
            // street addresses: "12 Sample Street", "4B/12 Sample St", "Unit 4B"
            Pattern.compile("(?i)\\b\\d+[A-Z]?(?:/\\d+[A-Z]?)?,?\\s+[A-Z][A-Za-z]+(?:\\s+[A-Z][A-Za-z]+)?\\s+"
                    + "(?:Street|St|Road|Rd|Avenue|Ave|Lane|Ln|Drive|Dr|Place|Pl|Crescent|Cres|Terrace|Tce|Way|Close)\\b"),
            Pattern.compile("(?i)\\b(?:Unit|Flat|Apt)\\s+\\d+[A-Z]?\\b"),
            // any other run of five or more digits (terminal ids, references, customer numbers)
            Pattern.compile("\\d{5,}"));

    private Redactor() {}

    public static String redact(String text) {
        String out = text;
        for (Pattern p : PATTERNS) {
            out = p.matcher(out).replaceAll(MASK);
        }
        return out.replaceAll("(\\Q" + MASK + "\\E\\s*)+", MASK + " ").strip();
    }
}

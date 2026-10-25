package nz.co.ksktech.cashbook.classify;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import nz.co.ksktech.cashbook.core.Classification;
import nz.co.ksktech.cashbook.core.ClassificationMethod;
import nz.co.ksktech.cashbook.core.GstTreatment;
import nz.co.ksktech.cashbook.core.ReviewItem;
import nz.co.ksktech.cashbook.core.Transaction;
import nz.co.ksktech.cashbook.core.reconcile.Merchants;
import nz.co.ksktech.cashbook.core.rules.Rules;

/**
 * Walks the review queue: y accepts the proposal, n excludes the line, c chooses a code, s skips, q quits.
 * Each answer becomes a HUMAN classification and both files are rewritten at once, so a later run resumes
 * where this one stopped. After a code is chosen or accepted, the answer can be saved as a learned rule.
 */
public final class ReviewSession {

    public static final String DECIDED_BY = "review";

    private final Path dir;
    private final Path rulesFile;
    private final Rules rules;
    private final BufferedReader in;
    private final PrintStream out;

    public ReviewSession(Path dir, Path rulesFile, Rules rules, BufferedReader in, PrintStream out) {
        this.dir = dir;
        this.rulesFile = rulesFile;
        this.rules = rules;
        this.in = in;
        this.out = out;
    }

    /** Runs until the queue is done, the user quits or input ends; returns how many items were answered. */
    public int run() {
        List<ReviewEntry> queue = new ArrayList<>(ClassifyStore.readReview(dir));
        Map<String, ClassifiedLine> lines = new LinkedHashMap<>();
        ClassifyStore.readLines(dir).forEach(l -> lines.put(l.id(), l));
        long open = queue.stream().filter(e -> !e.answered()).count();
        out.printf("%d item(s) to review, %d already answered.%n", open, queue.size() - open);
        int answered = 0;
        int position = 0;
        for (int i = 0; i < queue.size(); i++) {
            ReviewEntry entry = queue.get(i);
            if (entry.answered()) {
                continue;
            }
            position++;
            show(position, open, entry.item());
            Answer answer = ask(entry.item());
            if (answer == null) {
                break; // quit or end of input
            }
            if (answer.skip()) {
                continue;
            }
            Classification human = new Classification(answer.code(), answer.gst(), 1.0, ClassificationMethod.HUMAN,
                    DECIDED_BY, answer.description());
            queue.set(i, new ReviewEntry(entry.id(), new ReviewItem(entry.item().transaction(),
                    entry.item().proposed(), entry.item().question(), answer.description())));
            ClassifiedLine old = lines.get(entry.id());
            if (old != null) {
                lines.put(entry.id(), new ClassifiedLine(old.id(), old.transaction(), human, old.special(), old.aggregate(),
                        old.businessAmount(), List.of()));
            }
            ClassifyStore.writeLines(dir, new ArrayList<>(lines.values()), queue);
            answered++;
            if (answer.code() != null) {
                offerRule(entry.item().transaction(), answer.code());
            }
        }
        out.printf("Answered %d. %d still open.%n", answered, queue.stream().filter(e -> !e.answered()).count());
        return answered;
    }

    private record Answer(String code, GstTreatment gst, String description, boolean skip) {}

    private void show(int position, long total, ReviewItem item) {
        Transaction t = item.transaction();
        out.println();
        out.printf("[%d/%d] %s  %s  %s  %s%n", position, total, t.transactionDate(), t.accountType(), t.amount(),
                t.description());
        out.printf("        %s p%d: %s%n", t.source().file(), t.source().page(), t.source().rawText());
        Classification p = item.proposed();
        if (p != null && p.code() != null) {
            out.printf("  Proposed: %s %s (%s), confidence %.2f by %s - %s%n", p.code(), codeName(p.code()), p.gst(),
                    p.confidence(), p.decidedBy(), p.rationale());
        } else if (p != null && p.method() != ClassificationMethod.RULE) {
            out.printf("  Proposed: exclude (%s), confidence %.2f by %s%n", p.rationale(), p.confidence(), p.decidedBy());
        } else {
            out.println("  Proposed: nothing (no rule matched)");
        }
        out.println("  Question: " + item.question());
    }

    private Answer ask(ReviewItem item) {
        while (true) {
            out.print("  [y] accept  [n] exclude  [c] choose code  [s] skip  [q] quit > ");
            String input = read();
            if (input == null || input.equals("q")) {
                return null;
            }
            switch (input) {
                case "y" -> {
                    Classification p = item.proposed();
                    if (p == null || (p.code() == null && p.method() == ClassificationMethod.RULE)) {
                        out.println("  There is no proposal to accept: choose a code (c) or exclude (n).");
                        continue;
                    }
                    return new Answer(p.code(), p.gst(), "accepted: " + label(p.code(), p.gst()), false);
                }
                case "n" -> {
                    return new Answer(null, GstTreatment.EXCLUDED, "excluded in review", false);
                }
                case "s" -> {
                    return new Answer(null, null, null, true);
                }
                case "c" -> {
                    Answer chosen = choose();
                    if (chosen != null) {
                        return chosen;
                    }
                }
                default -> out.println("  Please answer y, n, c, s or q.");
            }
        }
    }

    private Answer choose() {
        List<String> codes = new ArrayList<>(rules.codes().keySet());
        for (int i = 0; i < codes.size(); i++) {
            out.printf("    %2d) %s %s (%s)%n", i + 1, codes.get(i), codeName(codes.get(i)), rules.codes().get(codes.get(i)).gst());
        }
        out.print("  Code number > ");
        String input = read();
        try {
            int n = Integer.parseInt(input == null ? "" : input);
            String code = codes.get(n - 1);
            GstTreatment gst = rules.codes().get(code).gst();
            return new Answer(code, gst, "chosen in review: " + label(code, gst), false);
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            out.println("  Not a number from the list.");
            return null;
        }
    }

    private void offerRule(Transaction t, String code) {
        String merchant = merchant(t.description());
        if (merchant.isEmpty()) {
            return;
        }
        out.printf("  Save a rule so lines starting \"%s\" are always %s %s? [y/N] > ", merchant, code, codeName(code));
        if ("y".equals(read())) {
            Map<String, Object> vendor = new LinkedHashMap<>();
            vendor.put("match", "(?i)^" + Pattern.quote(merchant));
            vendor.put("code", code);
            vendor.put("note", "learned in review on " + LocalDate.now());
            RulesLoader.appendLearned(rulesFile, vendor);
            out.println("  Saved to " + RulesLoader.learnedFile(rulesFile).getFileName());
        }
    }

    /** The merchant part of a description, used for learned rules. */
    static String merchant(String description) {
        return Merchants.name(description);
    }

    private String read() {
        try {
            String line = in.readLine();
            return line == null ? null : line.strip().toLowerCase(Locale.ROOT);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String codeName(String code) {
        Rules.Code c = rules.codes().get(code);
        return c == null ? "" : c.name();
    }

    private String label(String code, GstTreatment gst) {
        return code == null ? "exclude" : code + " " + codeName(code) + " (" + gst + ")";
    }
}

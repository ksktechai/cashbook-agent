package nz.co.ksktech.cashbook.eval;

/**
 * One column of the report: a run (or a given cashbook) scored against the golden one.
 *
 * @param mode "rules", "llm" or "actual"
 * @param notRun why the mode did not run (e.g. Ollama unreachable); null when it ran
 * @param classifier "rules only" or the model, e.g. "ollama:qwen3:30b"
 */
public record ModeResult(String mode, String classifier, String notRun, Score score, RunStats stats,
        LineMatcher.Matching matching) {

    public static ModeResult notRun(String mode, String classifier, String why) {
        return new ModeResult(mode, classifier, why, null, RunStats.unknown(), null);
    }

    public boolean ran() {
        return notRun == null;
    }
}

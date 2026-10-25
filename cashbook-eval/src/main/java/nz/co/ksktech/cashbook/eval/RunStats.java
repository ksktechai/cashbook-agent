package nz.co.ksktech.cashbook.eval;

import java.util.List;

/**
 * What a run cost and whether its inputs and output check out. Fields are null when unknown (e.g. comparing
 * a cashbook without its work folder).
 *
 * @param reviewItems questions a person would have to answer (classify review plus reconcile flags)
 * @param pendingLines lines left out because nothing (rule or model) settled them
 * @param statements statements read; {@code statementsReconciled} of them reconcile
 * @param exportFailures the export's failed checks; empty when it balances, null when not exported here
 * @param costNzd always 0: only local models are used
 */
public record RunStats(
        Integer reviewItems,
        Integer pendingLines,
        Integer llmCalls,
        Long llmInputTokens,
        Long llmOutputTokens,
        Long llmMillis,
        Double costNzd,
        Long runMillis,
        Integer statements,
        Integer statementsReconciled,
        List<String> reconcileProblems,
        List<String> exportFailures) {

    public static final double LOCAL_COST = 0.0;

    public static RunStats unknown() {
        return new RunStats(null, null, null, null, null, null, null, null, null, null, List.of(), null);
    }
}

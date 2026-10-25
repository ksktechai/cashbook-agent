# Evaluation (M6)

```sh
# run the pipeline twice (rules only, then rules + the local model) and score both against the golden cashbook
bin/cashbook-agent eval --golden target/synth/golden/cashbook.jsonl --period 2026-10:2027-03 \
    --in target/synth/in --template target/synth/template.xlsx --rules config/rules.example.yaml \
    --out eval --work target/eval-work [--modes rules,llm]

# or score a finished cashbook (run figures come from its run folder, <actual>.work, when there is one)
bin/cashbook-agent eval --golden <golden.xlsx> --period 2026-10:2027-03 --actual cashbook.xlsx --out private/eval
```

Writes `report.md` and `report.json` (schema: `cashbook-eval/src/main/resources/eval-report.schema.json`) to `--out`. The sample in [`eval/report.md`](../eval/report.md) is the synthetic period, run with `qwen3:30b` on a local Ollama.

**Privacy:** when the golden cashbook, `--in` or `--actual` is under `private/`, `--out` must be too, or the command refuses to run. Real reports list real amounts and descriptions.

## How a run works

Each mode runs ingest, classify, reconcile and export in its own work folder (`<work>/rules`, `<work>/llm`), never in a real run's folder, so your review answers are untouched. There is no person in the loop, so the run does what someone pressing "y" on every review question would do:
- a rule's or the model's proposal is taken
- a line nothing settled (no rule matched and the model wasn't used, or its answer was rejected) stays out and counts as pending
- flags (cut-off, high value) keep their line

The report therefore shows two things side by side: how right the agent is by itself, and how much a person would still have to decide (review items, pending lines).

If Ollama can't be reached, or the model isn't on the server, the `llm` column says "not run" with the reason, and the rules column is still reported.

## Matching

1. **Matched:** same month, same code, amount within $0.01.
2. **Wrong code:** of what is left, same month and amount with a different code.
3. **Missing:** golden lines left unpaired. **Extra:** produced lines left unpaired.

Among several candidates, the closest amount wins, then the closest date. Details don't have to agree.

Golden cashbooks are read as:
- **xlsx:** the filled template. The layout is found the same way the export finds it, and formulas are evaluated, so GST is what columns H (GST Income) and I (GST Expense) compute.
- **jsonl:** reconcile's `cashbook.jsonl`. GST is 3/23 of each STANDARD line.

## Metrics

| Metric | Meaning |
|---|---|
| Accuracy | matched lines ÷ golden lines |
| Known-vendor lines correct | of the produced lines a rule settled entirely, how many matched. A line summed per month (726, 729) counts only if no line was pending that month, since a pending line might have belonged in the sum. |
| Precision / recall per code | matched ÷ produced lines with that code / matched ÷ golden lines with that code |
| $ diff per code | produced total minus golden total |
| GST collected / claimed | totals and difference from the golden. "Within $1" is the phase-1 exit criterion. |
| Statements reconciled | opening + in − out = closing, and printed totals equal the extracted ones |
| Export checks | the export's own checks: row checks (F), Cash Pos difference, section totals |
| Review items, pending lines | questions a person would answer; lines left out for want of an answer |
| LLM calls, tokens, time, cost | from the classifier; cost is always $0.00 (local models only) |

## Synthetic result (seed 42)

| | rules | rules + qwen3:30b |
|---|---|---|
| Matched | 72/81 (88.9%) | 81/81 (100%) |
| Known-vendor lines | 53/53 (100%) | 72/72 (100%) |
| GST within $1 | no: unknown merchants (the $2,899 laptop among them) are left pending | yes |
| Review items | 16 | 18 (every model suggestion is reviewed by default) |

The tests run the same comparison with WireMock in place of Ollama. They script two wrong answers and check that both show up as wrong codes.

# Export and run (M5)

```sh
# one step at a time
bin/cashbook-agent export --reconciled target/reconcile --template <template.xlsx> --period 2026-10:2027-03 --out cashbook.xlsx

# or the whole pipeline; stops when review is needed, keeps your answers, finishes on the next run
bin/cashbook-agent run --period 2026-10:2027-03 --in <statements folder> --template <template.xlsx> --out cashbook.xlsx \
    --rules private/rules.yaml [--llm ollama]
bin/cashbook-agent review --dir cashbook.xlsx.work/classify --rules private/rules.yaml
```

The template file is never modified. A copy is filled and written to `--out`. It is written even when a check fails, so you can look at it; the exit code is then 1.

## How it fills the template (layout unchanged)

The structure is read from the sheet, not assumed:
- **Columns:** the header row of analysis codes is the first row with five or more 3-digit codes.
- **Totals row:** the first `SUM` in the Bank column.
- **Data rows:** the rows with the running balance `D{r-1}+G{r}`.
- **Month blocks:** runs of data rows with a code or detail, separated by blank rows. There must be one per month of the period.
- **Entertainment rows:** a row coded 726 whose Bank cell links to `OtherCalculation!C…`.

Per line:

| Column | Written as |
|---|---|
| A | transaction date |
| B | code |
| C | detail |
| G | signed amount |
| I | GST Expense formula; blank for EXEMPT, typed 0 for NO_GST |
| D | running balance, rewritten on every data row |

- **Spare rows:** A–C cleared, Bank set to 0.
- **Stray links:** any other formula in a Bank cell (like the March rent row's link to `OtherCalculation!C15`) is replaced, with a note.
- **Row insertion:** when a month has more lines than rows, rows are inserted inside that block. Formulas and styles are copied from the block's last row, and every reference below moves, including other sheets' references to the totals row (Cash Pos `Cashbook!J105` becomes `J109` after four inserted rows).
- **OtherCalculation:**
  - the entertainment month totals go into the cells the Cashbook links to
  - Insurance, Internet Subscriptions, Car Park Fees and Business Expenses are itemised per month with subtotals and a `Total` row
  - then the utility split table
- **Period labels:** "Cashbook for period ending …", "Cash Position as at …" and the `DATE(…)` month cells are updated.

## Checks (after POI evaluates every formula)

- every row check (column F) is 0
- the Cash Pos difference is 0
- each OtherCalculation section total equals its codes' Cashbook total
- each utility split row checks to 0

## Template notes

The export reports, without changing anything:
- a column whose header disagrees with the code it collects
- a column that collects different codes in different rows
- a code collected by more than one column (a line with that code would fail its row check)
- a Bank formula it replaced

Run on the real Oct–Mar template (with synthetic data), every check passed, and the notes reported:
- column AG collects 705 in rows 7–63 and 735 in rows 64–104, while AG4 says 735
- code 735 is collected by AG, AH and AV (no line uses 735)
- G95 held a stray link to `OtherCalculation!C15`, which was replaced

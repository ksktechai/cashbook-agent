# Reconcile (M4)

```sh
bin/cashbook-agent reconcile --classified target/classify --ingest target/ingest --period 2026-10:2027-03 --out target/reconcile
```

Reads the reviewed `classifications.jsonl` (and the review answers) plus the utility invoices from the ingest output, and writes:

| File | Contents |
|---|---|
| `cashbook.jsonl` | the `CashbookLine`s: month, code, detail, signed amount, GST treatment, source references |
| `sections.jsonl` | itemised detail for the OtherCalculation sheet (entertainment, insurance, no-GST subscriptions, car parks, business expenses) |
| `split.jsonl` | the utility split table |
| `flags.jsonl` | open questions, also added to the classify folder's `review.jsonl` |

Exit code 1 means the cashbook is incomplete: lines are still unreviewed or flags unanswered. Answer them with `review --dir <classify folder>` and run reconcile again. Answers are honoured on every later run.

## What it does, in order (all pure functions in `cashbook-core`)

1. **Pending and excluded lines** are left out. Lines still under review are listed as unreviewed.
2. **Double-count guard:** each card "payment received" is paired with a bank payment of the same amount within 3 days. If that bank payment was *coded* rather than excluded, it is flagged, because the card's purchases are already in the cashbook.
3. **Refund netting:** a purchase and a later refund from the same merchant on the same account, equal and opposite, are both left out.
4. **Utility split** (`special: utilitySplit`): the payment is matched to the invoice dated in the previous month.
   - Internet (727) is the broadband subtotal.
   - Power (725) is the power subtotal plus the card surcharge (payment − power − broadband).
   - When a credit was applied, the payment is split pro-rata to the invoice charges.
   - The parts always sum to the payment. A payment that differs from the invoice's amount due is flagged.
5. **FX:** a currency exchange (`fxConversion`, the NZD actually debited including the fee) costs its foreign purchase (`fxPurchase`). An offshore margin (`offshoreMargin`) joins its foreign charge on the same date and becomes one line. Unpaired ones are flagged.
6. **Mixed charges** (`mixedCharge`): only `businessAmount` is kept; the detail records the personal remainder.
7. **Month** is the card/transaction date's month. A card date before the period that was processed inside it is put in the first month and **flagged** (`PERIOD_CUT_OFF`).
8. **High value:** purchases under `capitalise-codes` (default 734, 709) above **$1,000 excl. GST** are flagged "may need capitalising". Services, levies and insurance are not.
9. **Monthly codes** (default 726 entertainment, 729 car parks, plus vendor rules with `aggregate: MONTHLY`) are summed to one line per month, matching the template's one row per month. The dates are itemised in `sections.jsonl`.

## Configuration (`cashbook.reconcile.*`)

| Key | Default |
|---|---|
| `capitalise-threshold-excl-gst` | `1000.00` |
| `capitalise-codes` | `734,709` |
| `monthly-codes` | `726,729` |
| `sections` | `726` Entertainment, `730`/`732` Insurance, `739` Internet Subscriptions, `729` Car Park Fees, `734` Business Expenses |
| `repayment-window-days` | `3` |

## Checked against the golden

On the synthetic period (rules only, with the review queue answered from the truth file), the reconciled cashbook equals `golden/cashbook.jsonl` to the cent: 81 lines, the six utility splits, and every section total equal to its codes' total. With your local model's suggestions accepted instead, 80 of 81 lines matched (the laptop went to 709 rather than 734).

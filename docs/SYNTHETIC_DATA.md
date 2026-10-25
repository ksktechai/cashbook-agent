# Synthetic data

`cashbook-agent synth --seed 42 --out target/synth` generates a fake Oct 2026 – Mar 2027 GST period. Tests and CI use only this data. The same seed always gives byte-identical files.

## Where the layouts come from

Each layout copies the structure of a real statement. That structure was found by a **redacted probe** run locally over `private/`: every digit was shown as 9, and any word outside a short allowlist was shown only by its case shape. No real names, merchants, accounts or amounts were used. The institutions are fictional, account numbers use bank code 99 (not issued in NZ), and every page carries a "SYNTHETIC TEST DATA" footer.

| Synthetic file | Modelled on | Layout features |
|---|---|---|
| `BusinessEveryday-*.pdf`, `ChequeAccount-*.pdf` (Kauri Bank) | Westpac transaction search export | newest first; "Balance at end of day" rows; opening balance at the end; totals block; Type wraps to two lines; negative balances |
| `KauriRewardsMastercard-*.pdf` | Westpac card export | card and reference columns; no balance column (totals only) |
| `Harbourside-NetBank-Credit-visa.pdf` | ASB Visa (browser print) | oldest first; `CR` credits; card date and processed date; foreign charges over three lines plus a separate OFFSHORE SERVICE MARGINS line |
| `Harbourside-Online-*.pdf` | ASB account | `– $` / `+ $` amounts; two-line descriptions straddling the amount line |
| `spend_account_statement_*.pdf` (Sharepot) | Sharesies Spend | timestamps; currency exchange and fee columns; column positions change after page 1 |
| `fibrewave/<uuid>.pdf` ×7 | Orcon invoice | Power and Broadband sections with subtotals and discount credits; card payment fee billed on the next invoice; credit carried forward |
| `body-corp/*-Levy-Notice-*.pdf` ×2 | Body-corp levy tax invoice | fund lines with GST; early-payment discount (gross minus discount = net paid); the January notice is **image-only** (scanned at 300 dpi) to exercise OCR |

**Highlights** are real PDF Highlight annotations in the six colours seen in the real files. Any colour means "business". They cover part of a line (about 6–25% of its box), and some neighbouring rows pick up a sliver. The cheque export uses flattened highlights instead: rectangles drawn into the page content.

## Traps

Each line in `truth/transactions.jsonl` lists the traps it exercises.

| Trap | Where | Expected handling |
|---|---|---|
| `CARD_REPAYMENT` | business and cheque → Mastercard, Harbourside account → Visa | excluded; the card lines are coded instead |
| `TRANSFER`, `SAVINGS_SWEEP`, `SPEND_TOP_UP`, `ATM_CASH` | business, cheque, Harbourside account, Sharepot | excluded |
| `TAX_PAYMENT`, `GST_REFUND` | business (IRD) | excluded; a GST refund is money in but not income |
| `REFUND_NETS_TO_ZERO` | Mastercard: −$89.50 then +$89.50 | excluded |
| `PERIOD_CUT_OFF` | Visa: card 30/09/2026, processed 01/10/2026 | flag for review; golden includes it in October (734) |
| `MONTH_BOUNDARY` | Visa: card 31/12, processed 04/01 | month follows the card date (December) |
| `UTILITY_SPLIT`, `UTILITY_CREDIT` | Mastercard Fibrewave payments | split into Power 725 (power + card fee) and Internet 727; pro-rata when a credit applied; always sums to the card charge |
| `FOREIGN_CURRENCY`, `OFFSHORE_MARGIN` | Visa USD/AUD charges | 739 no GST, including the paired margin line |
| `FX_CONVERSION_FEE` | Sharepot NZD→USD conversion | 739 at the NZD actually debited, fee included |
| `MIXED_CHARGE` | Mastercard `APPLE.COM/BILL` $54.98 | $40.00 to 734; the rest is personal |
| `HIGH_VALUE` | Mastercard $2,899 laptop | 734, flagged "may need capitalising" |
| `POLICY_QUESTION` | Mastercard café lunch in January | 726, but ask the reviewer |
| `MONTHLY_AGGREGATE` | business car parks | summed per month (729), dates itemised |
| `CREDIT_NOT_REPAYMENT` | Mastercard insurance refund in Money in | excluded, and not a repayment |
| `LEVY_DISCOUNT` | cheque body-corp payments | 733 at the discounted net amount |
| `HIGHLIGHT_BLEED`, `FLATTENED_HIGHLIGHT`, `WRAPPED_TEXT` | Visa, cheque, Harbourside account | extraction and highlight-detection traps |
| `REDACTION_TARGET` | IRD-style numbers, account numbers | must be redacted before any LLM call |

## Outputs

| Path | Contents |
|---|---|
| `in/` | the PDFs: the `--in` folder for a run |
| `template.xlsx` | sanitised template (also committed at `synthetic/src/test/resources/template.xlsx`) |
| `truth/transactions.jsonl` | one `Transaction` per statement line (file, page, box, raw text, dates, amounts, FX, highlight), plus the expected classification and traps |
| `truth/statements.jsonl` | opening and closing balances, money in and out, page count per statement |
| `truth/documents.jsonl` | every figure on the invoices and levy notices |
| `golden/cashbook.jsonl` | expected `CashbookLine`s with source references |
| `golden/cashbook.xlsx` | the template filled with the golden lines, including the OtherCalculation sections and the utility split table |
| `manifest.json` | seed and SHA-256 of every file |

## The sanitised template

`TemplateBuilder` rebuilds the real template's structure with fictional names, and keeps its quirks on purpose so the M5 exporter has to handle them:

- the code header row (row 4) mixes text and number cells, stops at column AG, and AG's header (735) disagrees with its formulas (705)
- AK, AL and AM have formulas only in row 6 (the opening row, which never holds a transaction) and the totals row; AV tests 730 in row 6 and 735 below, as do AH and (in the real file) part of AG. None of these double-counts a used code, so every row check is 0
- GST Expense is missing on exempt rows (rent, bank fees) and typed as 0 on no-GST rows
- the March rent row links to an empty `OtherCalculation!C15`
- Cash Position labels row 35 from `H5` ("GST Income") while its amount is GST Expense
- fixed month blocks: Oct 7–22, Nov 24–37, Dec 39–54, Jan 56–70, Feb 72–86, Mar 88–100; totals in row 105

After an intentional change to `TemplateBuilder`, run `./gradlew :synthetic:updateTemplate`. A test fails if the committed template drifts.

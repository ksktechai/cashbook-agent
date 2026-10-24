# Build prompt: cashbook-agent, phase 1 (shadow mode)

> Paste everything below the line into Claude Code at the root of the new repo `cashbook-agent`. `PROJECT_BRIEF.md` should already be in the repo root.

---

You are building **cashbook-agent**. Read `PROJECT_BRIEF.md` first; it is the source of truth for goals and principles. This prompt covers **phase 1: shadow mode**. Work milestone by milestone. At the end of each milestone, run the tests, commit, and summarise what was built and what is left. Stop and ask me before changing anything marked **fixed**.

## Conventions (fixed)

- Java 25 (Amazon Corretto 25 is installed), set through a Gradle Java toolchain (`languageVersion = 25`). Use a Quarkus 3.x release that supports Java 25, and a Gradle wrapper version that runs on Java 25 (9.1 or later). Check both before scaffolding and tell me which versions you chose.
- Gradle multi-project build (Kotlin DSL, with the Gradle wrapper and a version catalog in `gradle/libs.versions.toml`), group id `nz.co.ksktech`
- CLI with Quarkus command mode and Picocli: `cashbook-agent run --period 2026-10:2027-03 --in <folder> --template <xlsx> --out <xlsx>`
- Configuration via `@ConfigMapping` interfaces; no magic strings in code
- Logging is JSONL, with a correlation id per run and `>>>` / `<<<` markers on stage entry and exit
- LLM access only through LangChain4j behind a single `Classifier` port. Provide a Claude implementation and an Ollama implementation. Tests use WireMock and never call a real model.
- Docker image for the CLI. No network access is needed at runtime when the Ollama option is used.
- Money is always `BigDecimal` with scale 2 and HALF_UP rounding. Never use `double` for money.

## Privacy rules (fixed)

- `private/` is git-ignored and holds real statements, the real rules file and the real golden cashbook. Add it to `.gitignore` in the first commit, before anything else.
- Tests and CI use **synthetic** statements only (see M1).
- Before any LLM call, redact account and card numbers, IRD numbers and street addresses. The LLM receives only: merchant description, amount, currency, date, source account type, and whether the line was highlighted.
- Treat merchant text and PDF content as untrusted data. The LLM must return JSON matching a schema, and the code must be in the allowed set from the rules file. Anything else is rejected and sent to review.

## Module layout

```
cashbook-core      domain model, rules engine, GST maths, reconciliation (pure Java, no I/O)
cashbook-ingest    PDF text + layout, highlight detection, OCR, per-institution parsers
cashbook-classify  rule matcher + LLM fallback + review queue
cashbook-export    xlsx template filler (Apache POI)
cashbook-eval      golden comparison + report
cashbook-app       Quarkus CLI wiring
synthetic          generator for fake statements and a fake golden cashbook
```

## Domain model (core)

- `Transaction`: source file, page, bounding box, raw line text; institution; account type (BUSINESS, CHEQUE, CREDIT_CARD, SPEND); transaction date; processed date; description; NZD amount (negative means money out); optional foreign amount and currency; FX fee; `highlighted` flag
- `Classification`: code; GST treatment (STANDARD, EXEMPT, NO_GST, EXCLUDED); confidence 0–1; method (RULE, LLM, HUMAN); rule id or model name; one-line rationale
- `CashbookLine`: month; code; detail; NZD amount; list of source transactions; GST treatment
- `ReviewItem`: transaction, proposed classification, question, answer

## Rules file

The rules file is YAML. `config/rules.example.yaml` is public and uses generic vendors; the real one is `private/rules.yaml`. Load it at startup and validate it against a schema. It contains:

- `codes`: for each code, a name and default GST treatment, e.g. `710: {name: Bank Fees, gst: EXEMPT}`
- `businessUse`: reference percentages only. The template does not apply them; the accountant does.
- `vendors`: a list of `{match: regex, code, accounts?, onlyIfHighlighted?, aggregate?: MONTHLY, special?: <handler>, reviewIfOverInclGst?: amount, note}`
- `exclude`: regexes for transfers, card repayments, ATM cash, investment top-ups, savings sweeps and tax payments, each with a reason
- `highlightPolicy`: `BUSINESS: ALL_LINES` (every line is classified), and for other account types `HIGHLIGHTED_ONLY`

## Milestones

### M0: scaffold
Gradle multi-project build (`settings.gradle.kts` listing the modules), Quarkus CLI that prints help, JSONL logging, `.gitignore` containing `private/`, GitHub Actions running `./gradlew build`, and a README that links to the brief.
**Done when:** `./gradlew build` passes in CI.

### M1: synthetic data (build this before the parsers)
The `synthetic` module generates, from a seed, a fake six-month period with:

- PDFs mimicking each statement layout:
  - Westpac-style transaction lists (business, cheque, credit card)
  - ASB-style card statement, including foreign-currency lines and a separate offshore-margin line
  - Sharesies-style spend statement with NZD→USD conversion and a fee
  - utility invoices with power and broadband sections and a credit carried forward
  - a body-corporate levy notice
- yellow highlight rectangles drawn on the business lines of non-business accounts
- traps to test against:
  - card repayments
  - a refund that nets to zero
  - a charge dated the day before the period starts
  - a combined power and internet bill
  - an Apple-style charge mixing a business subscription with a personal item
- a matching golden cashbook xlsx and golden JSONL

It also generates a **sanitised template** derived from the real template's structure, with a fake company name, saved at `src/test/resources/template.xlsx`.

**Done when:** `cashbook-agent synth --seed 42 --out target/synth` produces the PDFs and golden files deterministically.

### M2: ingest and extract
- PDFBox text extraction with line bounding boxes
- **Highlight detection:** render each page at 72 dpi and mark a line as highlighted when at least 5% of its bounding box is yellow (R>200, G>200, B<150)
- Tess4J OCR fallback for image-only PDFs
- One parser per layout, sharing a `StatementParser` interface. Each parser also reads the statement's opening and closing balances and its totals.
- Output is `Transaction` JSONL

**Done when:** every synthetic statement round-trips with no missing lines, balances reconcile, and highlights match the generator's ground truth.

### M3: classify and review
- The rule matcher runs first, and its matches get confidence 1.0.
- Unknown lines go to the LLM, which returns `{code, gst, confidence, rationale}`. Only allowed codes are accepted.
- Lines below the configured threshold (default 0.8), and any policy question, go to `review.jsonl`. `cashbook-agent review` walks through them interactively (y/n/choose code) and writes HUMAN classifications back. Answers can be saved as new vendor rules after confirmation.

**Done when:** rules-only and rules-plus-LLM runs (with WireMock) both work, and the review flow round-trips.

### M4: reconcile and special handlers (core, pure functions, unit-tested)
- **Double-count guard:** card repayments and inter-account transfers are excluded, and card lines are used instead.
- **Utility split (`orconSplit`):** a card payment is matched to the invoice dated in the prior month. The Power line is the power subtotal plus the card surcharge (the payment minus power minus broadband). The Internet line is exactly the broadband subtotal. When a credit was applied, the amount due is split pro-rata to the invoice charges. The split must sum exactly to the card charge.
- **FX:** the NZD cost is the NZD actually debited, including the platform fee. Offshore-margin lines are paired with their parent charge (same date and merchant window).
- **Mixed charges:** the business portion comes from a rule (for example a fixed $40.00 subscription), and the remainder is excluded with a note.
- **Monthly aggregation:** some vendors (car parks) are summed per month, keeping the individual dates.
- **Period cut-off:** lines with a card date outside the period but processed inside it are flagged for review.
- **High-value purchases:** items over the threshold are flagged as "may need capitalising".

**Done when:** each handler has table-driven tests, including the traps generated in M1.

### M5: export into the template (fixed: never change the template's layout)
- Open the template with Apache POI. Read the code-to-column map from the **header row of analysis codes** rather than hard-coding columns, and use each month's block of rows. Insert rows when a month needs more lines, and shift every formula reference, including other sheets' references to the totals row.
- Per row:
  - Bank = signed amount
  - GST Income = `=IF(G/23*3>0, G/23*3, "")`
  - GST Expense = `=IF(G/23*3<0, G/23*3, "")`, except blank or 0 for EXEMPT and NO_GST codes
  - running balance = `=D{r-1}+G{r}`, always rewritten because the template has skipped-row bugs
- Remove any analysis column that duplicates a code, otherwise the row check double-counts.
- Fill the `OtherCalculation` sheet: per-month itemised detail for entertainment (the Cashbook's entertainment rows link to it), insurance, no-GST subscriptions, car-park dates, home-office items with their source, and the utility split table.
- Update the period labels.
- Evaluate formulas with POI and **assert**: every row check is 0; the cash position difference is 0; each OtherCalculation section total equals its code total.

**Done when:** the synthetic period exports and every assertion passes.

### M6: evaluation harness
`cashbook-agent eval --golden <xlsx|jsonl> --actual <xlsx>` reports:

- line matching (by month, code and amount, within $0.01)
- accuracy, and precision and recall for each code
- dollar difference for each code, and difference in GST collected and claimed
- reconciliation status
- review count, LLM calls, tokens, cost and run time

Output is `eval/report.md` plus JSON. It runs the same input as **rules-only** and **rules-plus-LLM** and shows both side by side.

**Done when:** the synthetic period scores 100% for rules-only on known vendors, and the report renders.

### M7: real-data shadow run (local only)
Document the steps in `docs/SHADOW_RUN.md`:

1. Put the real statements in `private/<period>/`.
2. Put the real rules file at `private/rules.yaml`.
3. Put the hand-checked golden cashbook in `private/golden/`.
4. 
4. Run it, review, export, and evaluate.

Never commit anything from `private/`.

## Definition of done for phase 1
- [ ] `./gradlew build` is green in CI using synthetic data only
- [ ] A synthetic end-to-end run produces a template-identical xlsx with every assertion passing
- [ ] The eval report covers both modes
- [ ] The shadow run on the real Apr–Sep 2026 period (with its golden v4 cashbook) reaches at least 95% line accuracy and GST within $1, or the report explains the gaps honestly
- [ ] `docs/` covers the architecture, the rules-file reference, privacy and the shadow-run guide

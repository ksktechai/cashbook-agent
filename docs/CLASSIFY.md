# Classify and review (M3)

```sh
bin/cashbook-agent classify --in target/ingest --out target/classify --rules private/rules.yaml            # rules only
bin/cashbook-agent classify --in target/ingest --out target/classify --rules private/rules.yaml --llm ollama
bin/cashbook-agent review --dir target/classify --rules private/rules.yaml
```

`classify` writes `classifications.jsonl` (every transaction with its `Classification`, plus the vendor rule's `special` handler, `aggregate` and `businessAmount` for M4), `review.jsonl` (the queue) and `llm-usage.json`.

## The LLM is optional

The cashbook is complete **without any LLM**: rules decide, and anything no rule covers goes to review, where you choose the code (and can save it as a rule). `--llm none` is the default.

With `--llm ollama`, a **local open-source model** suggests a code for lines no rule matches. No paid LLM API is used, by design. The model gets only the redacted description, amount, currency, date, account type and highlighted flag. Card, account, IRD/GST numbers, addresses and any 5+ digit run are masked first (`Redactor`). Its reply must be JSON with `code`, `gst`, `confidence` and `rationale`, and the code must be one of the rules file's codes. Anything else is rejected and sent to review.

### Finding the Ollama server

`cashbook.llm.ollama.urls` lists servers to try in order; the first that answers `/api/version` and has the model is used. When the server's IP changes, put its Bonjour name first (`http://<machine>.local:11434`) and the last-known IP second. Put this in a git-ignored `.env` (see `.env.example`):

```
CASHBOOK_LLM_OLLAMA_URLS=http://my-mac.local:11434,http://192.168.1.10:11434
```

`--ollama-url` (repeatable) and `--model` override per run. If nothing is reachable or the model is missing, classify stops with a clear message; it never silently drops to rules only.

### What the live check showed (synthetic period, `qwen3:30b`)

| Run | Unknown merchants right | Notes |
|---|---|---|
| Plain prompt | 1 of 6 | excluded highlighted lunches and the laptop as "personal"; picked 715 for stationery |
| With code `hint`s and the highlight rule | 5 of 6 | the laptop shop went to 739 (overseas) at confidence 0.95 |

Confidence is **not calibrated**: right and wrong answers both came back at 0.9–0.95. Set `cashbook.llm.review-all=true` to send every LLM suggestion to review as a one-keystroke proposal instead of accepting those at or above `cashbook.review.threshold` (0.8).

## Rules file

Validated against `cashbook-classify/src/main/resources/rules.schema.json` at load. Errors name the path, e.g. `vendors[3].code: 999 is not defined under codes`. Evaluation order: `highlightPolicy`, then `exclude`, then `vendors` (first match wins).

| Section | Fields |
|---|---|
| `codes` | `name`, `gst` (STANDARD, EXEMPT, NO_GST, EXCLUDED), optional `hint` (what belongs there; shown to the LLM) |
| `businessUse` | reference share per code (0–1); not applied by the cashbook |
| `vendors` | `match` (Java regex on the description), `code`, optional `gst`, `accounts`, `onlyIfHighlighted`, `aggregate: MONTHLY`, `special` (`utilitySplit`, `mixedCharge`, `offshoreMargin`, `fxConversion`, `fxPurchase`, `monthlyAggregate`), `businessAmount`, `reviewIfOverInclGst`, `ask` (a policy question that always goes to review), `note` |
| `exclude` | `match`, `reason`, optional `accounts` |
| `highlightPolicy` | per account type: `ALL_LINES` or `HIGHLIGHTED_ONLY` |

`config/rules.example.yaml` covers the synthetic vendors and deliberately leaves a few out, so both modes have work to do.

## Review

`review` shows each open item (transaction, proposal, question) and takes:

| Key | Action |
|---|---|
| `y` | accept the proposal |
| `n` | exclude the line |
| `c` | choose a code from the list |
| `s` | skip |
| `q` | quit |

Each answer is written at once as a `HUMAN` classification, so the next run resumes. After a code is chosen or accepted, you can save it as a rule. Learned rules go to `<rules>.learned.yaml` next to the rules file, which is validated and loaded with it, so your hand-written rules file is never rewritten.

# Ingest (M2)

`cashbook-agent ingest --in <folder> --out <dir>` reads every PDF under the folder and writes:

| File | Contents |
|---|---|
| `transactions.jsonl` | one `Transaction` per statement line: source file, page, box and raw text, dates, signed NZD amount, foreign amount, FX fee, highlighted |
| `statements.jsonl` | each statement's printed figures (opening/closing balance, totals) and the reconciliation of its lines against them |
| `documents.jsonl` | utility invoices (power and broadband subtotals, card fee, credit, amount due) and levy notices (net, GST, gross, discount, net due), each marked `ocr` when read by OCR |

It exits 1 when a file could not be read or a statement does not reconcile (the outputs are still written).

## How it reads a PDF

1. **Words and lines.** PDFBox gives each glyph's position. Words are split at spaces and at gaps wider than a fraction of a space (two cells drawn touching). Words are grouped into lines when their boxes overlap vertically by at least half.
2. **OCR.** A page with no text layer is rendered at 300 dpi and read by Tesseract (Tess4J). The words come back with boxes in points, so the parsers don't care which route the text took.
3. **Layout detection** uses the page structure (titles and column headers), never the bank's name, so the same parser reads the real export and the synthetic one.
4. **Columns** come from each page's own header row. Each cell (a run of words) goes to the column it overlaps most; a cell that mostly straddles two columns is split word by word.
5. **Parsers**, one per layout: `TransactionSearchParser` (account and card), `VisaPrintParser`, `AccountPrintParser`, `SpendStatementParser`. Supplier documents use `DocumentParsers`. Each parser joins multi-line records (wrapped Type cells, three-line foreign charges, straddling descriptions), skips non-transaction rows (end-of-day balances, page furniture), and reads the opening/closing balances and totals.
6. **Reconciliation** (`cashbook-core`, pure): opening + in − out = closing (reversed for cards, where the balance is the amount owing), and the lines' totals equal any printed totals.

## Highlight detection

Any highlighter colour means "business" (confirmed for the real statements, which use five colours). A line is highlighted when **any** of these holds:

- **Annotations:** Highlight annotation quads cover at least 5% of the line's box.
- **Pixels** (highlights flattened into the page): the page is rendered at 72 dpi; at least 5% of the box's non-text pixels are highlighter-coloured (saturated). Dark text pixels are left out of the count.
- **Cells:** any single cell on the line (amount, description) is at least 50% covered by either measure. A highlighter stroke covers a whole cell, while bleed from a neighbouring row only clips a sliver of one. This was added because real and synthetic highlights often cover only the amount, which is 6–10% of the line, close to the 5% threshold.

The 5% threshold is configurable as `cashbook.ingest.highlight-threshold`.

## Configuration (`cashbook.ingest.*`)

| Key | Default | Meaning |
|---|---|---|
| `highlight-threshold` | `0.05` | share of a line that must be highlighted |
| `business-account-pattern` | `(?i)business` | account names matching this are BUSINESS, other accounts CHEQUE |
| `ocr.enabled` | `true` | read image-only pages with Tesseract |
| `ocr.tessdata` | (search) | language data folder; otherwise `$TESSDATA_PREFIX`, then Homebrew, Debian and Alpine locations |
| `ocr.dpi` | `300` | render resolution for OCR |
| `ocr.library-path` | `/opt/homebrew/lib:/usr/local/lib` | where JNA finds the native Tesseract library |

# Cashbook evaluation

Golden: `target/synth/golden/cashbook.jsonl` · Period: 2026-10:2027-03 · Generated: 2026-10-26

Each run accepts every suggestion a rule or the model makes, as a person pressing "y" in review would; a line nothing settled stays out. Lines match on month, code and amount (within $0.01).

## Summary

| | rules | llm |
|---|---:|---:|
| Classifier | rules only | ollama:qwen3:30b |
| Golden lines | 81 | 81 |
| Lines produced | 73 | 81 |
| **Matched** | **72 (88.9%)** | **81 (100.0%)** |
| Wrong code | 0 | 0 |
| Missing | 9 | 0 |
| Extra | 1 | 0 |
| Known-vendor lines correct | 53/53 (100.0%) | 72/72 (100.0%) |
| GST collected (golden $8,100.00) | $8,100.00 (+0.00) | $8,100.00 (+0.00) |
| GST claimed (golden $1,981.47) | $1,531.80 (−449.67) | $1,981.47 (+0.00) |
| GST within $1 | **no** | yes |
| Statements reconciled | 6/6 | 6/6 |
| Export checks | all passed | all passed |
| Review items | 16 | 18 |
| Lines left pending | 15 | 0 |
| LLM calls | 0 | 8 |
| LLM tokens in / out | 0 / 0 | 4,970 / 307 |
| LLM time | 0.0 s | 5.9 s |
| Cost | $0.00 (local) | $0.00 (local) |
| Run time | 2.3 s | 7.0 s |

## By code

P = precision (produced lines with the code that are right), R = recall (golden lines found), $ diff = produced total minus golden total.

| Code | Golden lines | Golden $ | rules P | rules R | rules $ diff | llm P | llm R | llm $ diff |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 707 | 7 | $62,100.00 | 100.0% | 100.0% | +0.00 | 100.0% | 100.0% | +0.00 |
| 710 | 5 | −$25.00 | 100.0% | 100.0% | +0.00 | 100.0% | 100.0% | +0.00 |
| 711 | 2 | −$1,725.00 | 100.0% | 100.0% | +0.00 | 100.0% | 100.0% | +0.00 |
| 724 | 6 | −$14,400.00 | 100.0% | 100.0% | +0.00 | 100.0% | 100.0% | +0.00 |
| 725 | 6 | −$778.93 | 100.0% | 100.0% | +0.00 | 100.0% | 100.0% | +0.00 |
| 726 | 6 | −$535.50 | 0.0% | 0.0% | +489.00 | 100.0% | 100.0% | +0.00 |
| 727 | 6 | −$530.19 | 100.0% | 100.0% | +0.00 | 100.0% | 100.0% | +0.00 |
| 728 | 6 | −$390.00 | 100.0% | 100.0% | +0.00 | 100.0% | 100.0% | +0.00 |
| 729 | 6 | −$164.60 | 100.0% | 100.0% | +0.00 | 100.0% | 100.0% | +0.00 |
| 730 | 6 | −$289.20 | 100.0% | 100.0% | +0.00 | 100.0% | 100.0% | +0.00 |
| 732 | 1 | −$1,240.00 | 100.0% | 100.0% | +0.00 | 100.0% | 100.0% | +0.00 |
| 733 | 2 | −$6,339.37 | 100.0% | 100.0% | +0.00 | 100.0% | 100.0% | +0.00 |
| 734 | 9 | −$3,198.50 | 100.0% | 66.7% | +2,958.50 | 100.0% | 100.0% | +0.00 |
| 739 | 13 | −$348.15 | 100.0% | 100.0% | +0.00 | 100.0% | 100.0% | +0.00 |

## rules: differences

### Wrong code (0)

### Missing (9)

| Month | Code | Amount | Detail |
|---|---|---:|---|
| 2026-10 | 726 | −$65.00 | Entertainment (Business Lunch/Coffee/Meals/Drinks) |
| 2026-10 | 734 | −$35.00 | Home Office Expenses - Domain renewal (card 30/09, processed 01/10) |
| 2026-11 | 726 | −$87.00 | Entertainment (Business Lunch/Coffee/Meals/Drinks) |
| 2026-12 | 726 | −$117.00 | Entertainment (Business Lunch/Coffee/Meals/Drinks) |
| 2026-12 | 734 | −$2,899.00 | Home Office Expenses - Laptop (may need capitalising) |
| 2026-12 | 734 | −$24.50 | Home Office Expenses - Printer paper and toner (card 31/12, processed 04/01) |
| 2027-01 | 726 | −$87.50 | Entertainment (Business Lunch/Coffee/Meals/Drinks) |
| 2027-02 | 726 | −$102.00 | Entertainment (Business Lunch/Coffee/Meals/Drinks) |
| 2027-03 | 726 | −$77.00 | Entertainment (Business Lunch/Coffee/Meals/Drinks) |

### Extra (1)

| Month | Code | Amount | Detail | Source |
|---|---|---:|---|---|
| 2027-01 | 726 | −$46.50 | Entertainment (1) | KauriRewardsMastercard-05Apr27.pdf p.1 |

## llm: differences

### Wrong code (0)

### Missing (0)

### Extra (0)


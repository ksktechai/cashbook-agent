# cashbook-agent

**An agentic pipeline that turns bank, card and wallet statements plus supplier invoices into a completed, accountant-ready GST cashbook.**

The tax maths is deterministic. An LLM is used only where judgment is needed, a human approves anything the agent is unsure about, and an evaluation harness measures accuracy against a hand-checked answer key.

- Goals, principles and phases: [PROJECT_BRIEF.md](PROJECT_BRIEF.md)
- Phase 1 build plan: [docs/BUILD_PROMPT.md](docs/BUILD_PROMPT.md)
- Synthetic test data and its traps: [docs/SYNTHETIC_DATA.md](docs/SYNTHETIC_DATA.md)
- Ingest: [docs/INGEST.md](docs/INGEST.md) · Classify and review: [docs/CLASSIFY.md](docs/CLASSIFY.md) · Reconcile: [docs/RECONCILE.md](docs/RECONCILE.md)

The cashbook needs no LLM. Optionally, a local open-source model through Ollama can suggest codes for merchants no rule covers; no paid LLM API is used.

## Build and run

Requires Java 25 (Amazon Corretto 25). The Gradle wrapper does the rest.

```sh
./gradlew build                         # compile and test all modules
./gradlew :cashbook-app:quarkusBuild    # package the CLI
bin/cashbook-agent --help
bin/cashbook-agent synth --seed 42 --out target/synth   # fake statements, golden cashbook, template
bin/cashbook-agent ingest --in target/synth/in --out target/ingest   # PDFs -> transactions/statements/documents JSONL
bin/cashbook-agent classify --in target/ingest --out target/classify --rules config/rules.example.yaml   # rules only
bin/cashbook-agent review --dir target/classify --rules config/rules.example.yaml                       # answer the queue
bin/cashbook-agent reconcile --classified target/classify --ingest target/ingest --period 2026-10:2027-03 \
    --out target/reconcile --rules config/rules.example.yaml                                           # cashbook lines
bin/cashbook-agent run --period 2026-10:2027-03 --in <folder> --template <xlsx> --out <xlsx>
```

Logs are JSONL on stderr, with one correlation id per run.

Scanned (image-only) PDFs are read with Tesseract. Install it for local OCR: `brew install tesseract` on macOS, or `apt-get install tesseract-ocr` on Debian/Ubuntu. Without it, the OCR tests are skipped locally; CI and the Docker image include it.

Docker:

```sh
docker build -t cashbook-agent .
docker run --rm cashbook-agent --help
```

## Privacy

Real statements, the real rules file and the real golden cashbook go in `private/`, which is git-ignored. The repository, its tests and CI use synthetic data only.

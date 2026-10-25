# cashbook-agent

**An agentic pipeline that turns bank, card and wallet statements plus supplier invoices into a completed, accountant-ready GST cashbook.**

The tax maths is deterministic. An LLM is used only where judgment is needed, a human approves anything the agent is unsure about, and an evaluation harness measures accuracy against a hand-checked answer key.

- Goals, principles and phases: [PROJECT_BRIEF.md](PROJECT_BRIEF.md)
- Phase 1 build plan: [docs/BUILD_PROMPT.md](docs/BUILD_PROMPT.md)
- Synthetic test data and its traps: [docs/SYNTHETIC_DATA.md](docs/SYNTHETIC_DATA.md)

## Build and run

Requires Java 25 (Amazon Corretto 25). The Gradle wrapper does the rest.

```sh
./gradlew build                         # compile and test all modules
./gradlew :cashbook-app:quarkusBuild    # package the CLI
bin/cashbook-agent --help
bin/cashbook-agent synth --seed 42 --out target/synth   # fake statements, golden cashbook, template
bin/cashbook-agent run --period 2026-10:2027-03 --in <folder> --template <xlsx> --out <xlsx>
```

Logs are JSONL on stderr, with one correlation id per run.

Docker:

```sh
docker build -t cashbook-agent .
docker run --rm cashbook-agent --help
```

## Privacy

Real statements, the real rules file and the real golden cashbook go in `private/`, which is git-ignored. The repository, its tests and CI use synthetic data only.

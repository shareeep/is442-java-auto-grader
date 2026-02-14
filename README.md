# IS442 Auto-Grading System

Automated grading system for IS442 Java programming assignments. Extracts student submissions from ZIP files, compiles and runs tester files against student code, and generates grading reports.

## Quick Start
Run the gradle clean build after making changes, then use the one-shot command to quickly test that the program works.

```bash
# Build
./gradlew clean build

# Fix lint/formatting
./gradlew spotlessApply

# Run (interactive mode)
java -jar build/libs/autograder-1.0-SNAPSHOT-all.jar

# Run (one-shot mode)
java -jar build/libs/autograder-1.0-SNAPSHOT-all.jar \
  --submissions ./is442-project-materials/student-submission \
  --testers ./is442-project-materials/Tester-Files \
  --scoresheet ./is442-project-materials/IS442-ScoreSheet.csv \
  --output ./output

# Test (no unit tests for now)
./gradlew test
```

> **Note**: No Gradle installation needed — the Gradle Wrapper (`gradlew`) is included.
> Requires **Java 17+**.

## Features

- **ZIP extraction** — automatically unpacks and normalises student submissions
- **Identity resolution** — resolves student names from Java headers, scoresheet, or username
- **Compilation & execution** — compiles student code + tester files, runs with timeout protection
- **Anomaly detection** — flags missing headers, unrenamed folders, compilation errors
- **Dual CSV output** — LMS-compatible graded scoresheet + detailed per-question breakdown
- **Interactive CLI** — step-by-step prompts with `back` navigation

## Project Structure

```
src/main/java/com/is442/autograder/
├── App.java               # Entry point (CLI arg parsing)
├── GradingPipeline.java   # Orchestrates the full grading flow
├── config/                # AppConfig (reads config.properties)
├── extraction/            # ZipExtractor, StructureNormalizer, IdentityResolver
├── validation/            # SubmissionValidator (header checks, anomalies)
├── execution/             # GradingEngine + ProcessRunner (compile & run)
├── reporting/             # ConsoleReporter + CSVExporter
├── model/                 # StudentSubmission, QuestionResult, Anomaly, etc.
├── ui/                    # ConsoleUI (interactive mode)
└── util/                  # FileUtils

src/test/java/com/is442/autograder/   # Unit tests (see tests README)
```

## Documentation

| Document | Description |
|----------|-------------|
| [commands.md](docs/commands.md) | Build, run, test, and lint commands |
| [design_overview.md](docs/design_overview.md) | Architecture and class diagrams |
| [tests README](src/test/java/com/is442/autograder/README.md) | Test guide |
| [new_test_case_generation.md](docs/new_test_case_generation.md) | Guide for creating test submissions |
| [orig_full_plan.md](docs/orig_full_plan.md) | Original project plan and requirements |

## Tech Stack

- **Java 17** — source and target compatibility
- **Gradle 9.3** — build system (wrapper included, config cache enabled)
- **JUnit 5** — testing framework
- **Spotless** — auto-formatting (Eclipse JDT) + cleanup (unused imports, trailing whitespace)
- **Checkstyle** — code style enforcement (Google Java Style)

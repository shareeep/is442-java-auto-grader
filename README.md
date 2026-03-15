# IS442 Auto-Grading System

Automated grading system for IS442 Java programming assignments. It extracts student submissions from ZIP files, compiles and runs tester files against student code, and generates grading reports — reducing manual marking effort significantly.

> **Unable to see Unicode characters** — for Windows users
> Go to Control Panel > Clock and Region (Change date, time, or number formats) > Administrative > Change system locale... > Ensure "Use Unicode UTF-8..." is checked.

## Quick Start

> **Note**: No Gradle installation needed — the Gradle Wrapper (`gradlew`) is included.
> Requires **Java 17+**.

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

# Run tests
./gradlew test
```

| Argument | Required | Description |
|----------|----------|-------------|
| `--submissions` | Yes | Folder containing student ZIP files |
| `--testers` | Yes | Folder containing tester `.java` files |
| `--scoresheet` | No | Input scoresheet CSV (LMS format); enables official name/ID mapping |
| `--output` | No | Output directory (default: `./output`) |

## Regenerating Extra Test Submissions

```bash
# macOS/Linux
./setup-test-submissions.sh

# Windows
setup-test-submissions.bat
```

The final submissions will be under `is442-project-materials/student-submission/`.

## Features

- **ZIP extraction** — automatically unpacks and normalises student submissions
- **Identity resolution** — resolves student names from Java headers, scoresheet, or username
- **Compilation & execution** — compiles student code + tester files, runs with timeout protection
- **Anomaly detection** — flags missing headers, unrenamed folders, compilation errors
- **Dual CSV output** — LMS-compatible graded scoresheet + detailed per-question breakdown
- **Interactive CLI** — step-by-step prompts with `back` navigation

## System Architecture (OO Design)

### Overview

```
┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐
│  EXTRACT │──▶│ VALIDATE │──▶│ COMPILE  │──▶│ EXECUTE  │──▶│  REPORT  │
└──────────┘   └──────────┘   └──────────┘   └──────────┘   └──────────┘
     │              │              │              │              │
     ▼              ▼              ▼              ▼              ▼
  Unzip file    Check folder   Run javac     Run tester    Generate
  + resolve     structure &    on student    with timeout  CSV scores
  identity      fix issues     + tester                    + anomalies
```

### Pipeline Flow

The system processes each submission through a **linear staged pipeline**:

```
START → Load Config → Scan ZIPs → [For each ZIP]
  → Extract → Parse Username → Validate → Normalize
  → [For each Question] → Copy Tester → Compile → Run (with timeout) → Parse Score
  → Sum Scores → [Next Student] → Export CSV → END
```

### Separation of Concerns (5 Layers)

```
┌─────────────────────────────────────────────────────────────┐
│                      UI LAYER                               │
│   Handles all user interaction (menu, inputs, progress)     │
├─────────────────────────────────────────────────────────────┤
│                   SERVICE LAYER                             │
│   Contains all business logic:                              │
│   - Extract ZIPs, validate structure, compile, execute      │
├─────────────────────────────────────────────────────────────┤
│                    MODEL LAYER                              │
│   Pure data objects (no logic, just state):                 │
│   - Student info, scores, results, anomalies                │
├─────────────────────────────────────────────────────────────┤
│                  REPORTING LAYER                            │
│   Transforms results into output formats:                   │
│   - CSV export, console summary, anomaly reports            │
├─────────────────────────────────────────────────────────────┤
│                   CONFIG LAYER                              │
│   Externalized settings (no hardcoding):                    │
│   - Paths, timeout, question definitions                    │
└─────────────────────────────────────────────────────────────┘
```

Data flow: `User Input → UI → Service → Model → Reporting → Output` (Config feeds into Service)

### Class Responsibilities

| Layer | Classes | Responsibility |
|-------|---------|----------------|
| Entry | `App`, `GradingPipeline` | CLI parsing, pipeline orchestration |
| Extraction | `ZipExtractor`, `StructureNormalizer`, `IdentityResolver` | Unzip, fix folder layout, parse student name/email |
| Validation | `SubmissionValidator` | Check structure, flag anomalies (missing files, bad headers) |
| Execution | `GradingEngine`, `ProcessRunner` | `javac` compile + `java` run with timeout, capture score |
| Reporting | `ConsoleReporter`, `CSVExporter`, `QuestionLogWriter` | Console progress, graded CSV, per-question logs |
| Model | `StudentSubmission`, `QuestionResult`, `Anomaly`, `StudentIdentity` | Immutable data objects passed between stages |
| Config | `AppConfig` | Reads `config.properties`; single source of truth for paths and timeouts |
| UI | `ConsoleUI` | Interactive step-by-step prompts with `back` navigation |

Each layer has one job — changes in one layer don't affect others (e.g. change CSV format → only touch Reporting; switch to GUI → only replace UI Layer).

## Open-Source Libraries

| Library | Version | Purpose | Why chosen |
|---------|---------|---------|------------|
| **JUnit 5** | 5.x | Unit testing | Industry-standard Java testing framework; integrates directly with Gradle |
| **Spotless** | — | Auto-formatting (Eclipse JDT) + import cleanup | Enforces consistent style automatically on save/push; zero-config for Java |
| **Checkstyle** | — | Static code style enforcement (Google Java Style) | Catches style violations at build time; widely adopted in Java projects |
| **JFreeChart** | 1.5.4 | Chart generation for reports | Mature, well-documented Java charting library; no external runtime needed |
| **OpenPDF** | 1.3.42 | PDF report export | Open-source iText fork; actively maintained and licence-friendly (LGPL) |
| **Lanterna** | 3.1.2 | Terminal UI rendering | Pure Java TUI library; enables a rich interactive CLI without native deps |

## Algorithms

| Algorithm | Used For | Chosen? | Trade-offs |
|-----------|----------|---------|-----------|
| **Regex pattern matching** | Parse student name/email from Java file headers | ✅ Yes | Simple, fast, and deterministic. Fails only if headers are entirely absent or malformatted — acceptable for a structured academic submission. |
| **Fuzzy / normalised string matching** | Map parsed names to scoresheet rows (strip punctuation, lowercase) | ✅ Yes | Handles minor typos and casing differences without external NLP libraries. Less accurate than edit-distance but sufficient for this domain. |
| **Edit-distance (Levenshtein)** | Alternative for name matching | ❌ Not used | More accurate for larger typos but adds complexity and false-positive risk when names are short. |
| **Linear pipeline** | Processing each submission through Extract → Validate → Compile → Execute → Report | ✅ Yes | Predictable, easy to debug, and trivially parallelisable per-student. Slightly slower than parallel-stage approaches but correctness is prioritised. |
| **Process timeout** | Guard against infinite-loop student code | ✅ Yes | Simple OS-level interrupt via `ProcessBuilder` + `waitFor(timeout)`. Prevents a single bad submission from hanging the entire run. |

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

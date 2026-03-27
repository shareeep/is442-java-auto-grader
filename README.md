# IS442 Auto-Grading System

Automated grading system for IS442 Java programming assignments. Extracts student submissions from ZIP files, compiles and runs tester files against student code, and generates grading reports — with an AI-powered test generation web UI.

## Modes

| Mode | Command | Description |
|------|---------|-------------|
| **Web UI** | `./gradlew bootRun` | Spring Boot + React frontend (port 8080) |
| **CLI grading** | `java -jar build/libs/autograder-1.0-SNAPSHOT-all.jar --submissions ...` | One-shot grading |
| **Interactive CLI** | `java -jar build/libs/autograder-1.0-SNAPSHOT-all.jar --cli` | Step-by-step prompts |
| **Docker** | `docker compose up` | Full stack: Docling (5001) + backend (8080) + frontend (80) |

## Quick Start

Requires **Java 25+** and a running **Docling Serve** instance (or `docker compose up`).

```bash
# Build
./gradlew clean build

# Run web UI
./gradlew bootRun

# Grade submissions (CLI)
java -jar build/libs/autograder-1.0-SNAPSHOT-all.jar \
  --submissions ./is442-project-materials/student-submission \
  --testers ./is442-project-materials/Tester-Files \
  --output ./output

# Generate test cases (CLI)
java -jar build/libs/autograder-1.0-SNAPSHOT-all.jar \
  --generate-tests --exam-pdf ./exam.pdf --template-dir ./template --tester-dir ./testers

# Fix lint
./gradlew spotlessApply
```

## Features

- **ZIP extraction** — automatically unpacks and normalises student submissions
- **Identity resolution** — resolves student names from Java headers, scoresheet, or username
- **Compilation & execution** — compiles student code + tester files, runs with timeout protection
- **Anomaly detection** — flags missing headers, unrenamed folders, compilation errors
- **PDF report generation** — instructor reports with charts (JFreeChart + OpenPDF)
- **Dual CSV output** — LMS-compatible graded scoresheet + detailed per-question breakdown
- **AI test generation** — LangChain4j + Docling; structured JSON output, code refinement loop
- **Config inference** — auto-detects question structure from exam PDF markdown
- **Web UI** — React + TypeScript + Vite; VS Code-themed 4-step wizard
- **Live grading terminal** — SSE stream of grading progress
- **Past runs viewer** — browse previous grading sessions

## Project Structure

```
src/main/java/com/is442/autograder/
├── App.java                    # Entry point (--web, --cli, --generate-tests)
├── GradingPipeline.java        # Orchestrates grading flow (with SSE callbacks)
├── config/                     # AppConfig, EnvLoader
├── data/                       # SessionDatabase (SQLite)
├── extraction/                 # ZipExtractor, StructureNormalizer, IdentityResolver
├── validation/                 # SubmissionValidator
├── execution/                  # GradingEngine + ProcessRunner
├── generation/                 # TestGenerationService, LangChainService, PdfParser, ConfigInferenceService
├── reporting/                  # PdfReportGenerator, ChartGenerator, CSVExporter
├── model/                      # StructuredTestCase, InferredConfig, StudentSubmission, etc.
├── web/                        # Spring Boot controllers + DTOs
├── ui/                         # ConsoleUI (Lanterna TUI)
└── util/                       # FileUtils

frontend/
├── src/components/wizard/      # 4-step wizard (ProjectSetup → InferenceReview → GenerationHub → FinalizeExport)
├── src/components/             # GradingTerminal, FileTreeView, ui/ (shadcn)
├── src/pages/                  # GraderWorkspace, PastRuns, RunResults, TestGenerator
├── src/generated/              # HeyAPI-generated types, SDK, TanStack Query hooks
└── openapi-spec.json           # Cached OpenAPI spec from backend
```

## API Type Generation

Types and query hooks are auto-generated from the backend OpenAPI spec:

```bash
# 1. Start backend
./gradlew bootRun

# 2. Generate types + hooks
cd frontend && npm run generate:api
```

Output: `frontend/src/generated/` — types, SDK functions, `useQuery`/`useMutation` hooks.

## Documentation

| Document | Description |
|----------|-------------|
| [UPDATES.md](docs/UPDATES.md) | Modernization summary, phase status, file inventory, WIP tasks |
| [TESTCASES.md](docs/TESTCASES.md) | 18 test submissions and edge case coverage |
| [design-overview.md](docs/[old]%20design-overview.md) | Original 5-layer architecture (historical) |

## Tech Stack

- **Java 25** + **Gradle 9.3** (wrapper included)
- **Spring Boot 3.5.12** — REST API
- **LangChain4j** — AI integration (`@AiService`)
- **Docling Serve** — PDF parsing with OCR
- **SQLite** — session cache
- **React 19 + TypeScript + Vite** — frontend
- **TanStack Query** — server state caching (5min stale, 30min gc)
- **HeyAPI (`@hey-api/openapi-ts`)** — auto-generate types + hooks from OpenAPI spec
- **Tailwind CSS + shadcn/ui** — styling
- **Spotless + Checkstyle** — code style
- **Docker Compose** — 3-service deployment

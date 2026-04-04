# IS442 Auto-Grading System

Automated grading system for IS442 Java programming assignments. Extracts student submissions from ZIP files, compiles and runs tester files against student code, and generates grading reports with an AI-powered test generation web UI.

## Modes

| Mode | Command | Description |
|------|---------|-------------|
| **CLI grading** | `java -jar build/libs/autograder-1.0-SNAPSHOT-all.jar --submissions ...` | One-shot grading (auto-infers questions) |
| **Interactive CLI** | `java -jar build/libs/autograder-1.0-SNAPSHOT-all.jar --cli` | Step-by-step prompts |
| **Web UI (Docker)** | `docker compose up` | Full stack: backend (8080) + frontend (5173) + Docling (5001) |

## CLI Grading

Questions are **auto-inferred** from tester files at runtime — no config needed.

```bash
# Build the fat JAR
./gradlew fatJar

# One-shot grading
java -jar build/libs/autograder-1.0-SNAPSHOT-all.jar \
  --submissions ./is442-project-materials/student-submission \
  --testers ./is442-project-materials/Tester-Files \
  --output ./output

# Interactive CLI mode
java -jar build/libs/autograder-1.0-SNAPSHOT-all.jar --cli
```

### Interactive CLI flow
1. Enter submissions folder
2. Enter testers folder
3. Review inferred questions (auto-detected from tester files)
4. Optionally add scoresheet CSV
5. Choose output directory

## Web UI (React + Docker Compose)

Full-stack web application with AI-powered test generation, live grading terminal, and past runs viewer.

### Setup

**Environment:** Create `.env` file in project root:
```
OPENROUTER_API_KEY=contact us for access
DOCLING_SERVE_URL=http://localhost:5001
```

**Launch:**
```bash
docker compose up
```

Starts three services:
- **Frontend** (http://localhost) — React UI (Vite, Tailwind + shadcn/ui)
- **Backend** (http://localhost:8080) — Spring Boot REST API
- **Docling** (http://localhost:5001) — PDF parsing with OCR

**API docs:** [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)

### Web UI Features

#### 1. **Test Generator Wizard** (4-step flow)
- **Step 1: Project Setup** — Upload exam PDF, template files, tester data files
- **Step 2: Inference Review** — Auto-detects questions from PDF and file structure; review & edit
- **Step 3: Generation Hub** — AI analyzes each question and generates test cases using LangChain4j
- **Step 4: Finalize & Export** — Split-pane code viewer; optionally refine with AI feedback loop

#### 2. **Auto-Grader Workspace**
- Upload student submissions (ZIP files)
- Auto-infer questions from testers
- Execute grading with live progress terminal (SSE stream)
- Generate PDF reports with charts and dual CSV outputs (LMS-compatible + detailed breakdown)

#### 3. **Past Runs Viewer**
- Browse all previous grading sessions
- Inspect results per student and per question
- Download reports (PDF, CSV) and generated test code

#### 4. **Features**
- **AI Test Generation** — Structured JSON output (no syntax errors), deterministic assembly, concept coverage analysis
- **PDF Parsing with OCR** — Docling Serve extracts questions from scanned PDFs, diagrams, and images
- **Config Auto-Inference** — Questions auto-detected from tester filenames, template folder structure, and PDF headings
- **Live Grading Logs** — Real-time progress stream (SSE) with student count and status
- **Code Review Interface** — Side-by-side syntax-highlighted comparison of template vs generated code

## Dev

```bash
# Fix lint
./gradlew spotlessApply
```

## Features

**Core Grading:**
- **ZIP extraction** — automatically unpacks and normalises student submissions
- **Identity resolution** — resolves student names from Java headers, scoresheet, or username
- **Compilation & execution** — compiles student code + tester files, runs with timeout protection
- **Anomaly detection** — flags missing headers, unrenamed folders, compilation errors
- **PDF report generation** — instructor reports with charts (JFreeChart + OpenPDF)
- **Dual CSV output** — LMS-compatible graded scoresheet + detailed per-question breakdown
- **Config inference** — auto-detects question structure from tester files and PDF headings

**AI & Test Generation:**
- **LangChain4j integration** — AI service with structured JSON output (no syntax errors)
- **Docling PDF parsing** — OCR-capable extraction from scanned PDFs, diagrams, and images
- **Concept-aware generation** — AI recommends test count and concepts to cover based on existing tests
- **Deterministic assembly** — structured test cases assembled into valid Java code
- **Code refinement loop** — iteratively improve generated tests via AI feedback

**Web UI:**
- **4-step wizard** — guided workflow for exam setup, question inference, test generation, and export
- **Live grading terminal** — real-time SSE stream of compilation and execution progress
- **Past runs viewer** — browse and inspect previous grading sessions
- **Code review interface** — side-by-side syntax-highlighted code comparison
- **Auto-inferred configuration** — questions detected automatically from files and PDF structure

## Project Structure

```
src/main/java/com/is442/autograder/
├── App.java                    # Entry point (--web, --cli)
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
├── src/components/test-generator/  # 4-step wizard (ProjectSetup → InferenceReview → GenerationHub → FinalizeExport)
├── src/components/auto-grader/     # GradingTerminal, FileTreeView, FileUploadCard, ResultsTable, SubmissionDetails
├── src/components/layout/          # Layout, Topbar
├── src/components/ui/              # shadcn/ui primitives
├── src/pages/                      # GraderWorkspace, PastRuns, RunResults, TestGenerator
├── src/store/                      # Zustand stores (graderStore, wizardStore)
├── src/api/                        # uploadTemplate (direct fetch helpers)
├── src/generated/                  # HeyAPI-generated types, SDK, TanStack Query hooks
└── openapi-spec.json               # Cached OpenAPI spec from backend
```

## API Type Generation

Types and query hooks are auto-generated from the backend OpenAPI spec (`frontend/src/generated/openapi-spec.json`).

```bash
# Generate types + hooks from committed spec
cd frontend && npm run generate:api
```

Output: `frontend/src/generated/` — types, SDK functions, `useQuery`/`useMutation` hooks.

**When you change backend endpoints, request/response types:** re-fetch the spec from the running backend and regenerate in one step:

```bash
./scripts/sync-api-types.sh
```

This fetches the spec from `localhost:8080`, writes `openapi-spec.json`, and reruns the generator. Commit both `openapi-spec.json` and the regenerated files together. Any frontend code that uses the updated types must be adjusted manually afterward.

## Documentation

| Document | Description |
|----------|-------------|
| [design-overview.md](docs/design-overview.md) | Current system design, architecture, and runtime data flows |
| [UPDATES.md](docs/[new]%20UPDATES.md) | Modernization summary, phase status, file inventory, WIP tasks |
| [ai-processing-flow.md](docs/[for-ref]%20ai-processing-flow.md) | AI generation architecture, flow, and configuration for reference |
| [TESTCASES.md](docs/TESTCASES.md) | 18 test submissions and edge case coverage |

## Tech Stack

- **Java 25** + **Gradle 9.3** (wrapper included)
- **Spring Boot 3.5.12** — REST API
- **LangChain4j** — AI integration (`@AiService`)
- **Docling Serve** — PDF parsing with OCR
- **SQLite** — session cache
- **React 19 + TypeScript + Vite** — frontend
- **TanStack Query v5** — server state caching (5min stale, 30min gc)
- **HeyAPI (`@hey-api/openapi-ts`)** — auto-generate types + hooks from OpenAPI spec
- **Tailwind CSS + shadcn/ui** — styling
- **Spotless + Checkstyle** — code style
- **Docker Compose** — 3-service deployment

**Requirements:**
- Docker & Docker Compose (for web UI)
- OpenRouter API key (for AI test generation)
- Docling Serve running on localhost:5001 (started by docker-compose)
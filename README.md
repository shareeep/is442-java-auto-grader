# IS442 Auto-Grader

Automated grading system for IS442 Java programming assignments. Extracts student submissions from ZIP files, compiles and runs tester files against student code, and generates grading reports with an AI-powered test generation web UI.

## Quick Start

### Setup
```
# Create `.env` file (Refer to .env.example)
```

### CLI (no Docker needed)

```bash
# Build
./gradlew fatJar

# One-shot
java -jar build/libs/autograder-1.0-SNAPSHOT-all.jar \
  --submissions ./student-submissions \
  --testers ./Tester-Files \
  --output ./results

# Interactive CLI
java -jar build/libs/autograder-1.0-SNAPSHOT-all.jar --cli
```

### Web UI (Docker)

```bash
docker compose up
```

| Service | URL |
|---------|-----|
| Frontend | http://localhost:5173 |
| Backend API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| JPlag Viewer | http://localhost:1996 |
| Docling (PDF OCR) | http://localhost:5001 |


---

## Feature Matrix

| Capability | CLI | Web UI |
|-----------|-----|--------|
| Auto-infer questions from testers | ✅ | ✅ |
| ZIP extraction + structure normalization | ✅ | ✅ |
| Identity resolution from Java headers | ✅ | ✅ |
| Per-question compilation + execution | ✅ | ✅ |
| Timeout protection | ✅ | ✅ |
| Scoresheet CSV enrichment | ✅ | ✅ |
| PDF report with charts | ✅ | ✅ |
| Dual CSV output (LMS + detailed) | ✅ | ✅ |
| JPlag plagiarism checking | ✅ | ✅ |
| SSE live grading stream | — | ✅ |
| AI test generation wizard | — | ✅ |
| Docling PDF parsing (OCR) | — | ✅ |
| Config inference from PDF | — | ✅ |
| Browse past runs | — | ✅ |
| Download all results as ZIP | — | ✅ |
| Plagiarism viewer | — | ✅ |

---

## Tech Stack

**Backend:** Java 25 · Gradle 9.3 · Spring Boot 3.5 · LangChain4j · SQLite (session cache) · OpenPDF · JFreeChart · JPlag 6.3.0

**Frontend:** React 19 · TypeScript · Vite · TanStack Query v5 · Zustand · HeyAPI · Tailwind CSS · shadcn/ui · Lucide icons

**Infrastructure:** Docker Compose · Nginx · Docling Serve · JPlag Report Viewer (git-cloned from github.com/jplag/JPlag)

---

## Project Structure

```
src/main/java/com/is442/autograder/
├── App.java                        # Entry: --web, --cli, or one-shot args
├── GradingPipeline.java            # 5-step orchestrator with SSE callbacks
├── config/
│   └── AppConfig.java             # config.properties > application.properties
├── data/
│   └── SessionDatabase.java        # SQLite session cache (exams, questions, configs)
├── extraction/
│   ├── ZipExtractor.java           # ZIP extraction with bomb/traversal protection
│   ├── IdentityResolver.java       # Parses Name/Email from Java headers
│   └── StructureNormalizer.java    # Flattens nested submission folders
├── execution/
│   ├── GradingEngine.java         # Per-question: copy tester → compile → run → parse
│   ├── ProcessRunner.java          # javac/java process wrapper with timeout
│   └── PlagiarismChecker.java     # JPlag 6.3.0 wrapper
├── generation/
│   ├── TestGenerationService.java # AI orchestrator with 3-retry logic
│   ├── LangChainService.java      # LangChain4j interface (vision + text beans)
│   ├── PdfParser.java             # Docling Serve HTTP client
│   ├── ConfigInferenceService.java # Auto-detect Q structure from folders + PDF
│   └── TesterFileWriter.java      # Assembles structured cases → Java code
├── reporting/
│   ├── PdfReportGenerator.java    # 7-section instructor PDF (OpenPDF + JFreeChart)
│   ├── CSVExporter.java           # LMS-compatible grade sheet
│   ├── DetailedCsvExporter.java   # Per-question breakdown CSV
│   └── ChartGenerator.java       # Score distribution, pass rate, anomaly charts
├── web/
│   ├── GradingStreamController.java  # SSE streaming grading
│   ├── ExamController.java           # PDF upload + parse + analyze
│   ├── GenerationController.java      # AI wizard endpoints
│   ├── ReportsController.java         # Past runs + artifact serving
│   └── UploadRegistry.java           # ConcurrentHashMap in-memory upload store
├── model/                          # Immutable record classes
└── ui/
    └── ConsoleUI.java              # Lanterna terminal TUI

frontend/
├── src/
│   ├── components/
│   │   ├── test-generator/         # Wizard: Upload → Review → Generate → Export
│   │   └── auto-grader/           # GradingTerminal, ResultsTable, SubmissionDetails
│   ├── pages/
│   │   ├── GraderWorkspace.tsx     # Main grading tab
│   │   ├── TestGenerator.tsx       # AI wizard wrapper
│   │   └── PastRuns.tsx           # Run history browser
│   ├── store/
│   │   ├── graderStore.ts         # Zustand: phase, result, runId
│   │   └── wizardStore.ts         # Zustand: step, examId, configs
│   ├── generated/                  # HeyAPI output: types + TanStack Query hooks
│   └── openapi-spec.json           # Cached from backend /v3/api-docs
└── package.json

plagiarism-viewer/                  # Git-cloned JPlag report viewer (Vue.js)
docker-compose.yml
```

---

## HeyAPI — Auto-Generated Types & Hooks

The frontend uses **HeyAPI** to auto-generate TypeScript types and TanStack Query hooks from the backend's OpenAPI spec. No manual API wiring.

```bash
# Generate from running backend
cd frontend && npm run generate:api
```

Output lives in `frontend/src/generated/`. On every backend OpenAPI change: re-fetch spec + regenerate → commit both spec and generated files together.

See [docs/API.md](docs/API.md) for the full endpoint reference.

---

## Documentation

| Guide | What it covers |
|-------|----------------|
| [docs/README.md](docs/README.md) | Docs index |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Package map, design decisions, Mermaid diagrams |
| [docs/design-overview.md](docs/design-overview.md) | Comprehensive architecture & design deep-dive |
| [docs/CLI-GUIDE.md](docs/CLI-GUIDE.md) | One-shot + interactive CLI usage |
| [docs/AI-TEST-GENERATION.md](docs/AI-TEST-GENERATION.md) | Wizard walkthrough + system prompts + retry logic |
| [docs/PARSING.md](docs/PARSING.md) | PDF parsing, question inference, file upload filtering |
| [docs/API.md](docs/API.md) | REST endpoint reference |
| [docs/DEMO-GUIDE.md](docs/DEMO-GUIDE.md) | Demo presentation script (4 flows + tech highlights) |
| [docs/diagrams/](docs/diagrams/) | Mermaid diagrams: class, sequence, deployment, flow |


# Demo Guide

A walkthrough script for demoing the IS442 Auto-Grader. Covers all 4 runtime flows and key technical highlights.

**Tested and working:**
1. Normal auto-grader flow in frontend (18 test submissions)
2. Auto-grader with AI-generated test cases (max 5 per question)
3. Full AI generation from scratch (no existing testers)
4. CLI one-shot (run details viewable in frontend)
5. Interactive CLI flow

---

## Prerequisites

```bash
docker compose up          # starts all 4 services
```

| Service | URL | Purpose |
|---------|-----|---------|
| Frontend | http://localhost:5173 | React web UI |
| Backend | http://localhost:8080 | Spring Boot REST API |
| Docling | http://localhost:5001 | PDF OCR + parsing |
| Plagiarism Viewer | http://localhost:1996 | JPlag report viewer |

Have ready:
- Student submission ZIPs (18 test cases in `is442-project-materials/`)
- Exam PDF
- Tester-Files directory
- Template (starter code) directory

---

## Flow 1: CLI One-Shot

Show that grading can be done entirely from the command line with a single command.

```bash
java -jar build/libs/autograder-1.0-SNAPSHOT-all.jar \
  --submissions ./student-submissions \
  --testers ./Tester-Files \
  --output ./results \
  --scoresheet ./IS442-ScoreSheet.csv \
  --name "IS442 Midterms"
```

**Demo points:**
- Show the parameters: `--submissions`, `--testers`, `--output`, `--scoresheet`, `--name`
- Run the command — live progress prints for each student
- Show the output folder: PDF report, CSV scores, plagiarism report, per-student logs
- Mention: results are also viewable in the frontend's Past Runs page

---

## Flow 2: Interactive CLI

Start the interactive terminal UI for instructors who prefer guided prompts.

```bash
java -jar build/libs/autograder-1.0-SNAPSHOT-all.jar --cli
```

**Demo points:**
- Walk through the step-by-step prompts (select submissions, testers, scoresheet)
- Show the **Lanterna** terminal UI with progress bar and live output
- Same output artifacts as one-shot mode
- Press `Q` to exit (known minor display quirk: character not visually echoed)

---

## Flow 3: Frontend Grading (Main Demo)

Show that the web UI replicates the CLI flow with richer tooling.

### Steps

1. **Auto-Grader tab** — upload student submission ZIPs + tester `.java` files
2. **Start grading** — watch the SSE live stream in the terminal component (real-time per-student progress)
3. **View results** — scores table with per-question breakdown

### Showcase the tooling

| Feature | What to show |
|---------|-------------|
| PDF report viewer | Click "View PDF" — inline viewer, no download needed |
| JPlag plagiarism viewer | Click "Plagiarism" — opens the JPlag viewer at `:1996` |
| Download all | Click "Download" — entire run as a single `.zip` |
| IDE-style code viewer | Click a student row — LeetCode-style layout: student code on top, test cases at bottom |
| Past Runs | Navigate to Past Runs — browse all previous grading sessions |

**Key point:** Same `GradingPipeline` runs underneath — the frontend is a UI layer on top of the same grading engine used by the CLI.

---

## Flow 4: AI Test Generation (Star Feature)

Demonstrate the AI-powered test case generation wizard.

### Setup

- Introduce **Docling Serve** — an added service in Docker Compose that parses PDF files including images via OCR
- This powers the exam analysis step

### Step 1 — Upload

Upload three things in the **Test Generator** tab:
- **Exam PDF** — the question paper
- **Template folder** — student starter code (e.g., `RenameToYourUsername/`)
- **Tester-Files folder** — existing testers (optional — can be empty for full generation from scratch)

### Step 2 — Review

The system infers the question structure from **two sources**:
1. **Folder names** — scans template and tester directories for patterns like `Q1a`, `Q2b`
2. **PDF content** — regex-matches headings like `## Question 1a` from the Docling-parsed markdown

Cross-references both to produce an `InferredConfig` with question IDs, folder mappings, tester class names, and max scores. Instructors can review and correct any misdetections.

**Be clear about the inference logic:** folders provide structure, PDF provides context and max scores. When both agree, confidence is high.

### Step 3 — Generate

Two stages per question:

1. **Recommendations** — AI analyses the question text + existing testers and suggests up to **5 concepts** not yet covered (e.g., "Boundary: empty list", "Exception: null input")
2. **Generation** — based on those recommendations, test cases are generated as structured JSON, then assembled into Java tester code

**Retry mechanism:** Up to 3 attempts with exponential backoff (3s, 6s, 12s). Retries on null response, JSON parse failure, or empty results.

**What the AI receives as input:**
- System prompt with strict JSON schema constraints
- Question markdown (extracted per-question from PDF)
- Existing tester code (to replicate method invocation style)
- Student template code (to know available method signatures)
- Data file contents (`.txt` files in question folders)
- API documentation (if present in template)
- Recommended concepts + custom instructor instructions

### Step 4 — Export

Review the generated testers and export. The generated files are ready to use as tester inputs.

### Re-grade with new testers

1. Switch to the **Auto-Grader** tab
2. Upload student submissions + the **newly generated testers**
3. Run grading — show how the marks differ now that additional test cases are checking edge cases
4. View the updated results

### Full generation from scratch (if time permits)

Same flow but skip uploading existing testers — the AI generates all test cases from scratch based solely on the exam PDF and template code.

---

## Output Artifacts

Important to showcase the `output/` folder structure:

| File | Description |
|------|-------------|
| `instructor-report.pdf` | 7-section PDF with charts: overview, question performance, anomalies, structural validation, execution diagnostics, per-student results, insights |
| `IS442-ScoreSheet-Graded.csv` | LMS-compatible grade sheet |
| `detailed-report.csv` | Per-question breakdown for all students |
| `results.json` | Structured JSON (consumed by frontend) |
| `plagiarism-report.jplag` | JPlag report (open in the plagiarism viewer) |
| `code/{username}/` | Extracted student `.java` source files |
| `testers/` | Copy of the tester files used |
| `logs/` | Per-student compilation and execution logs |
| `run.json` | Run metadata (timestamp, status, student count) |

---

## Tech Highlights

Call these out during the demo at appropriate moments:

| Technology | Where | Why |
|-----------|-------|-----|
| **Lanterna** | CLI interactive mode | Terminal UI library for the progress bar and step-by-step prompts |
| **React 19 + Vite + TypeScript** | Frontend | Modern SPA with fast HMR |
| **shadcn/ui** | Frontend components | Pre-built accessible components (buttons, cards, inputs, dialogs) — including community components |
| **HeyAPI** | Frontend API layer | Auto-generates TypeScript types + SDK functions from backend OpenAPI spec — zero manual API wiring |
| **TanStack Query v5** | Frontend data fetching | Server state management with caching (5min stale, 30min GC) |
| **Zustand 5** | Frontend client state | Lightweight stores with Immer middleware for immutability |
| **LangChain4j** | Backend AI | Two model beans — vision (Mimo Omni) for PDFs with images, text (MiniMax) for text-only |
| **Docling Serve** | PDF parsing | OCR + markdown extraction from exam PDFs, preserves images as base64 |
| **SSE streaming** | Grading progress | `SseEmitter` streams per-student results to the frontend in real time |
| **JPlag 6.3.0** | Plagiarism detection | Generates `.jplag` reports viewable in the bundled plagiarism viewer |
| **SQLite** | Session cache | Lightweight file-based cache for parsed PDFs and inferred configs — no database server needed |

---

## Repo Structure Walkthrough

When walking through the codebase:

```
src/main/java/com/is442/autograder/
├── App.java                  # Entry point: --web, --cli, or one-shot args
├── GradingPipeline.java      # 5-step grading orchestrator
├── config/                   # AppConfig, EnvLoader
├── data/                     # SessionDatabase (SQLite)
├── extraction/               # ZipExtractor, StructureNormalizer, IdentityResolver
├── validation/               # SubmissionValidator
├── execution/                # GradingEngine, ProcessRunner, PlagiarismChecker
├── generation/               # TestGenerationService, LangChainService, PdfParser
├── reporting/                # PdfReportGenerator, CSVExporter, ChartGenerator
├── model/                    # Data classes (StudentSubmission, QuestionResult, etc.)
├── web/                      # Spring Boot controllers + UploadRegistry
└── ui/                       # ConsoleUI (Lanterna TUI)

frontend/src/
├── components/
│   ├── test-generator/       # 4-step wizard (Upload → Review → Generate → Export)
│   ├── auto-grader/          # GradingTerminal, ResultsTable, SubmissionDetails
│   └── ui/                   # shadcn/ui components
├── pages/                    # GraderWorkspace, TestGenerator, PastRuns, RunResults
├── store/                    # Zustand stores (graderStore, wizardStore)
└── generated/                # HeyAPI output: types + SDK functions
```

See [ARCHITECTURE.md](./ARCHITECTURE.md) for the full package map and design decisions.

# IS442 Auto-Grader - Design Overview (Updated)

This document supersedes the older design overview in [[old] design-overview.md]([old]%20design-overview.md).

---

## 1. System Scope

The IS442 Java Auto-Grader is a modern, multi-modal grading platform supporting three runtime modes:

1. **One-shot CLI** — Batch grading from command line without interaction
2. **Interactive CLI** — Step-by-step prompts for folder selection and configuration
3. **Web UI** — Full-stack React application with AI-powered test generation and live grading terminal

The system evolved from a simple CLI tool into a comprehensive platform that automates exam setup, intelligent test generation, and distributed grading with detailed reporting.

---

## 2. Simple Architecture (5-Step Pipeline)

Core grading logic remains a linear, predictable pipeline:

```
┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐
│ EXTRACT  │──▶│ VALIDATE │──▶│ COMPILE  │──▶│ EXECUTE  │──▶│ REPORT   │
└──────────┘   └──────────┘   └──────────┘   └──────────┘   └──────────┘
     │              │              │              │              │
     ▼              ▼              ▼              ▼              ▼
  Unzip &      Normalize      Javac on        Run tester    Generate CSV
  resolve      structure &    student+         with timeout  + PDF report
  identity     fix issues     tester files                   + anomalies
```

This core pipeline is **shared** across all three modes. The UI layer changes; the grading logic does not.

---

## 3. Runtime Architecture (3-Service Deployment)

In web mode, the system runs as a containerized 3-service stack:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         DOCKER COMPOSE                                  │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────┐ │
│  │  FRONTEND        │  │  BACKEND         │  │  DOCLING SERVE       │ │
│  │  (React)         │  │  (Spring Boot)   │  │  (OCR Service)       │ │
│  │  Port: 5173      │  │  Port: 8080      │  │  Port: 5001          │ │
│  │                  │  │                  │  │                      │ │
│  │ - Test Generator │◄─┤ - REST API       │◄─┤ - PDF parsing        │ │
│  │ - Auto-Grader    │  │ - Grading Core   │  │ - Markdown extract   │ │
│  │ - Past Runs      │  │ - AI Orchestr.   │  │ - Image recognition  │ │
│  │ - File Upload    │  │ - Config Infer.  │  │                      │ │
│  │ (Vite, Tailwind) │  │ - Report Gen.    │  │                      │ │
│  │ (TanStack Query) │  │ - Session Cache  │  │                      │ │
│  │ (Zustand)        │  │ (SQLite / Java)  │  │                      │ │
│  └──────────────────┘  └──────────────────┘  └──────────────────────┘ │
│           │                     △                        △              │
│           └─────────── HTTP/REST ─────────────────────────┘            │
│                                   │                                     │
│                                   ▼                                     │
│                         ┌──────────────────┐                            │
│                         │   OpenRouter     │                            │
│                         │   (External)     │                            │
│                         │                  │                            │
│                         │ - LLM inference  │                            │
│                         │ - Test generation│                            │
│                         │ - Recommendations│                            │
│                         └──────────────────┘                            │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### Service Responsibilities

| Service | Technology | Role |
|---------|-----------|------|
| **Frontend** | React 19 + TypeScript + Vite | Instructor UI — file upload, wizard flow, result visualization |
| **Backend** | Spring Boot 3.5 + Java 25 | API, orchestration, grading pipeline, AI calls via LangChain4j |
| **Docling** | OCR Service (external) | PDF parsing with document understanding (tables, images) |

**External:** OpenRouter API handles all LLM calls (model: `minimax/minimax-m2.7`).

---

## 4. Backend Layered Architecture

The backend is organized into **6 logical tiers**, each with a clear responsibility:

```
┌──────────────────────────────────────────────────────────────────────┐
│                       ENTRY POINT LAYER                              │
│  App.java (CLI: --cli, --submissions) / WebApplication.java (web)    │
├──────────────────────────────────────────────────────────────────────┤
│                      CONTROLLER LAYER                                │
│  ExamController, GenerationController, GradingController, etc.       │
│  (Convert HTTP → domain operations, handle upload IDs, stream SSE)   │
├──────────────────────────────────────────────────────────────────────┤
│                      SERVICE LAYER (DOMAIN)                          │
│  GradingPipeline, TestGenerationService, ConfigInferenceService,    │
│  PdfParser, LangChainService, TesterFileWriter, etc.                │
│  (Core business logic, orchestration, external API calls)           │
├──────────────────────────────────────────────────────────────────────┤
│               EXECUTION & VALIDATION LAYER                           │
│  ZipExtractor, StructureNormalizer, IdentityResolver,               │
│  JavaCompiler, ProcessRunner, SubmissionValidator, etc.             │
│  (Low-level operations: filesystem, compilation, process mgmt)      │
├──────────────────────────────────────────────────────────────────────┤
│                      MODEL LAYER (DTO/Record)                        │
│  StudentSubmission, StructuredTestCase, InferredConfig,             │
│  ProcessResult, Anomaly, StudentIdentity, etc. (Pure data, no logic)│
├──────────────────────────────────────────────────────────────────────┤
│               CONFIG & INFRASTRUCTURE LAYER                          │
│  AppConfig, EnvLoader, SessionDatabase (SQLite), FileUtils, Paths  │
│  (External file I/O, environment, caching, config management)       │
└──────────────────────────────────────────────────────────────────────┘
```

### Data Flow Through Layers

```
HTTP Request (upload, inference, generate, grade)
  ↓
Controller (parse REST, resolve UUIDs → Paths)
  ↓
Service Layer (business logic, coordinate operations)
  ↓
Execution Layer (run javac/java, parse ZIP, etc.)
  ↓
Model Objects (immutable records, no side effects)
  ↓
Session/File Storage (SQLite cache, config.properties, output CSV/PDF)
```

### Key Characteristics

1. **Fail-soft**: Anomalies are logged and continue; non-fatal errors don't stop pipelines.
2. **Timeout isolation**: Compilation and execution are bounded per process.
3. **SSE streaming**: Grading progress emitted to React terminal in real-time (4-thread executor).
4. **Deterministic generation**: AI output transformed into strict typed Java records before assembly.
5. **Session cache**: SQLite stores parsed PDFs + inferred configs to avoid re-parsing.

---

## 5. Grading Pipeline (CLI & Web)

Detailed flow for the core **Extract → Validate → Compile → Execute → Report** pipeline:

```
START (CLI or Web)
  │
  ▼
┌──────────────────────────────────────┐
│ Load Configuration                   │  ← config.properties (external > classpath)
│ - Execution timeout                  │
│ - Question definitions               │
│ - Report format options              │
└──────────┬───────────────────────────┘
           ▼
┌──────────────────────────────────────┐
│ Scan/Intake Submissions              │  ← From web: multipart uploads
│ - Find all *.zip files               │  ← From CLI: directory scan
│ - Validate archives                  │
└──────────┬───────────────────────────┘
           ▼
     ┌─────┴──────────────────────────────┐
     │ For each Student Submission        │
     └─────┬───────────────────────────────┘
           ▼
  ┌────────────────────────────────────┐
  │ Extract ZIP + Normalize Structure  │
  │ - Unzip to temp directory          │
  │ - Sanitize path traversals         │
  │ - Fix common folder issues         │
  │ - Detect & log anomalies           │
  └────────┬──────────────────────────┘
           ▼
  ┌────────────────────────────────────┐
  │ Resolve Student Identity           │
  │ - Parse Java file headers          │
  │ - Check scoresheet CSV             │
  │ - Fallback to folder name          │
  └────────┬──────────────────────────┘
           ▼
  ┌────────────────────────────────────┐
  │ Infer Questions (auto-detection)   │
  │ - Scan tester filenames            │
  │ - Match: *Tester.java → Question   │
  │ - Cross-ref config.properties      │
  │ - Cache in SessionDatabase         │
  └────────┬──────────────────────────┘
           ▼
     ┌─────┴─────────────────────────────┐
     │ For each Question                 │
     └─────┬─────────────────────────────┘
           ▼
  ┌────────────────────────────────────┐
  │ Prepare Compile Directory          │
  │ - Copy question folder             │
  │ - Copy tester .java file           │
  │ - Copy dependency .class files     │
  │ - Set classpath = "."              │
  └────────┬──────────────────────────┘
           ▼
  ┌────────────────────────────────────┐
  │ Compile (javac -encoding UTF-8)    │
  │ Timeout: 10 seconds (configurable) │
  └────────┬──────────────────────────┘
        ┌──┴──────────────┐
        │ Success? (Y/N)  │
        └──┬──────────┬───┘
       Yes │      No  │
           │          └─→ [Score = 0.0] + log compile error
           ▼              [Continue to next question]
  ┌────────────────────────────────────┐
  │ Execute (java -cp . TesterClass)   │
  │ Timeout: 10 seconds (configurable) │
  │ Capture stdout/stderr              │
  └────────┬──────────────────────────┘
        ┌──┴──────────────────────┐
        │ Execution Status?       │
        └──┬──────┬──────┬────────┘
           │      │      │
       OK  │      │      └─→ [Timeout] → kill process
           │      └──────→ [Crash/Exception]
           ▼              [Score = 0.0] + log error
  ┌────────────────────────────────────┐
  │ Parse stdout → Extract Score       │
  │ (Last line, numeric, or custom)    │
  └────────┬──────────────────────────┘
           ▼
     ┌─ [Continue to next question]
     │
     ▼
  [Score aggregation: sum per-question scores]
           │
           ▼
     ┌─────┴──────────────────────────────┐
     │ Next Student in Loop               │
     └─────┬──────────────────────────────┘
           ▼
     [All students processed]
           ▼
  ┌────────────────────────────────────┐
  │ Generate Reports                   │
  │ - CSV (LMS-compatible + detailed)  │
  │ - PDF (with charts + breakdown)    │
  │ - Anomaly summary                  │
  └────────────────────────────────────┘
           ▼
         DONE
```

---

## 6. AI Test Generation Pipeline (Web Only)

Flow for the 4-step **Test Generator Wizard**:

```
SELECT TEST GENERATOR (React UI)
  ▼
STEP 1: PROJECT SETUP
  │
  ├─ Upload exam PDF file        → POST /api/generation/exam/upload
  ├─ Upload template ZIP         → POST /api/generation/template/upload
  ├─ Upload tester ZIP           → POST /api/generation/testers/upload
  │
  └─→ Backend: Store uploads, return {examId, templateId, testerId}
                Store in ConcurrentHashMap<UUID, Path>

STEP 2: INFERENCE REVIEW
  │
  ├─ Call /api/generation/analyzeSetup
  │  ├─ PdfParser extracts markdown (Docling Serve or SessionDatabase cache)
  │  ├─ ConfigInferenceService scans tester filenames
  │  ├─ Infer questions: Q1a, Q1b, Q2a, Q2b, Q3 (example)
  │  └─ Return {questions[], maxScores[], conceptsDetected}
  │
  └─→ React: Display inferred config, allow user to edit/confirm

STEP 3: GENERATION HUB
  │
  ├─ User selects questions to generate tests for
  ├─ Call /api/generation/generate (for each question)
  │  ├─ LangChainService.generateTestCasesJson()
  │  │  └─ OpenRouter API → minimax/minimax-m2.7
  │  │     (Input: question description + existing tests)
  │  │     (Output: JSON with StructuredTestCase[])
  │  ├─ Parse JSON → List<StructuredTestCase>
  │  └─ Return: {generation_uuid, status, test_cases_count}
  │
  ├─ (Optional) Call /api/generation/recommend
  │  └─ LangChainService.recommendJson()
  │     (Suggest test count + concepts based on question difficulty)
  │
  └─→ React: Display generated test cases, live progress stream (SSE)

STEP 4: FINALIZE & EXPORT
  │
  ├─ Side-by-side code editor
  │  ├─ Left: template tester file (read-only)
  │  ├─ Right: generated tester (editable)
  │
  ├─ User optionally call /api/generation/refine
  │  ├─ LangChainService.refineCode()
  │  │  └─ OpenRouter API → improve code based on feedback
  │  └─ Return: refined code
  │
  ├─ Call /api/generation/save
  │  ├─ TesterFileWriter.write() → persist .java files
  │  ├─ Store in SessionDatabase
  │  └─ Return: {saved_path, files_written}
  │
  └─→ React: Show export success, offer download link

Generated tester files ready for downstream grading workflow.
```

---

## 7. Frontend Architecture (React + TanStack Query)

```
┌──────────────────────────────────────────────────────┐
│                   React Components                   │
├──────────────────────────────────────────────────────┤
│                                                      │
│  Pages:                 Modules:                     │
│  ├─ TestGenerator       ├─ ProjectSetup             │
│  ├─ GraderWorkspace     ├─ InferenceReview         │
│  ├─ PastRuns           ├─ GenerationHub            │
│  ├─ RunResults         └─ FinalizeExport           │
│  └─ SubmissionDetails                              │
│                                                      │
├──────────────────────────────────────────────────────┤
│              State Management                        │
├──────────────────────────────────────────────────────┤
│                                                      │
│  TanStack Query (Server State)                      │
│  ├─ useQuery()    → Fetch API data                  │
│  ├─ useMutation() → POST/PUT/DELETE                 │
│  ├─ 5min stale, 30min GC (auto-generated hooks)    │
│  └─ Cache invalidation on mutation success         │
│                                                      │
│  Zustand Stores (Client State)                      │
│  ├─ useWizardStore → step, IDs, recommendations    │
│  └─ useGraderStore → submissions, results, terminal│
│                                                      │
└──────────────────────────────────────────────────────┘
```

Hooks and types are **auto-generated** from OpenAPI spec via HeyAPI:
```bash
npm run generate:api  # Creates src/generated/ with types + hooks
```

---

## 8. Data Model

Key immutable records:

| Record | Purpose | Fields |
|--------|---------|--------|
| `StudentIdentity` | Resolved student info | name, emailId, orgId |
| `StudentSubmission` | Per-student bundle | identity, questions[], anomalies[] |
| `InferredConfig` | Auto-detected setup | questions[], maxScores[], concepts |
| `StructuredTestCase` | Semantic test unit | description, setup, methodCall, assertion, expectsException |
| `ProcessResult` | Execution outcome | status (SUCCESS/COMPILE_ERROR/RUNTIME_ERROR/TIMEOUT), output, error |
| `GradeResult` | Per-question score | questionId, score, testCases, anomalies |
| `Anomaly` | Issue detected | type (MISSING_HEADER, COMPILATION_ERROR, TIMEOUT), severity |

---

## 9. Configuration Management

### External Config (`./config.properties`)

Priority order:
1. **External file** at `./config.properties` (working directory) ← **Wins**
2. **Classpath** `config.properties` (bundled in JAR)

### Sample Config Keys

```properties
# Execution
execution.timeout.seconds=10
execution.max.parallel.threads=4

# Questions (auto-inferred, but can override)
questions.q1a.maxScore=3
questions.q1a.folder=Q1
questions.q1a.tester=Q1aTester.java
questions.q1a.dependency.folders=RenameToYourUsername

# PDF Parsing
docling.serve.url=http://localhost:5001

# Report
report.include.anomalies=true
```

### Environment Variables (`.env`)

```
OPENROUTER_API_KEY=sk-or-v1-...
DOCLING_SERVE_URL=http://localhost:5001   # [optional, overrides config]
```

Loaded by `EnvLoader.load()` on startup.

---

## 10. Deployment

### Build Artifacts

| Artifact | Command | Purpose | Size |
|----------|---------|---------|------|
| **fat JAR** | `./gradlew fatJar` | CLI uber-JAR (all deps) | ~80 MB |
| **boot JAR** | `./gradlew bootJar` | Spring Boot web JAR | ~60 MB |
| **Docker** | `docker compose up` | 3-service compose stack | - |

### Local Development (Single Machine)

```bash
# Start all three services
docker compose up

# OR run backend + frontend separately:
./gradlew bootRun                 # Backend on :8080
cd frontend && npm run dev         # Frontend on :5173
# (Docling started separately or via container)
```

### Docker Compose Services

```yaml
services:
  frontend:
    build: ./frontend
    ports: ['80:5173']
  backend:
    build: .
    ports: ['8080:8080']
    environment: [$OPENROUTER_API_KEY, $DOCLING_SERVE_URL]
  docling:
    image: docker.io/ds4sd/docling-serve:latest
    ports: ['5001:5001']
```

---

## 11. Design Evolution from Original

Compared to the **original linear CLI design** ([old] design-overview.md), the modern system adds:

| Feature | Old | New |
|---------|-----|-----|
| **Modes** | CLI only | CLI + interactive CLI + web UI |
| **Config** | Static `config.properties` | **Auto-inference** from files + UI |
| **Test Generation** | Manual (instructor writes) | **AI-powered wizard** (4 steps) |
| **PDF Support** | None | **Docling OCR** with caching |
| **Grading Progress** | Batch output at end | **Live SSE stream** to terminal |
| **Architecture** | Monolithic JAR | **3-service Docker** (frontend + backend + OCR) |
| **State Caching** | None | **SQLite session DB** |
| **UI** | Console-only | **React with Tailwind + shadcn/ui** |
| **Code Generation** | N/A | **Deterministic tester assembly** |

The grading core (Extract → Validate → Compile → Execute) remains **unchanged and solid**. All additions layer on top without disrupting it.

---

## 12. Performance & Scalability Notes

1. **Parallel Grading**: `GradingPipeline` uses `ExecutorService` (4 threads default) to grade multiple students concurrently.
2. **SSE Streaming**: Real-time events emitted per student completion (no buffering).
3. **Session Cache**: SQLite avoids re-parsing same PDFs during a session (configurable TTL in future).
4. **Timeout Bounds**: Every `javac` and `java` invocation is bounded; runaway code cannot freeze the system.
5. **Memory Management**: `.deleteOnExit()` on temp directories ensures cleanup even on crash.

---

## Appendix: Quick Reference

**Start web UI:**
```bash
docker compose up
```

**Run CLI grading:**
```bash
./gradlew fatJar
java -jar build/libs/autograder-*.jar --submissions ./subs --testers ./tests --output ./out
```

**Run interactive CLI:**
```bash
java -jar build/libs/autograder-*.jar --cli
```

**View API docs:**
Navigate to `http://localhost:8080/swagger-ui/index.html` after backend starts.

**Generate frontend types:**
```bash
cd frontend && npm run generate:api
```
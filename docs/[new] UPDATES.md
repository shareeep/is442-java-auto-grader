# UPDATES

## Source Documents

This synthesis consolidates content from the following docs (some now deleted):

| Doc | Description | Status |
|-----|-------------|--------|
| `impt-wip-plan.md` | 9-phase remediation plan (B1–B5, phases 0–8) | **Merged into this doc; original deleted** |
| `generated-tests-WIP.md` | Original bug list (B1–B5, F1–F3) | **Merged; original deleted** |
| `langchain-integration-walkthrough.md` | Detailed LangChain4j migration walkthrough (479 lines) | **Merged; original deleted** |
| `multithreading-proposal.md` | Parallel generation + grading proposals | **Merged; original deleted** |
| `issues.md` | Docker wildcard issue (6 lines) | **Merged; original deleted** |
| `tech-debt.md` | Architecture trade-offs (3 items) | Still exists |
| `[old] cli-commands.md` | CLI command reference (old) | Still exists (marked old) |
| `[old-dayner-v1] new-ai-test-generation-frontend.md` | Old frontend architecture (Dayner v1, 268 lines) | Still exists (marked old) |
| `[old] design-overview-full.md` | Original 1335-line project plan | Still exists (marked old) |
| `[old] design-overview.md` | 5-layer architecture + class diagram | Still exists (marked old) |
| `[old] ideas-for-swe-style-new-test-case-generation.md` | 3 approaches for test generation | Still exists (marked old) |
| `TESTCASES.md` | 18 test submissions (6 original + 12 generated) | Still exists |
| `README.md` (docs) | Docs index (2 lines) | Minimal |
| `README.md` (root) | Project README | Partially stale (missing web UI, AI features) |

---

## Summary

All 9 phases (0–8) from the remediation plan are **complete**. The repository has been modernized from a console-only Java app into a full-stack web application with Docker Compose orchestration, AI-powered test generation, and a VS Code-themed React frontend.

**Verified against `main` (822ac75) vs current state:** The grading core (reporting, extraction, validation, execution) was already solid on `main` — Tabitha's work. The modernization added the generation module, web layer, frontend, and LangChain4j integration on top of it.

---

## Phase-by-Phase Status

### Phase 0 — PDF Parse Diagnostic [DEVIATION]

| Planned | Actual |
|---------|--------|
| Standalone `PdfDiagnostic.java` with `main()` for CLI-based PDF inspection | **Not created as a standalone class.** Diagnostic functionality was folded directly into `PdfParser.java`, which handles extraction and error reporting in one place. |

**Status:** ✅ Functionally complete — the diagnostic capability exists but was not separated into its own class as originally planned.

---

### Phase 0.5 — Replace PDFBox with Docling (B2) ✅

| File | Status |
|------|--------|
| `build.gradle` | ✅ `docling-serve-client:0.5.0` added; PDFBox kept as fallback |
| `PdfParser.java` | ✅ Uses `DoclingServeApi` — Base64 `FileSource`, `ConvertDocumentRequest`, markdown output |
| `AppConfig.java` | ✅ `getDoclingServeUrl()` added, default `http://localhost:5001` |
| `config.properties` | ✅ `docling.serve.url=http://localhost:5001` |

**Why necessary:** PDFBox's `PDFTextStripper` cannot read images, diagrams, or tables rendered as images. Docling Serve provides structured markdown with OCR, solving B2 (PDF image support) automatically.

---

### Phase 1 — Missing Data Files (B5) ✅

Missing tester data files (`personstester.txt`, `studentstester.txt`) were added to the appropriate question folders. Config properties updated.

---

### Phase 2 — Config Generalisation (B4) ✅

| File | Status |
|------|--------|
| `ConfigInferenceService.java` | ✅ Scans exam PDF markdown, template dirs, tester dirs; cross-references and flags conflicts |
| `InferredConfig.java` | ✅ Model for full inferred configuration |
| `InferredQuestionConfig.java` | ✅ Per-question inferred config |
| `ConfigConflict.java` | ✅ Conflict model (MISSING_FOLDER, MISSING_TESTER, ORPHAN_TESTER, etc.) |
| `GenerationController.java` | ✅ `POST /api/generation/analyze-setup` and `POST /api/generation/execute` consolidated |
| `AppConfig.java` | ✅ `reload()` and `writeQuestionConfigs(InferredConfig)` added |

**Note:** `ConfigController` was **not** removed as originally planned — it still exists but is supplemented by the consolidated `GenerationController`. This is a minor deviation; the plan's intent (single-flow config + generation) is achieved.

---

### Phase 3 — Structured LLM Output (B1) ✅

| File | Status |
|------|--------|
| `StructuredTestCase.java` | ✅ Record with `description`, `conceptCovered`, `setup`, `methodCall`, `expected`, `weight`, `expectsException`, `exceptionType` |
| `TestGenerationService.java` | ✅ Uses `LangChainService` (LangChain4j) for structured JSON; `parseStructuredCases()` handles JSON; fallback on parse failure; `buildCodeFromStructured()` for deterministic assembly |
| `TesterFileWriter.java` | ✅ `buildCodeFromStructured()` implemented |

**Deviation from plan:** Original plan specified replacing `ClaudeApiClient`. The implementation went further — replaced the entire HTTP client with **LangChain4j** (`LangChainService.java` using `@AiService`), which is a cleaner abstraction with system messages baked in. ClaudeApiClient does not exist in the current codebase.

The LangChain4j migration also included:
- Model swap from `qwen/qwen3.5-397b-a17b` → `minimax/minimax-m2.7` (Qwen had excessive reasoning output)
- `reasoning-effort=low` — MiniMax mandates reasoning tokens; `"none"` causes 400 errors
- `max-tokens=4096` (was 99999, unrealistic)
- `timeout=180s` for slow API responses

---

### Phase 4 — AI-Inferred Test Count (B3) ✅

| File | Status |
|------|--------|
| `TestCaseRecommendation.java` | ✅ `questionId`, `recommendedCount`, `conceptsToCover`, `existingConcepts`, `rationale` |
| `LangChainService.java` | ✅ `recommendJson()` method with analyst system prompt |
| `GenerationController.java` | ✅ `POST /api/generation/recommend` endpoint |

---

### Phase 5 — Frontend Refactoring (7 → 4 Steps) ✅

| Planned Step | Actual Implementation |
|-------------|----------------------|
| 1. Project Setup | `wizard/ProjectSetup.tsx` |
| 2. Config & Questions | `wizard/InferenceReview.tsx` |
| 3. Generate | `wizard/GenerationHub.tsx` |
| 4. Review & Finalize | `wizard/FinalizeExport.tsx` — split-pane viewer (student template vs generated code), AI refinement loop, export |

**Bonus (not in plan):**
- `Wizard.tsx` — orchestrates the 4-step flow with stepper UI
- UI component library (`ui/button.tsx`, `ui/card.tsx`, `ui/code-editor.tsx`, etc.)
- TypeScript migration (`tsconfig.json`, `tsconfig.app.json`)
- `api/client.ts` — typed API client (86 lines)
- `FileTreeView.tsx`, `code-tabs.tsx` — file explorer and tabbed code views

**F1 (IDE-style review UI)** from `generated-tests-WIP.md`: ✅ Implemented as `FinalizeExport.tsx` with split-pane syntax highlighting (student template left, generated code right).

**F2 (Simplified input flow)** from `generated-tests-WIP.md`: ✅ Implemented as `ProjectSetup.tsx` — all path inputs on one page via drag-and-drop uploads.

**F3 (Save summary)** from `generated-tests-WIP.md`: ✅ Implemented as the export step in `FinalizeExport.tsx` with session summary and before/after comparison.

---

### Phase 6 — Deployment & Persistence ✅

| Item | Status |
|------|--------|
| `docker-compose.yml` | ✅ Three services: `docling` (:5001), `backend` (:8080), `frontend` (:5173→:80) |
| `Dockerfile` (backend) | ✅ Multi-stage Java/Gradle build |
| `frontend/Dockerfile` | ✅ Vite build, served via Nginx |
| Volume persistence | ✅ `./output:/app/output` mapped in docker-compose |

---

### Phase 7 — WIP Enhancements ✅

#### 7.1 Past Runs Viewer
| Item | Status |
|------|--------|
| `ReportsController.java` | ✅ `GET /api/reports/list`, `GET /{id}/pdf`, `GET /{id}/csv`, `GET /{id}/results`, `GET /{id}/code/{username}`, `GET /{id}/logs` |
| `PastRuns.tsx` | ✅ Frontend page |
| `RunResults.tsx` | ✅ Per-run results page with code viewer |

#### 7.2 Live Grading Logs (SSE)
| Item | Status |
|------|--------|
| `GradingStreamController.java` | ✅ SSE endpoint `POST /api/grade/stream` — streams `session`, `status`, `student`, `error`, `complete` events |
| `GradingTerminal.tsx` | ✅ Terminal-style UI component with real-time log streaming, student count, auto-scroll |
| `GradingPipeline.java` | ✅ Callback overloads: `run(..., Consumer<StudentSubmission> onStudentGraded, Consumer<String> onStudentStarted)` |

#### 7.3 VS Code / IDE Aesthetic
| Item | Status |
|------|--------|
| `index.css` | ✅ VS Code Dark Plus theme (background `#0D1117`, panels `#161B22`, text `#C9D1D9`) |
| `tailwind.config.js` | ✅ `vsc-bg`, `vsc-panel`, `vsc-border`, `vsc-blue`, `vsc-green`, etc. palette variables |
| Typography | ✅ JetBrains Mono font-family for monospace |
| Code viewer | ✅ `react-syntax-highlighter` with `vscDarkPlus` theme in `FinalizeExport.tsx` |
| Sharp edges | ✅ `borderRadius` set to `0.625rem` (10px); glow effects (`glow-blue`, `glow-accent`) |

---

### Phase 8 — Codebase Cleanup ✅

| Item | Status |
|------|--------|
| Legacy `components/steps/` (7-step files) | ✅ **Deleted** — no files found |
| Legacy `TestGenerationWizard/` | ✅ **Deleted** — directory does not exist |
| Legacy `StepWizard.tsx` | ✅ **Deleted** — file does not exist |

---

## Additional Work Beyond the WIP Plan

These were not in the WIP plan but were implemented during modernization. Items marked **[already on main]** existed before the modernization effort and are included for completeness.

| Item | Why It Matters |
|------|---------------|
| **Spring Boot web server** | `WebApplication.java`, `WebConfig.java`, full REST API layer — makes the frontend possible |
| **LangChain4j integration** | `@AiService` interface replaces raw `HttpClient`; auto-configured via `application.properties` |
| **Java 25** | Upgraded from Java 17; enables `String.stripTrailing()` and modern features |
| **Spring Boot 3.5.12** | Added Spring Boot (was not on main at all); initially 3.4.4, upgraded to 3.5.12 for LangChain4j |
| **`settings.gradle`** | Added `pluginManagement` block for Spring Boot 3.5.12 plugin resolution from Maven Central |
| **`build.gradle` deps** | Added `spring-boot-starter-web`, `jackson-databind`, `pdfbox`, `langchain4j`, `docling-serve-client` |
| **Environment variable support** | `EnvLoader.java` — `.env` file loading for API keys |
| **SQLite session cache** | `SessionDatabase.java` — parsed PDF markdown cached in `data/sessions.db`, avoids re-parsing |
| **Upload ID system** | Frontend sends UUIDs instead of raw paths; controller resolves via `ExamController.EXAM_FILES` maps |
| **DTO migration** | `GenerateRequest` / `SaveRequest`: `testerDir` → `testerId`, `templateDir` → `templateId` |
| **ExamController upload endpoints** | `POST /exam/upload`, `POST /template/upload`, `POST /testers/upload` |
| **CLI factory method** | `App.createLangChainService(config)` — programmatic `AiServices.create()` for CLI mode outside Spring |
| **`App.java` rewrite** | Added `--web`, `--cli`, `--generate-tests` modes; `@SpringBootApplication`; `EnvLoader.load()` |
| **`ConsoleUI.java` extension** | Added "Generate Test Cases" menu option (item 2); wiring for `TestGenerationService` |
| **PDF report generation** | `PdfReportGenerator.java` **[already on main]** — Tabitha's work |
| **Chart generation** | `ChartGenerator.java` **[already on main]** — Tabitha's work |
| **Tabitha's grading improvements** | Progress bar, unicode fixes, stop-grading support **[already on main]** |
| **Lanterna TUI** | Terminal UI rendering with `ConsoleUI.java` **[already on main]** |
| **Dual CSV output** | LMS-compatible graded scoresheet + detailed per-question breakdown **[already on main]** |
| **Checkstyle + Spotless** | Google Java Style enforcement, auto-formatting **[already on main]** |
| **Git hooks** | `config/git-hooks/pre-push` **[already on main]** |
| **Setup scripts** | `setup-test-submissions.sh` **[already on main]** |
| **`gradle.properties`** | **[already on main]** — configuration-cache enabled |
| **Springdoc OpenAPI** | `springdoc-openapi-starter-webmvc-ui:2.8.6` — serves `/v3/api-docs` for HeyAPI generation |
| **HeyAPI codegen** | `@hey-api/openapi-ts` generates types + SDK + TanStack Query hooks from OpenAPI spec into `src/generated/` |
| **TanStack Query** | `QueryClientProvider` in `main.tsx` — 5min staleTime, 30min gcTime; pages use `useQuery` instead of raw `useEffect`+`useState` |
| **`EnvLoader` fix** | Now calls `System.setProperty()` so Spring resolves `${OPENROUTER_API_KEY}` from `.env` |

---

## Bug Closure Matrix

| Bug | Description | Cleared? | How |
|-----|-------------|----------|-----|
| **B1** | Raw LLM code → syntax errors | ✅ | `StructuredTestCase` + `buildCodeFromStructured()` — deterministic assembly eliminates AI syntax errors |
| **B2** | PDF parser can't read images | ✅ | Docling Serve integration with OCR; markdown output preserves structure |
| **B3** | Hardcoded test case count | ✅ | `POST /api/generation/recommend` — AI analyzes question + existing tests, recommends count and concepts |
| **B4** | Hardcoded config paths | ✅ | `ConfigInferenceService` auto-detects from PDF headings, template dirs, tester dirs |
| **B5** | Missing data files for Q2 | ✅ | Tester data files added to question folders |

---

## Stale / Outdated Documentation

| Doc | Issue |
|-----|-------|
| `[old-dayner-v1] new-ai-test-generation-frontend.md` | Describes the old 7-step wizard (`TestGenerationWizard`, `Step1-7*.tsx`, `StepWizard.tsx`). All these components have been deleted and replaced with the 4-step `wizard/` directory. REST API reference also incomplete (missing `/analyze-setup`, `/recommend`, `/refine`, `/preparse-pdf`, `/template-source`). |
| `README.md` (root) | Lists Java 17 as requirement (actual: Java 25). Does not mention the web UI, Docker Compose, AI generation, LangChain4j, Docling, or the frontend. |
| `README.md` (docs) | Only 2 lines; does not reference `tech-debt.md`, `TESTCASES.md`, or this synthesis. |
| `[old] cli-commands.md` | References old CLI commands; should be superseded by root `README.md`. |

---

## Tech Debt (from `tech-debt.md`)

| # | Item | Status |
|---|------|--------|
| 1 | **Conditional frontend build** — `-PskipFrontend` flag makes build "magical" | ⚠️ Still in `build.gradle` |
| 2 | **Docling dependency** — app cannot run standalone without Docling container | ⚠️ Still required; no fallback parser |
| 3 | **Hardcoded wildcards in Docker** — `COPY --from=builder /app/build/libs/*web.jar` | ⚠️ Still in `Dockerfile` |

---

## Not Implemented (from other docs)

These items appear in other docs but were **not** implemented:

| Item | Source | Notes |
|------|--------|-------|
| **Parallel generation** (`CompletableFuture`) | `multithreading-proposal.md` Area 1 (now deleted) | Bulk `/generate` still sequential per question |
| **Parallel student grading** | `multithreading-proposal.md` Area 2 (now deleted) | `ConsoleReporter` redesign prerequisite; `GradingPipeline` still sequential |
| **Security: Input validation** | `[old] design-overview-full.md` §14 | No `InputValidator`, `SafeZipExtractor`, `JavaFileValidator`, `CSVValidator` classes |
| **Security: Execution sandbox** | `[old] design-overview-full.md` §14.4 | No sandboxed execution (Docker/chroot/SecurityManager) |
| **Plagiarism detection** | `[old] design-overview-full.md` §15.2 | No `CodeFingerprinter`, no JPlag integration |
| **Template detection** | `[old] design-overview-full.md` §15.1 | No hash-based or structural comparison of submissions vs template |
| **Common mistake analysis** | `[old] design-overview-full.md` §15.3 | No `MistakeAnalyzer` |
| **Dark mode toggle** | `index.css` | `.dark` CSS class exists but no UI toggle |
| **PdfDiagnostic standalone class** | `impt-wip-plan.md` Phase 0 (now deleted) | Absorbed into `PdfParser.java` |
| **`ConfigController` removal** | `impt-wip-plan.md` Phase 2 (now deleted) | Still exists alongside `GenerationController` |

---

## Architecture Summary (Current State)

```
┌─────────────────────────────────────────────────────────────────┐
│  Frontend (React + Vite + TypeScript + Tailwind)                │
│  /grader  /test-generator  /past-runs  /results/:runId          │
│  Wizard: ProjectSetup → InferenceReview → GenerationHub →       │
│          FinalizeExport                                          │
└──────────────────────────┬──────────────────────────────────────┘
                           │ HTTP (upload IDs, not raw paths)
┌──────────────────────────▼──────────────────────────────────────┐
│  Spring Boot REST API                                           │
│  GenerationController: /analyze-setup, /recommend, /refine,     │
│                        /execute, /generate, /save                │
│  GradingStreamController: /grade/stream (SSE)                   │
│  ReportsController: /reports/list, /{id}/pdf, /{id}/results     │
│  ExamController: /exam/upload, /template/upload, /testers/upload│
└──────────────────────────┬──────────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────────┐
│  Service Layer                                                    │
│  TestGenerationService ← LangChainService (@AiService)          │
│  ConfigInferenceService   TesterFileWriter                      │
│  GradingPipeline (with SSE callbacks)                           │
│  GradingEngine ← ProcessRunner                                  │
│  ZipExtractor ← StructureNormalizer ← IdentityResolver          │
│  SubmissionValidator                                            │
└──────────────────────────┬──────────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────────┐
│  Infrastructure                                                   │
│  SessionDatabase (SQLite)  AppConfig  EnvLoader                 │
│  PdfReportGenerator  ChartGenerator  CSVExporter                │
│  ConsoleUI (Lanterna TUI)                                       │
└─────────────────────────────────────────────────────────────────┘

Docker Compose: docling(:5001) + backend(:8080) + frontend(:80)
```

---

## Complete File Change Inventory

### Backend — Generation Module (`generation/`)
| File | Change | Notes |
|------|--------|-------|
| `LangChainService.java` | **Added** (replaces ClaudeApiClient) | `@AiService` — 3 methods: generate, recommend, refine |
| `PdfParser.java` | **Added** (replaces ExamPdfParser) | Uses Docling Serve for OCR |
| `ConfigInferenceService.java` | **Added** | Auto-infer question configs from markdown |
| `TestGenerationService.java` | **Modified** | Was on main using ClaudeApiClient; rewritten to use LangChainService + `String examContext` |
| `TesterFileWriter.java` | **Modified** | Was on main; added `buildCodeFromStructured()` for deterministic assembly |
| `ClaudeApiClient.java` | **Deleted** | Was on main; replaced by LangChainService |
| `ExamPdfParser.java` | **Deleted** | Was on main; replaced by PdfParser |

### Backend — Models (`model/`)
| File | Change |
|------|--------|
| `StructuredTestCase.java` | **Added** — immutable record |
| `TestCaseRecommendation.java` | **Added** — AI recommendation |
| `InferredConfig.java` | **Added** — inferred config container |
| `InferredQuestionConfig.java` | **Added** — per-question inferred config |
| `ConfigConflict.java` | **Added** — conflict detection |
| `GeneratedTestCase.java` | **Added** — API response model |
| `GenerationResult.java` | **Added** — generation result container |

### Backend — Web Controllers (`web/`)
| File | Change |
|------|--------|
| `GenerationController.java` | **Added** — 10+ endpoints |
| `GradingStreamController.java` | **Added** — SSE grading stream |
| `ReportsController.java` | **Added** — past runs API |
| `ExamController.java` | **Modified** — upload endpoints added |
| `ConfigController.java` | **Kept** — supplementary config endpoints |
| `WebApplication.java` | **Added** — Spring Boot entry point |
| `WebConfig.java` | **Added** — bean registration |
| `WebController.java` | **Added** — web routes |
| `SpaForwardController.java` | **Added** — SPA routing |
| `dto/GenerateRequest.java` | **Added** — upload IDs |
| `dto/SaveRequest.java` | **Added** — save request |
| `dto/QuestionSelection.java` | **Added** — question selection |
| `dto/SaveResponse.java` | **Added** — save response |

### Backend — Config & Data
| File | Change |
|------|--------|
| `AppConfig.java` | **Modified** — `getDoclingServeUrl()`, `writeQuestionConfigs()` |
| `EnvLoader.java` | **Added** — `.env` file loading |
| `SessionDatabase.java` | **Added** — SQLite cache |

### Backend — Reporting **[all already on main, unchanged]**
| File | Notes |
|------|-------|
| `PdfReportGenerator.java` | Tabitha's OpenPDF report — no changes from main |
| `ChartGenerator.java` | Tabitha's JFreeChart — no changes from main |
| `ConsoleLogCapture.java` | No changes from main |
| `ScoresheetEnricher.java` | No changes from main |
| `QuestionLogWriter.java` | No changes from main |

### Frontend
| File | Change |
|------|--------|
| `App.tsx` | **Rewritten** — React Router with 4 routes |
| `components/wizard/Wizard.tsx` | **Added** — 4-step orchestrator |
| `components/wizard/ProjectSetup.tsx` | **Added** — Step 1 |
| `components/wizard/InferenceReview.tsx` | **Added** — Step 2 |
| `components/wizard/GenerationHub.tsx` | **Added** — Step 3 |
| `components/wizard/FinalizeExport.tsx` | **Added** — Step 4 (split-pane + refine) |
| `components/GradingTerminal.tsx` | **Added** — SSE terminal |
| `components/ui/*.tsx` | **Added** — shadcn component library (10+ files) |
| `pages/GraderWorkspace.tsx` | **Added** — grading workspace |
| `pages/PastRuns.tsx` | **Added** — history viewer |
| `pages/RunResults.tsx` | **Added** — run details |
| `pages/TestGenerator.tsx` | **Added** — test generator page |
| `api/client.ts` | **Added** — typed API client |
| `types.ts` | **Added** — TypeScript interfaces |

### Infrastructure
| File | Change |
|------|--------|
| `build.gradle` | **Modified** — added Spring Boot 3.5.12, LangChain4j, docling deps; Java 25; bootJar/bootRun tasks; buildFrontend task |
| `settings.gradle` | **Added** — plugin management for Spring Boot 3.5.12 |
| `application.properties` | **Added** — LangChain4j config, logging |
| `docker-compose.yml` | **Added** — 3 services (docling, backend, frontend) |
| `Dockerfile` | **Added** — multi-stage backend build |
| `frontend/Dockerfile` | **Added** — Vite + Nginx |
| `.dockerignore` | **Added** |
| `.env.example` | **Added** — template for API keys |
| `gradle.properties` | **[already on main]** — no changes |
| `config/git-hooks/pre-push` | **[already on main]** — no changes |
| `config/checkstyle/checkstyle.xml` | **[already on main]** — no changes |
| `setup-test-submissions.sh` | **[already on main]** — no changes |
| `setup-test-submissions.bat` | **[already on main]** — no changes |

### Files Deleted (from main or from intermediate commits)
| File | Notes |
|------|-------|
| `ClaudeApiClient.java` | Was on main; replaced by `LangChainService` |
| `ExamPdfParser.java` | Was on main; replaced by `PdfParser` |
| `bin/` directory | Removed from repo (build artifacts) |
| `frontend/src/components/steps/Step1-7*.tsx` | Old 7-step wizard (intermediate); replaced by `wizard/` |
| `frontend/src/components/TestGenerationWizard/` | Old barrel export (intermediate); replaced by `wizard/Wizard.tsx` |
| `frontend/src/components/StepWizard.tsx` | Old step navigation (intermediate); replaced by `wizard/Wizard.tsx` |

---

## Session Database Schema (SQLite)

`SessionDatabase.java` manages `data/session.db` with 3 tables:

```
exams
├── id            TEXT PRIMARY KEY (UUID)
├── original_filename TEXT
├── parsed_markdown   TEXT (full Docling markdown output)
└── created_at    INTEGER (epoch ms)

questions
├── id                INTEGER PRIMARY KEY AUTOINCREMENT
├── exam_id           TEXT (FK → exams.id)
├── question_id       TEXT (e.g. "Q1a")
├── markdown          TEXT (question section markdown)
├── folder            TEXT (e.g. "Q1")
├── tester            TEXT (e.g. "Q1aTester")
├── max_score         REAL
└── inferred_from_pdf INTEGER (0/1)

exam_configs
├── id            INTEGER PRIMARY KEY AUTOINCREMENT
├── exam_id       TEXT (FK → exams.id)
├── config_json   TEXT (serialized InferredConfig)
└── updated_at    INTEGER (epoch ms)
```

---

## WIP Tasks

### 1. HeyAPI / OpenAPI for Frontend Type Generation ✅

**Status:** Done.

**Setup:**
- `springdoc-openapi-starter-webmvc-ui:2.8.6` added to `build.gradle` — serves `/v3/api-docs`
- `@hey-api/openapi-ts` configured in `frontend/openapi-ts.config.ts` — generates from `openapi-spec.json`
- Generated output: `frontend/src/generated/` — types, SDK, TanStack Query hooks
- Script: `npm run generate:api` (run after backend is up)

**Migration:**
- `PastRuns.tsx` — uses hand-written `usePastRuns()` query hook
- `RunResults.tsx` — uses generated `getResultsOptions()` + `useQuery`
- `GraderWorkspace.tsx` — uses generated `listRunsOptions()` + `useQuery`
- TanStack Query `QueryClientProvider` in `main.tsx` — 5min staleTime, 30min gcTime
- Old hand-written `api/client.ts` kept for non-generated calls (SSE, file uploads)
- Old `hooks/use-queries.ts` deleted

**Workflow:** `./gradlew bootRun` → `npm run generate:api` → types stay in sync with backend

**Bug fix:** `EnvLoader.load()` now calls `System.setProperty(key, value)` so Spring can resolve `${OPENROUTER_API_KEY}` from `.env` file.

### 2. SQLite Schema as Single Source of Truth

**Problem:** The SQLite schema is defined inline in `SessionDatabase.initialize()` via raw SQL strings. There's no standalone schema file, no migration tooling, and no documentation of the schema outside of this synthesis.

**Proposed solution:**
1. Extract schema to `src/main/resources/db/schema.sql`
2. Optionally add [Flyway](https://flywaydb.org/) or [Liquibase](https://www.liquibase.com/) for migration management
3. Keep this synthesis's schema section in sync (or better, generate docs from `schema.sql`)

### 3. Documentation Cleanup ✅

**Status:** Mostly done. Root `README.md` updated with Java 25, web UI, HeyAPI, TanStack Query. `UPDATES.md` doc link fixed. Remaining cleanup:

- `[old-dayner-v1] new-ai-test-generation-frontend.md` can be deleted (content already in this doc)
- `docs/README.md` needs a proper index of remaining docs

---

## Readiness Assessment

| Area | Status | Confidence |
|------|--------|------------|
| Core grading pipeline | ✅ Working | High |
| AI test generation | ✅ Working | High |
| Web UI (React) | ✅ Built | Medium (E2E unverified) |
| Docker deployment | ✅ Configured | Medium (E2E unverified) |
| CLI mode | ✅ Working | High |
| PDF parsing (Docling) | ✅ Working | High |
| SQLite caching | ✅ Working | High |
| Parallel processing | ❌ Not done | N/A |
| Security/sandboxing | ❌ Not done | N/A |
| Plagiarism detection | ❌ Not done | N/A |
| Documentation | ⚠️ Partially stale | Some [old] docs can be cleaned up |

---

*Generated: 2026-03-28. Updated: 2026-03-28 — added HeyAPI, TanStack Query, EnvLoader fix. Verified against `main` (822ac75) vs `HEAD` (c29fe1e). Sources: all docs in `docs/` + git diff + source code analysis.*

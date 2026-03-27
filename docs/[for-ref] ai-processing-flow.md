# AI Test Generation — Processing Flow

End-to-end data flow from the React frontend through the Spring Boot backend, covering every API call, service chain, and AI interaction.

---

## Overview

```
User (Browser)
  │
  ▼
React Wizard (4 steps)
  │  State: Zustand store
  │    examId, templateId, testerId
  │    inferredConfig, selectedQs, results, localCode
  │
  ▼ HTTP (upload IDs, not raw paths)
Spring Boot REST API
  │  ConcurrentHashMap: examId/templateId/testerId → temp Path
  │
  ├─► GenerationController.analyzeSetup()
  │      └─ ConfigInferenceService.inferConfig()
  │             ├─ PdfParser.extractAllText()
  │             │      └─ Docling Serve (http://localhost:5001) → markdown
  │             │             (or SessionDatabase cache)
  │             └─ filesystem scan → InferredConfig
  │
  ├─► GenerationController.execute()
  │      └─ TestGenerationService.generateForQuestion()
  │             └─ LangChainService.generateTestCasesJson()
  │                    └─ OpenRouter API → AI-generated test cases JSON
  │
  ├─► GenerationController.recommend()
  │      └─ TestGenerationService.recommendTestCases()
  │             └─ LangChainService.recommendJson()
  │                    └─ OpenRouter API → test case recommendations
  │
  ├─► GenerationController.refine()
  │      └─ TestGenerationService.refineCode()
  │             └─ LangChainService.refineCode()
  │                    └─ OpenRouter API → refined Java code
  │
  ├─► GenerationController.save()
  │      └─ TesterFileWriter.write() → .java files on disk
  │
  └─► SessionDatabase  (SQLite — caches markdown + question metadata)
```

---

## Upload ID System

Uploads never send raw filesystem paths. Each upload returns a UUID that the backend maps to a temp directory. All subsequent API calls reference these UUIDs.

```
POST /api/generation/exam/upload      → {examId: "uuid"}
POST /api/generation/template/upload  → {templateId: "uuid"}
POST /api/generation/testers/upload   → {testerId: "uuid"}

Backend maps:
  EXAM_FILES     ConcurrentHashMap<String, Path>  examId     → /tmp/autograder-exam-.../exam.pdf
  TEMPLATE_DIRS  ConcurrentHashMap<String, Path>  templateId → /tmp/autograder-template-.../
  TESTER_DIRS    ConcurrentHashMap<String, Path>  testerId   → /tmp/autograder-testers-.../

Cleanup: File.deleteOnExit() on all temp dirs
```

**Template upload** reconstructs nested folder structure:
- Filenames contain `__SEP__` where `/` should be — decoded on write
- Result: `Q1/Main.java`, `Q2/Helper.java`, etc.

**Tester upload** is flat — all `*.java` files in one directory.

---

## Frontend: 4-Step Wizard

**State managed in Zustand (`useWizardStore`):**
```
examId, templateId, testerId
inferredConfig: InferredConfig | null
selectedQs: string[]
recommendations: Record<questionId, TestCaseRecommendation>
results: Record<questionId, GenerationResult>
localCode: Record<questionId, string>
exportComplete: boolean
```

---

### Step 1 — ProjectSetup

User provides three things: exam PDF, template directory, testers directory.

```
1. uploadExam(file)
   → POST /api/generation/exam/upload (FormData: file)
   ← {examId, fileName}
   → store.setExamId(examId)

2. preparsePdf(examId)                    ← non-blocking, fire-and-forget
   → POST /api/generation/preparse-pdf
      body: {examId}
   ← {status: "parsed" | "already_cached"}
   (frontend continues while PDF parses in background)

3. uploadTemplate(files[])
   → POST /api/generation/template/upload (FormData: files[])
   ← {templateId, fileCount}

4. uploadTesters(files[])
   → POST /api/generation/testers/upload (FormData: files[])
   ← {testerId, fileCount}

5. analyzeSetup({examId, templateId, testerId})
   → POST /api/generation/analyze-setup
   ← InferredConfig
   → store.setInferredConfig(inferredConfig)
   → NEXT
```

---

### Step 2 — InferenceReview

Displays the inferred config. No API calls — reads `store.inferredConfig`.

```
UI shows:
  - Questions list (with folder, tester, maxScore per question)
  - Conflicts (MISSING_FOLDER, MISSING_TESTER, ORPHAN_TESTER)
  - Stats: total marks, PDF scan %, folder match %, tester match %

User reviews/edits in UI, then confirms → NEXT
```

---

### Step 3 — GenerationHub

Two-pane: question selector (left) + generation controls per question (right).

```
For each selected question:

  A. "Recommend" button
     → POST /api/generation/recommend
        body: {examId, questionId, testerId}
     ← TestCaseRecommendation {recommendedCount, conceptsToCover, existingConcepts, rationale}
     → store.recommendations[questionId] = recommendation

  B. "Generate" button
     → POST /api/generation/execute
        body: {examId, testerId, templateId, numCases, question: InferredQuestionConfig}
     ← GenerationResult {questionId, cases[], compiledOk, generatedCode}
     → store.results[questionId] = result
     → store.localCode[questionId] = result.generatedCode
     → Show preview (first 3 cases)

Multiple questions can be generated in parallel.
NEXT enabled once any result exists.
```

---

### Step 4 — FinalizeExport

Split-pane code reviewer. Left: question tabs. Right: syntax-highlighted generated code.

```
A. "Refine" (per question)
   → POST /api/generation/refine
      body: {examId, questionId, currentCode, refinementPrompt}
   ← {questionId, refinedCode}
   → store.localCode[questionId] = refinedCode

B. "Export"
   → POST /api/generation/save
      body: {
        examId, testerId, outputDir,
        results: [{questionId, testerClassName, generatedCode, cases[]}],
        updateMaxScores: true
      }
   ← {savedPaths, errors}
   → store.exportComplete = true
```

---

## Backend: Endpoint Call Chains

### `POST /api/generation/preparse-pdf`

**Purpose:** Parse PDF via Docling and cache markdown — runs in background so Step 2 is fast.

```
GenerationController.preparsePdf({examId})
  │
  ├─ Resolve: EXAM_FILES.get(examId) → Path
  ├─ Check: SessionDatabase.getParsedMarkdown(examId)
  │    Hit → return {status: "already_cached"}
  │    Miss ↓
  ├─ PdfParser.extractAllText(examPath)
  │    └─ DoclingServeApi.convertDocument(Base64FileSource, ConvertDocumentRequest)
  │         → Docling Serve (http://localhost:5001)
  │         ← Markdown string (with OCR, tables, structure)
  │
  ├─ SessionDatabase.saveParsedExam(examId, filename, markdown)
  └─ Return {status: "parsed"}
```

---

### `POST /api/generation/analyze-setup`

**Purpose:** Infer question structure from PDF + filesystem. Writes to SQLite.

```
GenerationController.analyzeSetup({examId, templateId, testerId})
  │
  ├─ Resolve paths via EXAM_FILES / TEMPLATE_DIRS / TESTER_DIRS
  ├─ Load markdown: SessionDatabase.getParsedMarkdown(examId)
  │    Miss → PdfParser.extractAllText() → cache
  │
  └─ ConfigInferenceService.inferConfig(markdown, templateDir, testerDir)
       │
       ├─ STEP 1: Scan PDF markdown
       │    Regex: "## Question \d+[a-z]?"
       │    → ["Q1", "Q1a", "Q2", "Q3a", "Q3b"]
       │
       ├─ STEP 2: Scan template directory
       │    Pattern: folders matching Q\d+[a-z]?
       │    → Extract dependency files (.class, .txt) per folder
       │    → Match folders to question IDs
       │
       ├─ STEP 2b: Smart folder inference
       │    Unmatched Q1a → look for parent folder "Q1"
       │    Children inherit parent folder if no own folder
       │
       ├─ STEP 3: Scan tester directory
       │    Pattern: *Tester.java files
       │    "Q1Tester.java" → questionId "Q1"
       │    Count "tcNum++" occurrences → maxScore
       │
       ├─ STEP 3b: Sub-question hierarchy
       │    Q1a → create implicit parent Q1
       │    Q3a, Q3b → parent Q3 (from PDF or implicit)
       │    Children without folders inherit from parent
       │
       ├─ STEP 4: Cross-reference + conflict detection
       │    ┌────────────────────┬─────────────────────────────────────────┐
       │    │ Conflict type      │ Condition                               │
       │    ├────────────────────┼─────────────────────────────────────────┤
       │    │ MISSING_FOLDER     │ no folder, no parent folder, not        │
       │    │                    │ implicit parent                         │
       │    │ MISSING_TESTER     │ no tester, not implicit parent, in PDF  │
       │    │ ORPHAN_TESTER      │ has tester but not found in PDF         │
       │    │ INCOMPLETE_DEPS    │ depFiles contains "TODO_MISSING"        │
       │    └────────────────────┴─────────────────────────────────────────┘
       │
       └─ Return InferredConfig {questions[], conflicts[]}

  ├─ SessionDatabase.saveQuestion(examId, qid, qMarkdown, folder, tester, maxScore, inferredFromPdf)
  │    (one row per question)
  │
  └─ Return InferredConfig (200 OK)
```

---

### `POST /api/generation/recommend`

**Purpose:** Ask AI to analyse existing tests and recommend what new test cases to write.

```
GenerationController.recommend({examId, questionId, testerId})
  │
  ├─ Load question markdown from SessionDatabase
  ├─ Resolve testersDir via TESTER_DIRS.get(testerId)
  ├─ Read existing tester file: testersDir/{tester}.java
  │    Count "tcNum++" → existingCaseCount
  │
  └─ TestGenerationService.recommendTestCases(questionId, examContext, existingCode, count)
       │
       ├─ Build USER message:
       │    "Question ID: Q1\n"
       │    "=== EXISTING TEST CASES (3) ===\n[existing tester code]\n"
       │    "=== EXAM QUESTION ===\n[question markdown]"
       │
       └─ LangChainService.recommendJson(prompt)
            SYSTEM: "You are an expert Java test case analyst.
                     Analyse existing tests, recommend NEW complementary test cases.
                     Respond with JSON:
                     {questionId, existingConcepts[], recommendedCount, conceptsToCover[], rationale}
                     Do NOT repeat concepts already covered."
            ↓ OpenRouter API (minimax/minimax-m2.7)
            ← JSON string

       ├─ Parse JSON → TestCaseRecommendation record
       └─ Return TestCaseRecommendation (200 OK)
```

---

### `POST /api/generation/execute`

**Purpose:** Generate test cases for one question. The core AI generation call.

```
GenerationController.executeQuestion({examId, testerId, templateId, question, numCases})
  │
  ├─ Load question markdown: SessionDatabase.getQuestion(examId, questionId)
  │    Miss → PdfParser.extractQuestionSection() → cache
  ├─ Resolve: TESTER_DIRS, TEMPLATE_DIRS
  │
  └─ TestGenerationService.generateForInferredQuestion(iqc, examContext, existingTesterFile, numCases, templateDir)
       │
       ├─ Read existing tester code (if file exists)
       │
       ├─ Build additional context:
       │    ├─ Read student code from templateDir/[folder]/*.java
       │    ├─ Read data files (.txt) from template folder
       │    ├─ Extract referenced filenames from existing tester
       │    └─ Include data file contents in context
       │
       ├─ Build USER message:
       │    "Question ID: Q1\n"
       │    "Tester class: Q1Tester\n"
       │    "Exam context:\n[question markdown]\n"
       │    "Existing tester code:\n[existing .java]\n"
       │    "Additional context:\n[student code + data files]\n"
       │    "Generate exactly N test cases. Return ONLY valid JSON array..."
       │
       └─ LangChainService.generateTestCasesJson(prompt)
            SYSTEM: "You are a Java test case generator for an automated grading system.
                     Return ONLY a valid JSON array. Each element must have:
                       description, conceptCovered, setup, methodCall, expected,
                       assertion, weight, expectsException, exceptionType
                     Rules:
                     - Only use method/file names from existing tester code
                     - Vary inputs: edge cases, boundaries, null, large
                     - Do NOT repeat inputs from existing tests
                     - For file-based tests: use real data for expected values
                     - For exceptions: expectsException=true, assertion=null
                     - For void methods: assertion=null, expectsException=false
                     - No markdown fences, no explanation — pure JSON"
            ↓ OpenRouter API (minimax/minimax-m2.7, max_tokens=4096, timeout=180s)
            ← JSON string

       ├─ Parse JSON → List<StructuredTestCase>
       │    Strip markdown fences, Jackson deserialise
       │    Fallback: treat raw string as single test case on parse failure
       │
       ├─ TesterFileWriter.buildCodeFromStructured(cases)
       │    (see "Code Generation" section below)
       │
       ├─ Convert List<StructuredTestCase> → List<GeneratedTestCase>
       │    (simplified model for API response)
       │
       └─ Return GenerationResult {questionId, cases[], compiledOk, generatedCode}
```

---

### `POST /api/generation/refine`

**Purpose:** Apply user's natural-language refinement request to generated code.

```
GenerationController.refineCode({examId, questionId, currentCode, refinementPrompt})
  │
  ├─ Load exam context from SessionDatabase
  │
  └─ TestGenerationService.refineCode(questionId, currentCode, refinementPrompt, examContext)
       │
       ├─ Build USER message:
       │    "Question ID: Q1\n"
       │    "Exam context:\n[markdown]\n"
       │    "Current generated code:\n[java code]\n"
       │    "Refinement request: [user prompt]\n"
       │    "Return COMPLETE updated Java code..."
       │
       └─ LangChainService.refineCode(prompt)
            SYSTEM: "You are a Java test code refiner.
                     Apply the requested changes and return the COMPLETE updated code.
                     Preserve class/method signatures.
                     Only modify what was asked.
                     Return ONLY valid Java — no markdown, no explanation."
            ↓ OpenRouter API
            ← refined Java string

       └─ Return {questionId, refinedCode} (200 OK)
```

---

### `POST /api/generation/save`

**Purpose:** Write final tester files to disk and (optionally) update config.properties max scores.

```
GenerationController.saveResults(SaveRequest)
  │  SaveRequest: {examId, testerId, outputDir, results[], updateMaxScores}
  │
  ├─ Resolve: TESTER_DIRS.get(testerId) → testersDir
  │
  └─ For each result entry:
       │
       ├─ Read existing tester code from testersDir (backup source)
       │
       ├─ TesterFileWriter.write(testerClassName, originalCode, generatedCode, cases, testersDir, outputDir)
       │    ├─ Back up original: Q1Tester.java → Q1Tester.java.bak
       │    ├─ Substitute WEIGHT_N placeholders with actual weights
       │    ├─ Wrap generated code in full class structure
       │    └─ Write: outputDir/Q1_generated.java
       │
       ├─ If updateMaxScores:
       │    ├─ Calculate totalWeight from cases
       │    ├─ TesterFileWriter.updateConfigMaxScore(configPath, questionId, totalWeight)
       │    │    └─ Regex-replace matching line in config.properties
       │    └─ AppConfig.reload()
       │
       └─ Append savedPath to response list

  └─ Return SaveResponse {savedPaths[], errors[]}
```

---

## LangChain4j AI Service

**Interface:** `LangChainService.java` — three methods, each with a baked-in `@SystemMessage`.

```java
interface LangChainService {

  @SystemMessage("You are a Java test case generator... return ONLY valid JSON array...")
  String generateTestCasesJson(@UserMessage String prompt);

  @SystemMessage("You are an expert Java test case analyst... return ONLY valid JSON object...")
  String recommendJson(@UserMessage String prompt);

  @SystemMessage("You are a Java code refiner... return ONLY valid Java code...")
  String refineCode(@UserMessage String prompt);
}
```

**AI responses — two formats:**

| Method | AI returns | Parsed into |
|--------|-----------|-------------|
| `generateTestCasesJson` | JSON array | `List<StructuredTestCase>` POJO (Jackson) |
| `recommendJson` | JSON object | `TestCaseRecommendation` POJO (Jackson) |
| `refineCode` | Plain Java code | Raw `String` — used directly, no parsing |

JSON responses are deserialized into domain objects for type safety. Plain text (refine) is returned as-is.

**Configuration (`application.properties`):**
```properties
langchain4j.open-ai.chat-model.base-url=https://openrouter.ai/api/v1
langchain4j.open-ai.chat-model.api-key=${OPENROUTER_API_KEY}
langchain4j.open-ai.chat-model.model-name=minimax/minimax-m2.7
langchain4j.open-ai.chat-model.max-tokens=4096
langchain4j.open-ai.chat-model.timeout=180s
langchain4j.open-ai.chat-model.log-requests=true
langchain4j.open-ai.chat-model.log-responses=true
```

**Why these settings:**
- `minimax/minimax-m2.7` — replaces Qwen (had excessive reasoning output)
- `reasoning-effort=low` — MiniMax mandates reasoning tokens; `"none"` returns HTTP 400
- `timeout=180s` — LLM responses can be slow for large exam contexts
- `max-tokens=4096` — previous value of 99999 was unrealistic

**Bean registration** (in `WebConfig.java` or similar):
```java
@Bean
public LangChainService langChainService(ChatLanguageModel chatModel) {
    return AiServices.builder(LangChainService.class)
        .chatLanguageModel(chatModel)
        .build();
}
```

For CLI mode (outside Spring), `App.createLangChainService(config)` builds the service programmatically via `AiServices.create()`.

---

## StructuredTestCase → Java Code

**The model:**
```
StructuredTestCase {
  description       "Test with empty list"
  conceptCovered    "Boundary: empty list"
  setup             "int[] arr = new int[0];"
  methodCall        "myMethod(arr)"
  expected          "Empty array handled"
  assertion         "result.isEmpty()"     ← null for void/exception tests
  weight            1.0
  expectsException  false
  exceptionType     null                   ← "NullPointerException" if expectsException
}
```

**`TesterFileWriter.buildCodeFromStructured()` generates one of three patterns:**

**Pattern A — Exception test** (`expectsException = true`)
```java
{
    // Boundary: empty list: Test with empty list
    System.out.println("Test " + tcNum + ": Test with empty list");
    try {
        var result = myMethod(arr);
        System.out.println("  => Expected NullPointerException but none thrown — Failed");
    } catch (NullPointerException e) {
        System.out.println("  => Caught expected NullPointerException — Passed");
        score += 1.0;
    } catch (Exception e) {
        System.out.println("  => Wrong exception: " + e.getClass().getSimpleName() + " — Failed");
    }
    tcNum++;
}
```

**Pattern B — Void method test** (`assertion = null`, `expectsException = false`)
```java
{
    // Normal: valid input
    System.out.println("Test " + tcNum + ": Some description");
    try {
        int[] arr = new int[0];
        myMethod(arr);
        System.out.println("  => Passed");
        score += 1.0;
    } catch (Exception e) {
        System.out.println("  => Exception: " + e.getMessage() + " — Failed");
    }
    tcNum++;
}
```

**Pattern C — Assertion test** (has `assertion`)
```java
{
    // Normal: valid input
    System.out.println("Test " + tcNum + ": Test with valid input");
    try {
        int[] arr = new int[]{1, 2, 3};
        var result = myMethod(arr);
        System.out.println("  Expected: 6");
        System.out.println("  Actual:   " + result);
        if (result.isEmpty()) {
            System.out.println("  => Passed");
            score += 1.0;
        } else {
            System.out.println("  => Failed");
        }
    } catch (Exception e) {
        System.out.println("  => Exception: " + e.getMessage() + " — Failed");
    }
    tcNum++;
}
```

---

## SQLite Cache (SessionDatabase)

**File:** `data/session.db`

```
exams
├── id                TEXT PRIMARY KEY   (UUID = examId)
├── original_filename TEXT
├── parsed_markdown   TEXT               ← full Docling output, can be large
└── created_at        INTEGER            (epoch ms)

questions
├── id                INTEGER PK AUTOINCREMENT
├── exam_id           TEXT (FK → exams.id)
├── question_id       TEXT               "Q1a"
├── markdown          TEXT               question section from PDF
├── folder            TEXT               "Q1"
├── tester            TEXT               "Q1aTester"
├── max_score         REAL
└── inferred_from_pdf INTEGER            0 or 1

exam_configs
├── id                INTEGER PK AUTOINCREMENT
├── exam_id           TEXT (FK → exams.id)
├── config_json       TEXT               serialized InferredConfig
└── updated_at        INTEGER            (epoch ms)
```

**Cache hit/miss lifecycle:**
```
1. Upload PDF + preparsePdf()
   → PdfParser.extractAllText() → Docling (expensive)
   → saveParsedExam(examId, filename, markdown)

2. analyzeSetup()
   → getParsedMarkdown(examId)  [cache hit if preparsePdf ran]
   → ConfigInferenceService.inferConfig()
   → saveQuestion() for each inferred question

3. execute() / recommend()
   → getQuestion(examId, questionId)  [cache hit after analyzeSetup]
   → No re-parsing, no Docling call

4. All subsequent calls within same session
   → Pure DB reads — fast
```

---

## Key File Index

| File | Layer | Role |
|------|-------|------|
| `frontend/src/components/wizard/ProjectSetup.tsx` | FE | Step 1: uploads |
| `frontend/src/components/wizard/InferenceReview.tsx` | FE | Step 2: review inferred config |
| `frontend/src/components/wizard/GenerationHub.tsx` | FE | Step 3: recommend + generate |
| `frontend/src/components/wizard/FinalizeExport.tsx` | FE | Step 4: refine + export |
| `frontend/src/api/client.ts` | FE | Typed HTTP client |
| `web/GenerationController.java` | BE API | All `/api/generation/*` endpoints |
| `web/ExamController.java` | BE API | All upload endpoints |
| `web/dto/GenerateRequest.java` | BE DTO | Request body for `/execute` |
| `web/dto/SaveRequest.java` | BE DTO | Request body for `/save` |
| `generation/ConfigInferenceService.java` | BE Service | PDF + filesystem → InferredConfig |
| `generation/TestGenerationService.java` | BE Service | Prompt building + response parsing |
| `generation/LangChainService.java` | BE AI | @AiService — 3 LLM methods |
| `generation/PdfParser.java` | BE Service | Docling Serve integration |
| `generation/TesterFileWriter.java` | BE Service | StructuredTestCase → .java |
| `model/StructuredTestCase.java` | BE Model | LLM output shape |
| `model/TestCaseRecommendation.java` | BE Model | Recommend response |
| `model/InferredConfig.java` | BE Model | Full inferred config container |
| `model/InferredQuestionConfig.java` | BE Model | Per-question inferred config |
| `model/ConfigConflict.java` | BE Model | Conflict detection result |
| `data/SessionDatabase.java` | BE Infra | SQLite cache |
| `config/AppConfig.java` | BE Infra | config.properties reader/writer |
| `src/main/resources/application.properties` | Config | LangChain4j + Spring config |

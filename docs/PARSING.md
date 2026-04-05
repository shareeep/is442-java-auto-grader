# Question Parsing & Inference

This document covers two related processes:

1. **PDF Parsing** — how exam PDFs are converted to markdown and split into per-question sections
2. **Question Structure Inference** — how the system auto-detects the question layout (IDs, folders, testers, scores) from the PDF and folder structure

---

## 1. PDF Parsing — Docling Integration

### How It Works

`PdfParser.extractAllText()` sends the PDF to Docling Serve and returns structured markdown:

```java
ConvertDocumentRequest request = ConvertDocumentRequest.builder()
    .source(FileSource.builder()
        .base64String(Base64.getEncoder().encodeToString(pdfBytes))
        .filename(dynamicFilename).build())
    .options(ConvertDocumentOptions.builder()
        .toFormat(OutputFormat.MARKDOWN)
        .imageExportMode(ImageRefMode.EMBEDDED)  // base64 in markdown
        .includeImages(true)
        .doOcr(true).build())
    .target(InBodyTarget.builder().build())
    .build();
```

| Option | Effect |
|--------|--------|
| `OutputFormat.MARKDOWN` | Docling returns structured markdown with headings, code blocks, tables preserved |
| `ImageRefMode.EMBEDDED` | Images encoded as `data:image/png;base64,...` data URIs inline in markdown |
| `doOcr(true)` | Runs OCR on image-only (scanned) PDFs — text is still extracted from native PDF text |

### What Docling Returns

A markdown string that includes:
- `## Question 1` / `## Question 2a` headings (section structure)
- Code blocks with original formatting
- Tables rendered as markdown tables
- Images embedded as base64 data URIs (if `ImageRefMode.EMBEDDED` was used)

Example snippet:
```markdown
## Question 1a

Write a method `isIsogram` that...

## Question 1b

Implement `flattenNestedLists`...
```

### Where the Markdown Is Stored

The full markdown is saved to the SQLite session database:

```sql
INSERT INTO exams (id, original_filename, parsed_markdown, created_at)
VALUES (?, ?, ?, ?)
```

On subsequent requests for the same `examId`, `SessionDatabase.getParsedMarkdown()` returns the cached markdown instead of re-calling Docling. This avoids expensive re-parsing.

---

## 2. Per-Question Markdown Extraction

When generating test cases, only the relevant question section is needed — not the full PDF. `PdfParser.extractQuestionSectionFromMarkdown()` extracts one question's section at a time.

### Algorithm

```
Input:  fullMarkdown, questionId  (e.g. "Q1a")
Output: markdown string for that question only
```

**Step 1 — Normalize IDs:**
```
questionId = "Q1a"
fullId    = "1a"       (strip leading Q)
parentId  = "1"         (strip trailing letter)
hasSub   = true        (parentId != fullId)
```

**Step 2 — Try exact match (regex with word boundary):**
```
Pattern: (?i)(##\s*Question\s+1a)\b(.*?)(?=\n##\s*Question|\Z)
```

This matches `## Question 1a` followed by a word boundary (so `1a` doesn't match `1ab`), then captures everything until the next `## Question` heading or end of file. `DOTALL` mode makes `.` match newlines.

**Step 3 — Fall back to parent section (only for sub-questions):**
```
Pattern: (?i)(##\s*Question\s+1)\b(.*?)(?=\n##\s*Question|\Z)
```

If the question is `Q1a` and no `## Question 1a` heading exists, the system falls back to the parent `## Question 1` section. This handles cases where the PDF only has one `## Question 1` heading with sub-parts listed below it.

**Step 4 — If nothing found, return full markdown:**
```java
logger.warn("[PDF] Section header not found for {} — falling back to full markdown", questionId);
return fullMarkdown;
```

### Why the Word Boundary `\b` Matters

Without `\b`, the regex for `## Question 1a` would incorrectly match `## Question 1ab` at the `1` position. The word boundary ensures the letter suffix is truly a suffix — `1a\b` matches `1a` but not `1ab`.

---

## 3. Question Structure Inference — 6-Step Pipeline

`ConfigInferenceService.inferConfig()` determines the full question layout from three sources:

- **PDF markdown** — question headings and numbering
- **Template folder** — student starter code structure
- **Tester folder** — existing JUnit tester files

### Step 1 — Scan PDF for Question IDs

Regex: `(?mi)^##\s*Question\s+(\d+)(?:([a-z]))?\b`

| Match | Captured groups | Result |
|-------|----------------|--------|
| `## Question 1` | group(1)=`1`, group(2)=null | `Q1` |
| `## Question 2a` | group(1)=`2`, group(2)=`a` | `Q2a` |
| `## Question 10b` | group(1)=`10`, group(2)=`b` | `Q10b` |

Flags: `MULTILINE` (`m`) so `^` matches line starts, `CASE_INSENSITIVE` (`i`) so `Question` matches `question`.

---

### Step 2 — Match Template Folders to Questions

Recursively scans the template directory for folders named `Q\d+[a-z]?` (case-insensitive). For each matched folder:
- Finds the corresponding `InferredQuestionConfig` from Step 1 by prefix (`Q1a` matches `Q1a`, `Q1` matches `Q1`)
- Sets `folder` and `dependencyFolder` on the config
- Scans the folder for `.class` and `.txt` files → adds to `dependencyFiles`

Example: folder `Q3/Test.class`, `Q3/input.txt` → `dependencyFiles = ["Test.class", "input.txt"]`

---

### Step 2c — Discover Questions from Template Source Files (No Tester Dir)

Only runs when **no tester directory was provided**. Scans ALL files in the template (recursively) for filenames matching `Q\d+[a-z]?` (language-agnostic — any extension). Each match creates a new `InferredQuestionConfig`.

This handles the "generate from scratch" flow: if there are no testers, the question IDs come from whatever source files exist in the template.

---

### Step 2b — Smart Folder Assignment (For Questions Still Without Folders)

For any question config still missing a folder assignment, scans `allFolders` (from Step 2):

1. **Exact match wins first:** `Q1a` folder → `Q1a` question
2. **Prefix match fallback:** `Q1` folder → `Q1a` question (if no exact match)
3. **Parent inherits to sub:** If `Q1a` has a folder and `Q1` doesn't, the parent's folder is set from the sub-question's folder

---

### Step 3 — Scan Tester Directory

Scans for `*Tester.java` files in the testers directory:

| Filename | Tester class name | Inferred question ID |
|----------|------------------|---------------------|
| `Q1aTester.java` | `Q1aTester` | `Q1a` |
| `Q3Tester.java` | `Q3Tester` | `Q3` |
| `TestQ1b.java` | `TestQ1b` | (strip `Tester` → `TestQ1b` → `TestQ1b` — no match to Q ID pattern, skipped) |

For each matched tester:
- `testerClassName` = filename minus `.java`
- `questionId` = class name minus `Tester` suffix
- `maxScore` = number of `tcNum++` occurrences in the file (each increment = 1 test case)
  - Fallback: count `@Test` annotations

If a tester doesn't match any existing question from Steps 1–2b, a **new** question entry is created for it.

---

### Step 3b — Sub-Question Inheritance & Implicit Parents

**Creates implicit parents:** If `Q1a` and `Q1b` exist but `Q1` doesn't, `Q1` is created as an `implicitParent=true` config. Implicit parents are excluded from grading unless they have their own tester.

**Sub-questions inherit from parent:**
- If `Q1a.folder` is empty but `Q1.folder` exists → `Q1a.folder = Q1.folder`
- If `Q1a.dependencyFiles` is empty but `Q1.dependencyFiles` has items → `Q1a.dependencyFiles` gets a copy

---

### Step 4 — Cross-Reference & Conflict Detection

After all configs are populated, the system cross-references them and generates conflict warnings:

| Conflict type | Condition | Shown to instructor? |
|--------------|-----------|---------------------|
| `MISSING_FOLDER` | Question has no folder AND no parent with a folder AND is not an implicit parent | Yes |
| `MISSING_TESTER` | Question is from PDF but has no tester (and no sub-questions with testers) | Yes |
| `ORPHAN_TESTER` | Tester exists for a question that was never in the PDF (and has no parent in PDF) | Yes |
| `INCOMPLETE_DEPENDENCIES` | Dependency list contains `TODO_MISSING` | Yes |
| `LIKELY_FALSE_POSITIVE` | Has a folder but no PDF presence AND no tester (when tester dir was provided) | Skipped silently |

---

## 4. Conflict Types Explained

### MISSING_FOLDER

**Cause:** Template folder has no subfolder matching this question's ID.

**Instructor action:** Either create the missing folder in the template, or correct the question ID mapping in the Review step.

**Example:** PDF has `## Question 3a` but the template folder only has `Q1/`, `Q2/` — no `Q3/` folder.

---

### MISSING_TESTER

**Cause:** Question appears in the PDF but no `*Tester.java` was found for it.

**Instructor action:** Either provide a tester file, or use the AI Test Generator to create one.

**Exception:** If the question has sub-questions (e.g., `Q1a`, `Q1b`) that each have testers, the parent `Q1` is not flagged — it's assumed the sub-parts are what's being tested.

---

### ORPHAN_TESTER

**Cause:** A `*Tester.java` file exists but the PDF never mentions a matching question.

**Instructor action:** Verify whether this tester belongs to the exam or was uploaded by mistake. May indicate a question numbering mismatch.

**Example:** `Q5Tester.java` uploaded but PDF only has questions up to `Q4`.

---

### INCOMPLETE_DEPENDENCIES

**Cause:** A `.class` or `.txt` file referenced in the question folder could not be found at generation time.

**Instructor action:** Check that all required dependency files are present in the template folder.

---

## 5. Frontend File Upload Filtering

### GraderWorkspace (Auto-Grader Tab)

The grader requires strict file types. Filtering happens in the browser before upload:

| Upload field | HTML attribute | JS filter | Error shown |
|-------------|---------------|-----------|------------|
| Student Submissions | `webkitdirectory` | `files.filter(f => f.name.toLowerCase().endsWith('.zip'))` | "Wrong Folder Submitted — folder must contain ZIP files." |
| Tester Files | `webkitdirectory` | `files.filter(f => f.name.toLowerCase().endsWith('.java'))` | "Wrong Folder Submitted — expected the Tester-Files folder with .java test files." |
| Grade Mapping | `accept=".csv"` | `files.filter(f => f.name.toLowerCase().endsWith('.csv'))` | "Wrong File Submitted — expected a CSV file." |

**How the error flow works:** If the user selects a folder but the JS filter finds no matching files, the error is set and `setSubmissionFiles([])` clears the selection. The upload is never sent to the backend in this case.

---

### Wizard Upload (Test Generator Tab)

| Upload field | HTML attribute | JS filter | Note |
|-------------|---------------|-----------|------|
| Exam PDF | `accept=".pdf"` | Drag-and-drop restricted to first file only | Single file only |
| Template Folder | `webkitdirectory` | **None** | All files accepted; backend handles unknown types |
| Testers Directory | `webkitdirectory` | **None** | All files accepted; inference finds what it needs |

The template and testers directory uploads intentionally have **no file type filter** because the system needs to handle diverse folder contents (`.java`, `.class`, `.txt`, `.md`, images, etc.). The inference step in Step 3 filters by `*Tester.java` suffix when matching testers.

---

### Backend Upload Handling

The backend (`ExamController`, `GenerationController`) writes uploaded files to temp directories with their original filenames preserved. **There is no server-side file type validation.**

```java
Path tempFile = tempDir.resolve(originalName);
file.transferTo(tempFile.toFile());
```

The `UploadRegistry` (`ConcurrentHashMap<String, Path>`) maps UUIDs → temp file paths, keeping uploads alive across API calls within a session. Temp files are deleted on JVM exit via `deleteOnExit()`.

If wrong file types are uploaded, the inference step simply won't find matching folders, testers, or PDF headings — and the instructor sees the resulting conflicts in the Review step.

---

## 6. End-to-End Data Flow

```
Instructor uploads PDF
    │
    ▼
ExamController.uploadExam()
    │  Saves to temp file, assigns UUID, stores in UploadRegistry
    ▼
ExamController.parsePdf()  [background]
    │  PdfParser.extractAllText() → Docling Serve OCR → markdown
    ▼
SessionDatabase.saveParsedExam(examId, markdown)  ← cached
    │
    ▼
Instructor uploads template + testers folders
    │  Stored in UploadRegistry (UUID → temp path)
    ▼
Instructor clicks "Begin Inference"
    │
    ▼
ExamController.analyzeSetup(examId, templateId, testerId)
    │
    ├─→ ConfigInferenceService.inferConfig()
    │      Step 1: PDF regex → Q IDs
    │      Step 2: template folder scan → folder/deps assignments
    │      Step 2c: source file scan (if no testers)
    │      Step 2b: smart folder assignment
    │      Step 3: tester dir scan → tester names + maxScore
    │      Step 3b: implicit parents + sub-question inheritance
    │      Step 4: cross-reference → conflicts
    │
    ├─→ For each question:
    │      SessionDatabase.saveQuestion() → question markdown, folder, tester, score
    │
    ▼
InferredConfig returned to FE
    │
    ▼
Instructor reviews in Wizard Step 2 (Review tab)
    │  Corrects misdetected folders/testers, resolves conflicts
    ▼
GenerationController generates per question
    │
    ├─→ Per question: PdfParser.extractQuestionSectionFromMarkdown(markdown, qId)
    │      (cache hit: reads from questions table; cache miss: re-calls Docling)
    │
    ├─→ TestGenerationService builds prompt:
    │      question text + existing tester code + student code
    │      + data files (.txt) + API docs (.html from api/ folder)
    │
    ▼
AI generates test cases → TesterFileWriter assembles Java code
```

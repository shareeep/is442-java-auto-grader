# AI Test Generation

## Wizard Walkthrough

The **AI Test Generator** tab walks instructors through a 4-step wizard. Each step is described from a user perspective below.

### Step 1 — Upload

Upload three things:
- **Exam PDF** — the question paper (required)
- **Template folder** — the student starter code (required for AI context; optional if generating from scratch)
- **Tester-Files folder** — existing JUnit testers if you have them (optional; can be empty)

After uploading, the PDF is sent to Docling Serve for parsing. This runs in the background — you can proceed to Step 2 while it processes.

### Step 2 — Review

The system analyses the PDF and folder structure, then presents an inferred question configuration:

| Field | Source | Editable |
|-------|--------|----------|
| Question ID | Folder names + PDF headings | Yes |
| Folder name | Folder structure | Yes |
| Tester class name | Existing testers folder | Yes |
| Max score | PDF text or default | Yes |
| Inferred from PDF | Yes/No badge | — |

Review each question and correct any misdetections. This config is passed to the generation step — accurate configs produce better test cases.

### Step 3 — Generate

For each question:
1. **Recommendation** — the AI analyses the question text + existing testers and recommends up to 5 test concepts not yet covered
2. **Generation** — based on those recommendations, test cases are generated (up to 3 retries on failure)
3. **Review** — inspect the generated test cases, add custom instructions if needed

You can:
- Accept the recommended count or override with a custom number (max 5)
- Add custom suggestions per question (e.g., "include a boundary case with an empty list")

### Step 4 — Export

Review the final testers and export as a ZIP. Upload this ZIP in the **Auto-Grader** tab when running grading.

---

## How It Works — Developer View

### System Prompts

`LangChainService.java` defines two AI methods with detailed system prompts:

#### `generateTestCasesJson` — Test Case Generation

```java
@SystemMessage("""
You are a Java test case generator for an automated grading system.

CRITICAL — 'description' field naming (violations will break the system):
- Each description MUST be a specific, meaningful name that identifies exactly what is being tested.
- ABSOLUTELY FORBIDDEN: "Generated Test Case 1", "Test Case 2", "Test 1", "Generated Test N", ...

Generate test cases as a JSON array. Each element MUST be a JSON object with EXACTLY these fields:
  "description"    — short meaningful name for the test case
  "conceptCovered" — what concept this tests
  "setup"          — VALID JAVA CODE ONLY (no English prose, ends with ';')
  "methodCall"     — single method invocation WITHOUT semicolon
  "expected"       — human-readable expected output (for log printing ONLY)
  "assertion"      — SELF-CONTAINED boolean Java expression using only 'result' variable
  "weight"         — numeric weight (default 1.0)
  "expectsException" — true if test should throw an exception
  "exceptionType"  — exception class name if expectsException is true

Rules:
- STYLE MATCHING: replicate existing tester method invocation style
- Use ONLY method names/class names from provided existing tester or student code
- For floating-point: use Math.abs(result - expected) < 0.001
- Return ONLY a valid JSON array — no markdown, no explanation
""")
String generateTestCasesJson(@UserMessage UserMessage userMessage);
```

**Key constraint:** `assertion` must use only the variable `result` — the AI must inline the expected value directly. It cannot reference a variable named `expected` (it doesn't exist in the generated code).

#### `recommendJson` — Test Case Recommendations

```java
@SystemMessage("""
You are an expert Java test case analyst.
Analyse the given exam question and existing test cases, then identify ALL new test cases needed.
Respond with a JSON object:
{
  "questionId": "...",
  "existingConcepts": ["Normal: valid input", "Boundary: single element"],
  "recommendedCount": <number equal to conceptsToCover.length>,
  "conceptsToCover": ["Boundary: empty list", "Exception: null input", ...],
  "rationale": "Brief explanation"
}
IMPORTANT:
- Analyze ONE SPECIFIC sub-question only
- Return at most 5 concepts
- Do NOT repeat concepts already covered by existing tests
- Consider: normal paths, boundary conditions, exception paths, null inputs
Return ONLY valid JSON, no markdown.
""")
String recommendJson(@UserMessage UserMessage userMessage);
```

---

### Retry Mechanism

`TestGenerationService` implements retry with linear backoff:

```java
private static final int MAX_AI_ATTEMPTS = 3;

for (int attempt = 1; attempt <= MAX_AI_ATTEMPTS; attempt++) {
    String rawJson = svc.generateTestCasesJson(message);
    if (rawJson == null || rawJson.isBlank()) { /* retry */ }
    try {
        structured = parseStructuredCases(rawJson);
    } catch (Exception e) { /* retry */ }
    if (structured.isEmpty()) { /* retry */ }
    // success
    Thread.sleep(3000L * attempt); // 3s, 6s, 9s
}
```

Guards that trigger retry:
- Null or blank response from AI
- JSON parse failure (malformed output, wrong schema)
- Empty test case list returned
- LLM API exception

After 3 failures → returns `GenerationResult` with `success=false`.

---

### What Gets Passed to the AI

For each question, `TestGenerationService.buildGeneratePrompt()` assembles:

1. **Question ID + tester class name** — from `QuestionConfig`
2. **Exam question context** — markdown extracted from PDF (question section only, via `PdfParser.extractQuestionSectionFromMarkdown()`)
3. **Existing tester code** — if provided; AI is instructed to replicate the same method names and invocation style
4. **Student code from template folder** — so the AI knows what method signatures exist
5. **Data file contents** — extracted from `.txt` files in the question folder (for file-based tests)
6. **API documentation** — HTML docs from `api/` folder in template, filtered to classes used in the question
7. **Key concepts to cover** — from the recommendation step
8. **Custom suggestions** — instructor's free-text instructions from the wizard

---

### Docling Integration

`PdfParser` sends PDFs to Docling Serve (configured via `DOCLING_SERVE_URL`):

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

The markdown response may contain base64-encoded images. `PdfParser.extractBase64Images()` pulls these out for the vision LLM. `PdfParser.stripInlineImages()` replaces them with `[diagram N]` placeholders for the text LLM.

---

### Config Inference Logic

`ConfigInferenceService.inferConfig()` determines the question structure:

1. **From folder names** — scans the testers folder for `Q1a.java`, `Q2b.java`, etc. → maps to question IDs
2. **From PDF headings** — regex matches `## Question 1a`, `## Question 2b` in the markdown
3. **Cross-reference** — tester class names in the testers folder are matched to questions
4. **Max scores** — extracted from PDF text near question headings (fallback: default of 10 per question)

The result is an `InferredConfig` with a list of `InferredQuestionConfig` objects, each holding `questionId`, `folder`, `testerClassName`, `maxScore`, and `inferredFromPdf` flag.

---

### Max 5 Test Cases Per Question

Enforced at two levels:
1. **Recommendation prompt** — instructs AI to return "at most 5 concepts"
2. **Generation prompt** — requests exactly `N` test cases where `N` = recommended count (capped at 5)

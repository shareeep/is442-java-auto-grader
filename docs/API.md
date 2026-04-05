# REST API Reference

All endpoints are under `/api`. CORS is configured for `http://localhost:5173` (frontend dev server).

Swagger UI is available at `http://localhost:8080/swagger-ui` when the backend is running.

---

## Exams

Exam PDF upload, parsing, and analysis. Managed by `ExamController`.

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/exams/upload` | Upload an exam PDF |
| `POST` | `/api/exams/{examId}/parse` | Trigger Docling PDF parsing (cache-first) |
| `POST` | `/api/exams/{examId}/analyze` | Analyse PDF + folders to infer question config |

### `POST /api/exams/upload`

**Request:** `multipart/form-data` with a `file` field (PDF)

**Response:**
```json
{ "examId": "uuid-string", "fileName": "midterms.pdf" }
```

### `POST /api/exams/{examId}/parse`

Sends the PDF to Docling Serve for OCR + markdown extraction. Result is cached in SQLite — subsequent calls return immediately.

### `POST /api/exams/{examId}/analyze`

**Request body:**
```json
{
  "templateId": "uuid",
  "testerId": "uuid"
}
```

**Response:** `InferredConfig` — the auto-detected question structure:
```json
{
  "assessmentName": "IS442 Midterms",
  "templateFolder": "RenameToYourUsername",
  "questions": [
    {
      "questionId": "Q1a",
      "folder": "Q1a",
      "tester": "Q1aTester",
      "maxScore": 5,
      "inferredFromPdf": true,
      "implicitParent": false
    }
  ],
  "conflicts": []
}
```

---

## Templates

Template (starter code) directory upload. Managed by `TemplateController`.

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/templates/upload` | Upload template directory |

**Request:** `multipart/form-data` with `files` field. Directory paths are encoded with `__SEP__` replacing `/` so the server can reconstruct the tree.

**Response:**
```json
{ "templateId": "uuid-string", "fileCount": 12 }
```

---

## Testers

Tester file upload, retrieval, and saving. Managed by `TesterController`.

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/testers/upload` | Upload tester `.java` files |
| `GET` | `/api/testers/{className}?testerId=...` | Get a tester file's source code |
| `POST` | `/api/testers/save` | Save generated testers to disk |

### `GET /api/testers/{className}`

**Query params:** `testerId` (required) — the upload session ID

**Response:**
```json
{ "code": "public class Q1aTester { ... }", "testCaseCount": 3 }
```

### `POST /api/testers/save`

**Request body:**
```json
{
  "examId": "uuid",
  "testerId": "uuid",
  "outputDir": "./Tester-Files",
  "results": [
    {
      "questionId": "Q1a",
      "testerClassName": "Q1aTester",
      "generatedCode": "public class Q1aTester { ... }",
      "cases": [
        { "description": "Empty list input", "inputArgs": "...", "expectedOutput": "...", "weight": 1.0 }
      ]
    }
  ],
  "updateMaxScores": true
}
```

**Response:**
```json
{
  "savedPaths": ["./Tester-Files/Q1aTester.java"],
  "errors": [],
  "fileContents": { "Q1aTester": "public class Q1aTester { ... }" }
}
```

---

## Generation

AI-powered test case generation. Managed by `GenerationController`.

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/generation/tests` | Generate test cases for a question |
| `POST` | `/api/generation/recommendations` | Get AI recommendations for a question |
| `POST` | `/api/generation/refinements` | Refine existing generated code |

### `POST /api/generation/recommendations`

**Request body:**
```json
{
  "examId": "uuid",
  "questionId": "Q1a",
  "testerId": "uuid"
}
```

**Response:**
```json
{
  "questionId": "Q1a",
  "existingConcepts": ["Normal: valid input", "Boundary: single element"],
  "recommendedCount": 3,
  "conceptsToCover": ["Boundary: empty list", "Exception: null input", "Edge: maximum size"],
  "existingCount": 2,
  "rationale": "Existing tests cover basic cases but miss boundary and exception paths."
}
```

### `POST /api/generation/tests`

**Request body:**
```json
{
  "examId": "uuid",
  "testerId": "uuid",
  "templateId": "uuid",
  "numCases": 3,
  "question": {
    "questionId": "Q1a",
    "folder": "Q1a",
    "tester": "Q1aTester",
    "maxScore": 5
  },
  "conceptsToCover": ["Boundary: empty list", "Exception: null input"],
  "customSuggestions": ["Add a test with a large input"]
}
```

**Response:**
```json
{
  "questionId": "Q1a",
  "cases": [
    { "description": "Empty list input", "inputArgs": "...", "expectedOutput": "...", "weight": 1.0 }
  ],
  "generatedCode": "public class Q1aTester { ... }",
  "compiledOk": true
}
```

### `POST /api/generation/refinements`

**Request body:**
```json
{
  "examId": "uuid",
  "questionId": "Q1a",
  "currentCode": "public class Q1aTester { ... }",
  "refinementPrompt": "Change the boundary test to use Integer.MAX_VALUE"
}
```

**Response:**
```json
{ "questionId": "Q1a", "refinedCode": "public class Q1aTester { ... }" }
```

---

## Grading

Live grading via Server-Sent Events (SSE). Managed by `GradingStreamController`.

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/grading/stream` | Start grading and stream progress via SSE |
| `POST` | `/api/grading/{sessionId}/stop` | Cancel a running grading session |

### `POST /api/grading/stream`

**Request:** `multipart/form-data`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `submissions` | `File[]` | Yes | Student submission ZIP files |
| `testers` | `File[]` | Yes | Tester `.java` files |
| `scoresheet` | `File` | No | LMS CSV scoresheet for name/ID enrichment |

**Response:** `text/event-stream` with the following events:

```
event: session
data: {"sessionId": "uuid"}

event: status
data: {"message": "Extracting submissions...", "phase": "extract"}

event: student
data: {
  "username": "alice.chan.2023",
  "displayName": "Alice Chan",
  "totalScore": 12,
  "maxPossibleScore": 18,
  "results": [{"questionId": "Q1a", "score": 5, "maxScore": 5}],
  "anomalies": []
}

event: complete
data: {"message": "Grading complete", "totalStudents": 18, "runId": "20241101-143022"}

event: error
data: {"message": "Failed to extract alice.chan.2023.zip"}
```

### `POST /api/grading/{sessionId}/stop`

Cancels an in-progress grading run. Partial results are preserved with `"status": "cancelled"` in `run.json`.

---

## Reports

Browse and download past grading run artifacts. Managed by `ReportsController`.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/reports/list` | List all past runs |
| `GET` | `/api/reports/{id}/pdf` | Instructor PDF report (inline) |
| `GET` | `/api/reports/{id}/pdf/{filename}` | Same as above (filename for browser display) |
| `GET` | `/api/reports/{id}/csv` | Graded score sheet CSV (attachment) |
| `GET` | `/api/reports/{id}/plagiarism` | JPlag report zip (attachment) |
| `GET` | `/api/reports/{id}/results` | `results.json` — structured grading data |
| `GET` | `/api/reports/{id}/download` | Entire run directory as `.zip` |
| `GET` | `/api/reports/{id}/code/{username}` | Student's extracted `.java` source files |
| `GET` | `/api/reports/{id}/testers` | Tester files used in this run |

### `GET /api/reports/list`

```json
[
  {
    "id": "20241101-143022",
    "timestamp": "20241101-143022",
    "hasPdf": true,
    "pdfFilename": "is442-midterms-results-01-11_22-30.pdf",
    "hasCsv": true,
    "hasPlagiarism": true,
    "studentCount": 18
  }
]
```

Cancelled runs (where `run.json` has `"status": "cancelled"`) are excluded from the list.

---

## System

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/system/boot-id` | Returns a UUID that changes on each backend restart |

**Response:**
```json
{ "bootId": "uuid-string" }
```

Used by the frontend to detect backend restarts and invalidate stale session data (upload IDs, wizard state).

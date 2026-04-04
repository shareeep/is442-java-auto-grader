# REST API Reference

All endpoints are under `/api`. CORS is configured for `http://localhost:5173` (frontend).

---

## Uploads

Manage file uploads for grading sessions.

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/uploads/submissions` | Upload student submission ZIP(s) as a directory |
| `POST` | `/api/uploads/testers` | Upload tester files directory |
| `POST` | `/api/uploads/template` | Upload template/starter code directory |

**Request:** `multipart/form-data` with a `file` field (directory or single file)

**Response:**
```json
{ "uploadId": "uuid-string", "fileName": "original-name", "fileCount": 18 }
```

---

## Exams

Exam PDF management and analysis.

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/exams/upload` | Upload an exam PDF |
| `POST` | `/api/exams/{examId}/parse` | Trigger Docling parsing (cache-first) |
| `POST` | `/api/exams/{examId}/analyze` | Analyse PDF + folders → infer question config |

### `POST /api/exams/upload`

**Request:** `multipart/form-data`
```json
{ "examId": "uuid", "fileName": "midterms.pdf" }
```

### `POST /api/exams/{examId}/analyze`

**Request body:**
```json
{
  "templateId": "uuid",
  "testerId": "uuid"
}
```

**Response:** `InferredConfig`
```json
{
  "questions": [
    {
      "questionId": "Q1a",
      "folder": "Q1a",
      "testerClassName": "Q1aTest",
      "maxScore": 5,
      "inferredFromPdf": true
    }
  ]
}
```

---

## Grading

### Streaming (SSE)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/grading/start` | Start a grading run, returns a `runId` |
| `GET` | `/api/grading/status/{runId}` | Poll run status |
| `POST` | `/api/grading/stream` | SSE stream — live grading events |

### `POST /api/grading/stream`

**Request:** `multipart/form-data`
```
submissions: File[] (ZIP files)
testers: File[] (.java tester files)
scoresheet: File (optional CSV)
```

**Response:** `text/event-stream` with events:

```json
// StudentStartedEvent
event: StudentStarted
data: {"username": "alice.chan.2023"}

// StudentGradedEvent
event: StudentGraded
data: {
  "username": "alice.chan.2023",
  "totalScore": 12,
  "maxPossibleScore": 18,
  "results": [{"questionId": "Q1a", "score": 5, "maxScore": 5}],
  "anomalies": []
}

// PipelineProgressEvent
event: PipelineProgress
data: {"processed": 5, "total": 18}

// PipelineCompleteEvent
event: PipelineComplete
data: {"runId": "20241101-143022", "studentCount": 18}
```

### `GET /api/grading/status/{runId}`

Returns current run status (from `run.json`).

---

## Test Generation

Multi-step AI generation wizard.

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/generation/setup` | Initialize generation session |
| `POST` | `/api/generation/recommend/{questionId}` | Get AI recommendations for a question |
| `POST` | `/api/generation/generate/{questionId}` | Generate test cases for a question |
| `POST` | `/api/generation/refine` | Refine existing generated code |

### `POST /api/generation/generate/{questionId}`

**Request body:**
```json
{
  "examId": "uuid",
  "questionId": "Q1a",
  "numCases": 3,
  "conceptsToCover": ["Boundary: empty list", "Exception: null input"],
  "customSuggestions": ["Add a test with a large input"]
}
```

**Response:** `GenerationResult`
```json
{
  "questionId": "Q1a",
  "testCases": [
    {
      "description": "Empty list input",
      "inputArgs": "ArrayList<String> input = new ArrayList<>();",
      "expectedOutput": "empty list",
      "weight": 1.0
    }
  ],
  "success": true,
  "generatedCode": "public class Q1aTest { ... }"
}
```

---

## Reports

Browse and download past grading run artifacts.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/reports/list` | List all past runs |
| `GET` | `/api/reports/{runId}/pdf` | Download instructor PDF report |
| `GET` | `/api/reports/{runId}/csv` | Download graded score sheet CSV |
| `GET` | `/api/reports/{runId}/plagiarism` | Download JPlag report zip |
| `GET` | `/api/reports/{runId}/results` | Download `results.json` |
| `GET` | `/api/reports/{runId}/download` | Download entire run as `.zip` |
| `GET` | `/api/reports/{runId}/code/{username}` | Get student's extracted source files |
| `GET` | `/api/reports/{runId}/testers` | Get tester files from that run |

### `GET /api/reports/list`

```json
[
  {
    "id": "20241101-143022",
    "timestamp": "20241101-143022",
    "hasPdf": true,
    "hasCsv": true,
    "hasPlagiarism": true,
    "studentCount": 18
  }
]
```

---

## Plagiarism Viewer

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/plagiarism/{runId}/viewer` | Redirect to plagiarism viewer for a run |

Accesses the JPlag report for a specific run in the plagiarism viewer at `http://localhost:1996` (Docker maps to container port 80).

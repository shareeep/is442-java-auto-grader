# Test Generation Frontend — Architecture & Integration Guide

## Architecture Overview

The test generation web UI is a React + Spring Boot layer on top of the existing
CLI-based test generation module. Spring Boot acts as a **thin REST wrapper** — it
delegates all work to the existing `TestGenerationService`, `TesterFileWriter`, and
`AppConfig` classes without modifying them.

```
┌─────────────────────┐       ┌─────────────────────────┐
│   React Frontend    │──────▶│   Spring Boot REST API  │
│   (Vite + TS)       │ /api  │   (thin controllers)    │
│   port 5173 (dev)   │       │   port 8080             │
└─────────────────────┘       └──────────┬──────────────┘
                                         │ delegates to
                              ┌──────────▼──────────────┐
                              │  Existing Java Services  │
                              │  TestGenerationService   │
                              │  TesterFileWriter        │
                              │  AppConfig               │
                              └──────────────────────────┘
```

**Dual mode**: The `--web` CLI flag launches Spring Boot; no args = existing console UI.

---

## REST API Reference

All endpoints are under `/api/generation/`.

### GET `/api/generation/config/questions`

Returns the question list from `config.properties`.

**Response:**
```json
[
  {
    "questionId": "Q1a",
    "folder": "Q1",
    "testerClassName": "Q1aTester",
    "maxScore": 3.0,
    "dependencyFolder": null,
    "dependencyFiles": []
  }
]
```

### GET `/api/generation/config/ai`

Returns AI generation settings.

**Response:**
```json
{
  "model": "minimax/minimax-m2.5",
  "maxTokens": 4096,
  "defaultCasesPerQuestion": 3
}
```

### POST `/api/generation/exam/upload`

Multipart PDF upload. Saves to a temp directory and returns a UUID-based exam ID.

**Request:** `multipart/form-data` with field `file` (PDF)

**Response:**
```json
{
  "examId": "550e8400-e29b-41d4-a716-446655440000",
  "fileName": "IS442-Exam.pdf"
}
```

### POST `/api/generation/generate`

Triggers test case generation. Synchronous — blocks 30-60s per question.

**Request:**
```json
{
  "examId": "550e8400-...",
  "testersDir": "is442-project-materials/Tester-Files",
  "questions": [
    { "questionId": "Q1a", "numCases": 3 },
    { "questionId": "Q1b", "numCases": 3 }
  ]
}
```

**Response:**
```json
[
  {
    "questionId": "Q1a",
    "cases": [
      { "description": "Generated test 1", "inputArgs": "", "expectedOutput": "", "weight": 1.0 }
    ],
    "compiledOk": true,
    "compileErrors": "",
    "generatedCode": "// try { ... } catch ..."
  }
]
```

### GET `/api/generation/tester/{className}?dir=<path>`

Reads an existing tester file and returns its content.

**Response:**
```json
{
  "code": "public class Q1aTester { ... }",
  "testCaseCount": 3
}
```

### POST `/api/generation/save`

Saves generated tester files with professor-assigned weights.

**Request:**
```json
{
  "examId": "550e8400-...",
  "testersDir": "is442-project-materials/Tester-Files",
  "outputDir": "generated-testers",
  "results": [
    {
      "questionId": "Q1a",
      "testerClassName": "Q1aTester",
      "generatedCode": "...",
      "cases": [
        { "description": "test 1", "inputArgs": "", "expectedOutput": "", "weight": 1.5 }
      ]
    }
  ],
  "updateMaxScores": true
}
```

**Response:**
```json
{
  "savedPaths": ["generated-testers/Q1aTester_generated.java"],
  "errors": []
}
```

---

## React Component API

### `<TestGenerationWizard />`

The main component wrapping the entire 7-step wizard.

**Props:**
| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `apiBaseUrl` | `string` | `""` | Base URL for API calls. Empty = same origin. |
| `onComplete` | `(savedPaths: string[]) => void` | — | Called after successful save |

**Usage:**
```tsx
import { TestGenerationWizard } from "./components/TestGenerationWizard";

function App() {
  return <TestGenerationWizard onComplete={(paths) => console.log("Saved:", paths)} />;
}
```

**Exported types** from `frontend/src/types.ts`:
- `QuestionConfig`, `AiSettings`, `GeneratedTestCase`, `GenerationResult`
- `QuestionSelection`, `GenerateRequest`, `SaveRequest`, `SaveResponse`
- `TestGenerationWizardProps`

---

## Integration Guide

To embed the wizard into another React app:

1. **Copy these files:**
   - `frontend/src/components/TestGenerationWizard/` (wizard + barrel export)
   - `frontend/src/components/StepWizard.tsx` (step navigation)
   - `frontend/src/components/steps/` (all step components)
   - `frontend/src/api/client.ts` (API client)
   - `frontend/src/types.ts` (TypeScript interfaces)

2. **Import the component:**
   ```tsx
   import { TestGenerationWizard } from "./components/TestGenerationWizard";
   ```

3. **Spring Boot controllers**: Copy `com.is442.autograder.web` package into your
   Spring Boot app, or import `WebConfig.java` as a configuration class.

4. **No global dependencies**: The wizard uses no React Router, Redux, or global
   Context. Styling is inline (no CSS conflicts).

---

## Development Setup

### Prerequisites
- Java 17+
- Node.js 18+
- npm

### Running locally

**Terminal 1 — Spring Boot backend:**
```bash
./gradlew bootRun
```

**Terminal 2 — Vite dev server (with hot reload):**
```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173` — Vite proxies `/api` requests to Spring Boot on port 8080.

### Production build
```bash
cd frontend && npm install && npm run build && cd ..
./gradlew bootJar
java -jar build/libs/autograder-1.0-SNAPSHOT-web.jar --web
```

Open `http://localhost:8080`.

### Console mode (unchanged)
```bash
./gradlew fatJar
java -jar build/libs/autograder-1.0-SNAPSHOT-all.jar
```

---

## File Map

### Backend (`src/main/java/com/is442/autograder/web/`)
| File | Purpose |
|------|---------|
| `WebApplication.java` | Spring Boot entry point |
| `WebConfig.java` | Registers existing services as beans |
| `ConfigController.java` | GET endpoints for config |
| `ExamController.java` | PDF upload |
| `GenerationController.java` | Generate + save endpoints |
| `SpaForwardController.java` | SPA routing |
| `dto/*.java` | Request/response DTOs |

### Frontend (`frontend/src/`)
| File | Purpose |
|------|---------|
| `App.tsx` | Root component |
| `types.ts` | TypeScript interfaces |
| `api/client.ts` | API client functions |
| `components/TestGenerationWizard/` | Barrel export for wizard |
| `components/StepWizard.tsx` | Step navigation |
| `components/steps/Step1-7*.tsx` | Individual wizard steps |

# IS442 Auto-Grader — Frontend

React + TypeScript SPA served by the Spring Boot backend in production and by Vite dev server locally.

## Tech Stack

| Layer | Library | Version |
|---|---|---|
| Framework | React | 19 |
| Language | TypeScript | 6 |
| Build tool | Vite | 8 |
| Routing | React Router | 7 |
| Server state | TanStack Query | 5 |
| Client state | Zustand (+ Immer) | 5 |
| Styling | Tailwind CSS | 3 |
| UI primitives | shadcn/ui (Radix UI) | — |
| Icons | Lucide React | — |
| Syntax highlight | Shiki, react-syntax-highlighter | — |
| Animation | Motion | — |
| API client | @hey-api/openapi-ts (generated SDK) | 0.94 |

## Project Structure

```
src/
├── api/
│   └── uploadTemplate.ts       # Manual fetch for template upload (__SEP__ encoding)
├── components/
│   ├── layout/                 # Layout, Topbar
│   ├── auto-grader/            # GradingTerminal, ResultsTable, SubmissionDetails, FileUploadCard, FileTreeView
│   ├── test-generator/         # Wizard, ProjectSetup, InferenceReview, GenerationHub, FinalizeExport
│   └── ui/                     # shadcn/ui primitives
├── generated/                  # Auto-generated from OpenAPI spec — do not edit manually
│   ├── sdk.gen.ts              # Typed fetch functions for every endpoint
│   ├── @tanstack/react-query.gen.ts  # Generated TanStack Query options/mutations
│   └── types.gen.ts            # Request/response types
├── pages/
│   ├── TestGenerator.tsx       # AI test case generator (multi-step wizard)
│   ├── GraderWorkspace.tsx     # Auto-grader — upload & run submissions
│   ├── PastRuns.tsx            # Browse past grading runs
│   └── RunResults.tsx          # Per-run results with code viewer and scoring
├── store/
│   ├── wizardStore.ts          # Zustand store for test-generator wizard state
│   └── graderStore.ts          # Zustand store for grader workspace state
└── types.ts                    # Shared FE-only types
```

## API Client

The SDK in `src/generated/` is generated from the Spring Boot OpenAPI spec. To regenerate after backend changes:

```bash
# Backend must be running
npm run generate:api
```

Most endpoints are called via the generated SDK directly. The one exception is `uploadTemplate` in `src/api/uploadTemplate.ts`, which encodes relative folder paths using a `__SEP__` separator before sending as multipart — logic that cannot be expressed in a generated SDK.

## Development

```bash
npm install
npm run dev       # Vite dev server (proxies /api to localhost:8080)
npm run build     # Type-check + production build
npm run lint      # ESLint
```

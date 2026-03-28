---
name: frontend-review
description: Review checklist and patterns for the IS442 Java Auto-Grader frontend. Use this skill whenever you add, change, or review frontend code in this project — including new components, state changes, API integration, styling, or bug fixes. Also use it when asked "is this correct?", "does this look right?", or "check my frontend code" in the context of this project.
---

# IS442 Auto-Grader Frontend — Review Guide

This skill captures the tech stack, conventions, and **gotchas** specific to this project's frontend so AI-generated code can be validated before shipping.

---

## Tech Stack Quick Reference

| Layer | Library | Version |
|---|---|---|
| Framework | React | 19 |
| Language | TypeScript (strict) | 6 |
| Build | Vite | 8 |
| Routing | React Router | 7 |
| Server state | TanStack Query | v5 |
| Client state | Zustand + Immer | 5 |
| Styling | Tailwind CSS + shadcn/ui | 3 |
| API client | Auto-generated SDK via `@hey-api/openapi-ts` | — |
| Icons | Lucide React | — |

---

## After Making Any Frontend Change

Run this before declaring done:

```bash
cd frontend && npm run build
```

A passing build is required — TypeScript errors and unused imports that slip past the editor will surface here. Do not skip this step.

---

## Key Gotchas to Check

### 1. Zustand Stores — Persist Version

Both stores use `persist` middleware with a `version` number:

```typescript
// src/store/wizardStore.ts
// src/store/graderStore.ts
```

**If you change the shape of persisted state**, bump `version` so stale localStorage is discarded on next load. Forgetting this causes silent bugs where old cached state is deserialized into the new shape.

Also check `partialize` — it controls which fields are persisted. New fields are NOT automatically persisted unless added there.

### 2. Zustand Selectors — Avoid Object Literals in Selectors

```typescript
// ❌ Creates a new object every render → infinite re-render loop
const { phase, setPhase } = useGraderStore((s) => ({ phase: s.phase, setPhase: s.setPhase }));

// ✅ Array selector — stable reference
const [phase, setPhase] = useGraderStore((s) => [s.phase, s.setPhase]);

// ✅ useShallow for object shape
import { useShallow } from 'zustand/react/shallow';
const { phase, setPhase } = useGraderStore(useShallow((s) => ({ phase: s.phase, setPhase: s.setPhase })));
```

### 3. File Uploads — The `__SEP__` Encoding

When uploading directories, the browser's `webkitRelativePath` contains slashes (e.g., `"RenameToYourUsername/Q1/Solution.java"`). Multipart form filenames strip slashes, so we encode them:

```typescript
// src/api/uploadTemplate.ts
const encodedName = file.webkitRelativePath.replace(/\//g, "__SEP__");
form.append("files", file, encodedName);
```

**Do not change this encoding without updating the backend** — the Spring controller splits on `__SEP__` to reconstruct the directory tree.

Never use the generated SDK (`uploadTemplateMutation`) for template uploads — use the manual wrapper at `src/api/uploadTemplate.ts`.

### 4. SSE Streaming — Vite Proxy Config

The `/api/grade/stream` endpoint uses Server-Sent Events. The Vite proxy in `vite.config.ts` sets `x-accel-buffering: no` to prevent response buffering. If you add a new streaming endpoint, it needs its own proxy entry with the same headers — the generic `/api` proxy will buffer it.

Also: the SSE parser in `GradingTerminal.tsx` handles `\r\n` line endings. If you refactor the parser, test against both `\n` and `\r\n`.

### 5. TanStack Query — Use Generated Hooks First

The file `src/generated/@tanstack/react-query.gen.ts` has auto-generated `queryOptions` and mutation configs for every endpoint. Prefer these over writing raw `useQuery` calls:

```typescript
// ✅ Use generated options
const { data } = useQuery(listRunsOptions());
const mutation = useMutation(runGradingMutation());

// ❌ Don't hand-roll query keys and fns for existing endpoints
const { data } = useQuery({ queryKey: ['runs'], queryFn: () => fetch('/api/runs') });
```

When the OpenAPI spec changes, regenerate with `npm run openapi-ts` — don't manually edit files in `src/generated/`.

### 6. Routing — Navigation Blocking During Grading

`GraderWorkspace.tsx` uses `useBlocker` from React Router to prevent navigation while `phase === 'grading'`. If you add a new phase or change phase names, check that the blocker condition is still correct.

### 7. Path Aliases

Use `@/` for all internal imports. Do not use relative `../../` paths more than one level deep:

```typescript
// ✅
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';

// ❌
import { cn } from '../../../lib/utils';
```

### 8. `cn()` for Class Names

Always merge Tailwind classes with `cn()` from `@/lib/utils`. Never string-concatenate class names — it breaks Tailwind's merge logic and produces duplicate/conflicting utilities.

```typescript
// ✅
<div className={cn('border rounded', isActive && 'border-primary bg-primary/10')} />

// ❌
<div className={`border rounded ${isActive ? 'border-primary bg-primary/10' : ''}`} />
```

---

## Design System

### Colors

Use **semantic tokens**, not raw hex or hardcoded Tailwind colors:

| Use case | Token |
|---|---|
| Page background | `bg-background` |
| Cards/panels | `bg-card` or `bg-secondary` |
| Subtle accent fills | `bg-accent/10`, `bg-accent/5` |
| Borders | `border-border` |
| Destructive/error | `text-destructive`, `border-destructive/30`, `bg-destructive/10` |
| Primary interactive | `bg-primary`, `text-primary-foreground` |

VS Code palette shortcuts are available for terminal/code-like UI: `text-vsc-blue`, `bg-vsc-green/10`, `text-vsc-orange`, etc. Use these in the grading terminal and code display areas.

### Typography

- Body: default (`font-sans` = Plus Jakarta Sans)
- Headings: `font-outfit`
- Code/monospace: `font-mono` (JetBrains Mono)

### Borders & Radius

Default radius is `rounded-xl` (10px) for cards. Use `rounded-lg` for smaller elements, `rounded-full` for badges/pills. Avoid sharp `rounded-none` unless it's intentional (e.g., inside a panel flush with another element).

### Spacing & Layout

The app uses a fixed-height layout — `h-screen` at root, `flex-1 h-0 overflow-y-auto` for the main content area. Components that need scrollable content must set their own height constraints or they'll cause layout breaks.

---

## Component Patterns

### Page Components (`src/pages/`)

- Own the Zustand store slice and major state
- Use TanStack Query for server data
- Handle error states explicitly — don't silently swallow errors
- Use `useBlocker` if destructive navigation is possible

### Feature Components (`src/components/{feature}/`)

- Receive data and callbacks via props
- Local `useState` for pure UI state (hover, open/close, selected row)
- Props interface named `{ComponentName}Props`

### UI Primitives (`src/components/ui/`)

- These are shadcn/ui components — do not modify them directly
- Compose them, don't rewrite them

### Memoization

Use `useMemo` when computing derived data from arrays (sorting, filtering, aggregating). Use `useRef` to hold mutable values inside event handlers or streaming loops to avoid stale closures.

---

## Error Handling Pattern

```tsx
const [error, setError] = useState<string | null>(null);

try {
  await doSomething();
  setError(null);
} catch (err: any) {
  setError(err.message ?? 'Something went wrong');
}

// Display
{error && (
  <div className="px-4 py-3 rounded-xl border border-destructive/30 bg-destructive/10 text-destructive text-sm flex items-center gap-2">
    <AlertCircle size={16} className="shrink-0" />
    {error}
  </div>
)}
```

---

## Naming Conventions

| Thing | Convention | Example |
|---|---|---|
| Components | PascalCase | `GradingTerminal` |
| Props interfaces | `{Name}Props` | `FileUploadProps` |
| Hooks | `use` prefix | `useGraderStore` |
| Constants | UPPER_SNAKE | `STEPS` |
| Utility functions | camelCase | `buildFileTree` |
| Files | Match component name | `GradingTerminal.tsx` |

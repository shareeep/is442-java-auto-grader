---
description: Review or implement Zustand 5.x state management patterns. Covers stores, selectors, useShallow, middleware (immer/devtools/persist), slices, and anti-patterns.
allowed-tools: Read, Grep, Glob, Write
disable-model-invocation: false
---

# Zustand Patterns

Modern state management with Zustand 5.x - lightweight, TypeScript-first, no boilerplate.

## Overview

- Global state without Redux complexity
- Shared state across components without prop drilling
- Persisted state with localStorage/sessionStorage
- Computed/derived state with selectors
- State that needs middleware (logging, devtools, persistence)

## Quick Reference

```typescript
// ✅ Create typed store with double-call pattern
const useStore = create<State>()((set, get) => ({ ... }));

// ✅ Use selectors for all state access
const count = useStore((s) => s.count);

// ✅ Use useShallow for multiple values (Zustand 5.x)
const { a, b } = useStore(useShallow((s) => ({ a: s.a, b: s.b })));

// ✅ Middleware order: immer → subscribeWithSelector → devtools → persist
create(persist(devtools(immer((set) => ({ ... })))))

// ❌ Never destructure entire store
const store = useStore(); // Re-renders on ANY change

// ❌ Never store server state (use TanStack Query instead)
const useStore = create((set) => ({ users: [], fetchUsers: async () => ... }));
```

## Key Decisions

| Decision | Option A | Option B | Recommendation |
|----------|----------|----------|----------------|
| State structure | Single store | Multiple stores | **Slices in single store** - easier cross-slice access |
| Nested updates | Spread operator | Immer middleware | **Immer** for deeply nested state (3+ levels) |
| Persistence | Manual localStorage | persist middleware | **persist middleware** with partialize |
| Multiple values | Multiple selectors | useShallow | **useShallow** for 2-5 related values |
| Server state | Zustand | TanStack Query | **TanStack Query** - Zustand for client-only state |
| DevTools | Always on | Conditional | **Conditional** - `enabled: import.meta.env.DEV` |

## Reference Files

Read these on demand for full code examples:

- Read(".claude/skills/zustand-patterns/references/core-patterns.md") — Basic store, slices, Immer, persist, selectors, async, devtools
- Read(".claude/skills/zustand-patterns/references/anti-patterns-and-integration.md") — Forbidden patterns and TanStack Query integration
- Read(".claude/skills/zustand-patterns/references/middleware-composition.md") — Combining multiple middleware in correct order
- Read(".claude/skills/zustand-patterns/scripts/store-template.ts") — Production-ready store template
- Read(".claude/skills/zustand-patterns/checklists/zustand-checklist.md") — Implementation checklist

## Task

$ARGUMENTS

Use the reference files above as needed. Review or implement based on what is asked.

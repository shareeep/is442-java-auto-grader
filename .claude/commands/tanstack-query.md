---
description: Review or implement TanStack Query v5 patterns. Covers query keys, mutations, cache invalidation, optimistic updates, and anti-patterns for server state management.
allowed-tools: Read, Grep, Glob, Write
disable-model-invocation: false
---

# TanStack Query v5

Async state manager for all server data — caching, background updates, and synchronization.

## Quick Reference

```typescript
// ✅ v5 single object signature
useQuery({ queryKey: ['users'], queryFn: fetchUsers })

// ✅ Typed query key factory
const userKeys = {
  all: ['users'] as const,
  detail: (id: string) => ['users', id] as const,
}

// ✅ Mutation with cache invalidation
const mutation = useMutation({
  mutationFn: createUser,
  onSuccess: () => queryClient.invalidateQueries({ queryKey: userKeys.all }),
})

// ✅ Dependent query
useQuery({ queryKey: userKeys.detail(id), queryFn: () => fetchUser(id), enabled: !!id })

// ❌ v4 overloads (removed in v5)
useQuery(queryKey, queryFn, options)

// ❌ onSuccess/onError on useQuery (removed in v5 — use QueryCache instead)
useQuery({ queryKey, queryFn, onSuccess: () => {} })
```

## Key v5 Changes

- `cacheTime` → `gcTime`
- `isLoading` → `isPending` (isLoading now means pending + fetching)
- `onSuccess/onError/onSettled` removed from `useQuery` — use `QueryCache` callbacks
- Suspense: use `useSuspenseQuery` (no experimental flag needed)

## Reference File

Read(".claude/skills/tanstack-query/SKILL.md") for full patterns including:
- Query key factories
- Optimistic updates
- Prefetching
- Error handling with QueryCache
- Testing with MSW
- Anti-patterns

## Task

$ARGUMENTS

Use the reference file above as needed. Review or implement based on what is asked.

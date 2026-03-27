# Config Hardcoding Issue — Analysis & Fix Plan

## The Problem

**B4 (Config Generalisation) is only half-fixed.**

The `ConfigInferenceService` + `writeQuestionConfigs()` solution from Phase 2 only works inside the **web/AI test generation flow**. The CLI and the grading pipeline itself still read from a hardcoded `config.properties` baked into the classpath at build time.

---

## Root Causes

### 1. `config.properties` is a classpath resource (bundled in the JAR)

`AppConfig` loads config like this:

```java
// AppConfig.java:25
try (InputStream is = getClass().getClassLoader().getResourceAsStream("config.properties")) {
```

This reads from inside the JAR. Any inference or user-provided config **cannot affect** a running JAR unless the in-memory `Properties` object is updated — and even then, it resets on next startup.

### 2. `writeQuestionConfigs()` writes to the source tree, not a stable external path

```java
// AppConfig.java:174
Path configPath = Paths.get("src/main/resources/config.properties");
```

- Works in dev (`./gradlew bootRun` from project root) ✅
- Broken when running a compiled JAR from any other directory ❌
- Has no effect on the already-running JAR's classpath resource ❌

### 3. `ConfigInferenceService` is only wired into the web layer

The inference → config update loop is only reachable via `POST /api/generation/analyze-setup` in `GenerationController`. The CLI (`ConsoleUI.java`) skips it entirely:

```
CLI menu → "1. Grade Submissions" → gradeSubmissions() → GradingPipeline
                                                          ↑
                                              reads AppConfig directly
                                              (classpath config.properties)
```

`ConfigInferenceService` is never called in the CLI path.

---

## What Needs to Change

### Goal

Config should:
1. Be loaded from an **external file** on the user's filesystem (not bundled in the JAR)
2. Fall back to the bundled `config.properties` if no external file exists
3. Be updatable at runtime by both the CLI and the web layer
4. Persist across restarts

---

## Fix Plan

### Step 1 — Change `AppConfig` to load from an external file first

Modify `AppConfig()` to use a priority chain:

1. **`./config.properties`** (working directory — user-managed, external)
2. **Classpath `config.properties`** (bundled defaults — fallback only)

```java
// Priority 1: external file in working directory
Path externalConfig = Paths.get("config.properties");
if (Files.exists(externalConfig)) {
    try (InputStream is = Files.newInputStream(externalConfig)) {
        properties.load(is);
    }
} else {
    // Priority 2: classpath fallback
    try (InputStream is = getClass().getClassLoader().getResourceAsStream("config.properties")) {
        if (is == null) throw new IOException("config.properties not found on classpath");
        properties.load(is);
    }
}
```

**Files to change:** `AppConfig.java`

---

### Step 2 — Fix `writeQuestionConfigs()` to write to the external path

Change the hardcoded source-tree path to write to `./config.properties` (working directory), which is the same path Step 1 reads from:

```java
// Before (broken for JARs):
Path configPath = Paths.get("src/main/resources/config.properties");

// After (stable external path):
Path configPath = Paths.get("config.properties");
if (!Files.exists(configPath)) {
    // Bootstrap: copy the classpath default out as a starting point
    try (InputStream is = getClass().getClassLoader().getResourceAsStream("config.properties")) {
        Files.copy(is, configPath);
    }
}
```

**Files to change:** `AppConfig.java` (`writeQuestionConfigs()` method)

---

### Step 3 — Add a `reload()` method to `AppConfig`

After `writeQuestionConfigs()` persists to disk, callers should be able to force a reload so the next `getQuestionConfigs()` call reflects the new values. (The web layer already updates in-memory, but this makes it explicit and usable from CLI too.)

```java
public void reload() throws IOException {
    Path externalConfig = Paths.get("config.properties");
    if (Files.exists(externalConfig)) {
        try (InputStream is = Files.newInputStream(externalConfig)) {
            properties.load(is);
        }
    }
}
```

**Files to change:** `AppConfig.java`

---

### Step 4 — Wire config inference into the CLI menu

Add a new CLI menu option (before grading) that runs `ConfigInferenceService` and updates config. Insert in `ConsoleUI.java`:

```
Menu:
  1. Grade Submissions
  2. Generate Test Cases
  3. Configure from Exam PDF   ← NEW
  4. View Current Configuration
  5. Exit
```

The new option should:
1. Prompt for the exam PDF path
2. Prompt for template directory path
3. Prompt for testers directory path
4. Call `ConfigInferenceService.infer(examMarkdown, templateDir, testersDir)`
5. Display detected questions and any conflicts
6. Ask user to confirm before calling `config.writeQuestionConfigs(inferredConfig)`
7. Print "Config saved to config.properties" confirmation

**Files to change:** `ConsoleUI.java`

You will also need to inject `ConfigInferenceService` and `PdfParser` into `ConsoleUI`. These are already constructed in `App.java` for the web path — follow that pattern for CLI mode.

---

### Step 5 — Update `.gitignore`

The external `config.properties` in the working directory will contain exam-specific paths and question configs that differ per deployment. Add it to `.gitignore` so users don't accidentally commit their local config over the bundled defaults:

```gitignore
# External runtime config (user-managed; generated by the app)
/config.properties
```

The bundled `src/main/resources/config.properties` stays in git as the default template.

**Files to change:** `.gitignore`

---

## File Change Summary

| File | Change |
|------|--------|
| `AppConfig.java` | Load from `./config.properties` first, fallback to classpath; fix `writeQuestionConfigs()` target path; add `reload()` |
| `ConsoleUI.java` | Add "Configure from Exam PDF" menu option wired to `ConfigInferenceService` |
| `App.java` | Inject `ConfigInferenceService` + `PdfParser` into `ConsoleUI` for CLI mode |
| `.gitignore` | Ignore `./config.properties` (external runtime config) |

---

## Affected Flows After Fix

| Flow | Before | After |
|------|--------|-------|
| CLI → Grade | Reads hardcoded classpath config | Reads `./config.properties` if present |
| CLI → Configure from PDF | Not available | Infers + writes `./config.properties` |
| Web → analyze-setup | Writes to source tree (broken for JAR) | Writes to `./config.properties` |
| Web → Grade | Reads in-memory (session-only) | Reads in-memory; persists to `./config.properties` |
| Cold start (no external config) | Always uses bundled defaults | Falls back to bundled defaults — no regression |

---

## Starting From Scratch (No Prior Config)

This is the case when someone clones the repo fresh, or when a new semester/exam starts and the old Q1a/Q1b/Q2a/Q2b/Q3 values in the bundled defaults are completely wrong.

### The problem with silent fallback

The current plan's "fall back to classpath `config.properties`" is a regression risk for fresh starts: the bundled defaults have **last semester's question IDs, tester names, and scores** hardcoded. If a user skips the "Configure from Exam PDF" step and goes straight to grading, they'd be grading against the wrong config with no warning.

### What needs to change

**The bundled `src/main/resources/config.properties` should be stripped down to a safe, obviously-incomplete template:**

```properties
# ============================================================
# IS442 Auto Grading System - Configuration
# ============================================================
# This file contains defaults only. Run the app and use
# "Configure from Exam PDF" to generate your exam-specific config.
# ============================================================

execution.timeout.seconds=10
validation.strict.mode=false
validation.allow.extra.files=true
output.include.anomalies=true
assessment.name=IS442 – Exam
assessment.template.folder=RenameToYourUsername
docling.serve.url=http://localhost:5001
ai.model=minimax/minimax-m2.7
ai.default.cases.per.question=3

# No question config — must be generated via "Configure from Exam PDF"
questions.list=
questions.folders=
questions.testers=
questions.max.scores=
questions.dependency.folders=
questions.dependency.files=
```

**`AppConfig.getQuestionConfigs()` should detect empty config and fail loudly:**

```java
if (ids.length == 0) {
    throw new IllegalStateException(
        "No questions configured. Run 'Configure from Exam PDF' first, " +
        "or manually populate config.properties."
    );
}
```

This turns a silent wrong-config into an immediate actionable error.

**The CLI should detect unconfigured state on startup and prompt accordingly:**

```
╔═══════════════════════════════════════════════════════╗
║         IS442 Auto Grading System v1.0               ║
╚═══════════════════════════════════════════════════════╝

⚠  No question configuration found.
   Run option 3 (Configure from Exam PDF) before grading.
```

Only show this warning if `questions.list` is empty — don't block the menu, just surface it.

### First-run flow (fresh start)

```
1. Clone repo / download JAR
2. Place exam PDF somewhere accessible
3. Run app → see "No question config" warning
4. Choose "3. Configure from Exam PDF"
5. Enter PDF path, template dir, testers dir
6. Review inferred questions + conflicts
7. Confirm → writes ./config.properties
8. Choose "1. Grade Submissions" — now uses correct config
```

### Files affected (additions to the main plan)

| File | Additional change |
|------|------------------|
| `src/main/resources/config.properties` | Strip question config to empty template |
| `AppConfig.java` | Throw on empty `questions.list` instead of returning empty list |
| `ConsoleUI.java` | Print warning on startup if `questions.list` is empty |

---

## Out of Scope for This Fix

- Validation of inferred config before writing (already handled by `ConfigConflict` model)
- Parallel grading (separate tech debt item)
- Docker: the `./config.properties` path will resolve correctly inside the container as long as the working directory is `/app` (which it is per the current `Dockerfile`)

---
name: java-backend
description: >
  Working with the IS442 Java Auto-Grader backend. Use this skill whenever you are
  touching Java source files, build.gradle, config.properties, the CLI grading pipeline,
  the Spring Boot web API, the AI test generation pipeline, or any service in
  src/main/java/com/is442/autograder/. Also use it when debugging compilation/execution
  failures, adding new questions, tweaking AI prompts, or configuring the Docling/OpenRouter
  integrations. If you are about to write or modify any Java backend code in this project,
  invoke this skill first.
---

# IS442 Java Auto-Grader — Backend Skill

## Project snapshot

Two runtimes share the same source tree:

| Mode | Entry point | JAR task | Port |
|------|-------------|----------|------|
| CLI grading (one-shot) | `App.main()` with `--submissions`, `--testers`, `--scoresheet`, `--output` flags | `fatJar` | — |
| Interactive CLI | `App.main()` with `--cli` | `fatJar` | — |
| Spring Boot web server | `WebApplication.main()` | `bootJar` | 8080 |

Build with Gradle (`./gradlew`). Java 25 is required — the `build.gradle` sets both `sourceCompatibility` and `targetCompatibility` to `JavaVersion.VERSION_25`. Do not downgrade this.

---

## Build commands to know

```bash
./gradlew fatJar          # CLI uber-JAR → build/libs/autograder-*-all.jar
./gradlew bootJar         # Spring Boot web JAR → build/libs/autograder-*-web.jar (not literally, check exact name)
./gradlew bootRun         # Run dev web server on :8080
./gradlew spotlessApply   # Auto-format (Eclipse JDT — must pass before push)
./gradlew checkstyleMain  # Google Java Style lint (warnings only, non-blocking)
```

Skip the frontend build (e.g., inside Docker or during backend-only work):
```bash
./gradlew bootJar -PskipFrontend
```

Code style is enforced by a **git pre-push hook** via Spotless. If you touch Java files, run `./gradlew spotlessApply` before committing.

---

## Environment & external services

The backend needs two external services running:

| Service | Default URL | Env var override | Purpose |
|---------|-------------|-----------------|---------|
| Docling Serve | `http://localhost:5001` | `DOCLING_SERVE_URL` | PDF → Markdown via OCR |
| OpenRouter API | `https://openrouter.ai/api/v1` | — (set in `application.properties`) | LLM calls via LangChain4j |

Keys and overrides live in a **`.env` file** in the working directory. `EnvLoader.load()` reads it on startup and calls `System.setProperty()`:
```
OPENROUTER_API_KEY=sk-or-v1-...
DOCLING_SERVE_URL=http://localhost:5001   # optional override
```

If `.env` is absent, `EnvLoader` falls back to `System.getenv()`. CI/Docker should inject these as real env vars.

---

## Config file gotchas

`AppConfig` loads `config.properties` with this priority order:
1. **External file** at `./config.properties` (working directory) — wins
2. **Classpath** `config.properties` (bundled in JAR) — fallback

When the user saves a configuration via the web UI (`GenerationController.save()`), the file is written to the **external** location. If you only edit the classpath version you will be confused by runtime behaviour that ignores your changes.

Key config keys:
```properties
execution.timeout.seconds=10
questions.q1a.folder=Q1a
questions.q1a.tester=Q1aTester.java
questions.q1a.maxScore=10
questions.q1a.dependency.folders=RenameToYourUsername
questions.q1a.dependency.files=
```

---

## Grading pipeline — how execution works

For each student × question:
1. Copy the question folder from the student's extracted submission into a temp compile dir.
2. Copy dependency `.class` files from the template folder into the same dir.
3. Copy the tester `.java` file into the dir.
4. Run `javac -encoding UTF-8 *.java` with a 10 s timeout.
5. Run `java -cp . {TesterClassName}` with a 10 s timeout.
6. Parse `ProcessResult` → `SUCCESS | COMPILE_ERROR | RUNTIME_ERROR | TIMEOUT`.

**Common AI mistakes:**
- Adding external JARs to a question's compilation classpath — not supported. The only classpath is `.` (the temp dir). Dependencies must be pre-compiled `.class` files already in the template folder.
- Changing the tester filename convention. Testers **must** end in `Tester.java` (e.g., `Q1aTester.java`). The `ConfigInferenceService` discovers them by this pattern.
- Assuming a student's class file will have a specific name — it's inferred from the `.java` filename.

---

## AI test generation pipeline

Flow: **Upload → Analyze → Generate → Review → Save**

```
ExamController          → store file, return UUID
GenerationController    → analyzeSetup() / generate() / save()
TestGenerationService   → orchestrates one question end-to-end
LangChainService        → @AiService, calls OpenRouter (minimax/minimax-m2.7)
TesterFileWriter        → deterministic Java code assembly from StructuredTestCase records
SessionDatabase         → SQLite cache at data/session.db (avoids re-parsing PDFs)
```

### LangChain4j @AiService quirk

`LangChainService` is a plain Java **interface** annotated with `@AiService`. The implementation is generated at runtime by LangChain4j:
```java
@Bean
public LangChainService langChainService(ChatModel chatModel) {
    return AiServices.create(LangChainService.class, chatModel);
}
```
Do not write an implementation class for it. If you need to add a new AI method, add it to the interface and match the system/user prompt via `@SystemMessage` / `@UserMessage` annotations.

### Known issues to check (update this skill when fixed)

These are current rough edges — not permanent rules. If you're debugging the AI pipeline or improving error handling, these are the first places to look:

- **OpenRouter + MiniMax reasoning:** The model `minimax/minimax-m2.7` requires reasoning tokens. `reasoningEffort("none")` causes a 400 from OpenRouter; current config uses `"low"`. If the model or config changes, verify this is still needed.

- **Docling error handling:** `PdfParser` has no graceful fallback if Docling Serve is unreachable or returns an error — it throws `IOException` which surfaces as a 400 to the client. This should get better structured error responses and retries. If you see mysterious 400s on `/analyzeSetup`, check Docling first.

- **JSON parse failures in generation:** `TestGenerationService.parseStructuredCases()` silently returns an empty list if the LLM response can't be parsed. The error is logged but the caller just sees 0 test cases. This should surface a proper error to the frontend. If generation appears to succeed but produces nothing, check the logs for parse failures here.

### StructuredTestCase → Java code mapping

`TesterFileWriter.buildCodeFromStructured()` maps semantic fields deterministically:

| Field | Java output |
|-------|------------|
| `description` + `conceptCovered` | Block comment |
| `setup` | Variable assignments before the call |
| `methodCall` | The actual invocation |
| `assertion` | `assert` or `System.out.println()` |
| `expectsException=true` + `exceptionType` | `try { … } catch (X e) { … }` |

Keep `StructuredTestCase` as a **record** — it is serialized/deserialized by Jackson and referenced by LangChain4j's JSON schema extraction.

---

## SQLite session cache

`SessionDatabase` creates `data/session.db` (auto-created if missing). Tables are created on first run — no migrations.

- `exams` — UUID → parsed PDF markdown
- `questions` — UUID → per-question markdown slice
- `exam_configs` — UUID → inferred question config JSON

**Gotcha:** There is no TTL or cleanup. Over time `data/` will grow. Don't worry about this in dev; in production you'd want a cleanup job.

---

## REST API conventions

- All endpoints return `ResponseEntity<?>`.
- Errors: `ResponseEntity.badRequest().body(Map.of("error", "message"))` — no `@ExceptionHandler`, errors are handled inline.
- Upload UUIDs (not raw paths) are used as references. The server maintains in-memory `ConcurrentHashMap<String, Path>` for exam/template/tester mappings. These are **process-scoped** — they disappear on restart. Don't expect them to survive a reboot.
- SSE grading stream: `GradingStreamController` uses `SseEmitter` with an executor pool (4 threads). Callbacks are `Consumer<StudentSubmission>` and `Consumer<String>` injected into `GradingPipeline.run()`.

---

## Error handling norms

- **No custom exception classes** — use `IOException`, `InterruptedException`, etc.
- **Fail-soft** — pipelines continue on non-fatal errors. Use `Anomaly` (with `AnomalyType` enum + `AnomalySeverity`) to record issues without crashing.
- **ProcessResult** is an enum-like set of factory methods: `ProcessResult.success(stdout)`, `ProcessResult.compileError(stderr)`, `ProcessResult.timeout(partial)`.
- Log with SLF4J: `logger.info("[SERVICE] action  key={}", value)` — bracket-prefix convention is established throughout the codebase.

---

## Coding conventions

- **Records for pure data:** `StructuredTestCase`, `StudentIdentity`, `ProcessResult` — keep them as records.
- **Path API everywhere:** Use `java.nio.file.Path` and `Files.*`. No `File.separator` string juggling.
- **Constructor injection for Spring beans** — no field `@Autowired`.
- **Callback-based streaming:** Heavy operations accept `Consumer<T>` callbacks rather than returning lists, so the web layer can emit SSE events in real time.
- **Spotless (Eclipse JDT) formatting** — run `./gradlew spotlessApply` after any Java edit. The formatter is opinionated; let it do its job rather than fighting it.

---

## ZIP extraction security model

`ZipExtractor` enforces hard limits. Don't relax these:
- Max total uncompressed: 100 MB
- Max entries: 1,000
- Max path length: 255
- Blocks `..` traversal, `__MACOSX/` metadata, symlinks

---

## Checklist before calling backend work done

- [ ] `./gradlew spotlessApply` passes cleanly
- [ ] `./gradlew bootRun` starts without errors (if touching web layer)
- [ ] `./gradlew fatJar` builds without errors (if touching CLI layer)
- [ ] New AI service methods added to `LangChainService` interface only — no implementation class
- [ ] Tester files follow `{QuestionId}Tester.java` naming
- [ ] No external JAR dependencies added to question compilation classpath
- [ ] `reasoningEffort` NOT set to `"none"` for OpenRouter/MiniMax
- [ ] `config.properties` edits target the external file (working directory), not just classpath

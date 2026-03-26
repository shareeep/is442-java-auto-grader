# Multithreading & Concurrency Proposal

This document proposes parallelism improvements for two areas of the codebase: AI test generation API calls and the student grading pipeline.

---

## Area 1 — Test Generation API Calls

### Current bottleneck

`GenerationController.generate()` (line 55) loops over questions **sequentially**. With 5 questions at ~30s per OpenRouter call, the bulk `/generate` endpoint blocks for ~150s total.

The `/generate-question` endpoint already handles per-question progress at the frontend level — the React UI calls it once per question. The `/generate` bulk endpoint has no such mechanism.

### Thread safety audit

Before parallelising, the services used inside the loop were audited:

**`ExamPdfParser`** — opens a fresh `PDDocument` per call via try-with-resources. No shared state.

```java
// ExamPdfParser.java:23
public String extractAllText(Path pdfPath) throws IOException {
    try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {  // new instance per call
        PDFTextStripper stripper = new PDFTextStripper();            // new instance per call
        return stripper.getText(document);
    }
}
```

**`ClaudeApiClient`** — holds only two fields: `HttpClient` (thread-safe by Java spec) and `ObjectMapper` (thread-safe by Jackson design). No mutable state.

**Verdict: `TestGenerationService.generateForQuestion()` is fully thread-safe. No changes needed to underlying services.**

### Approach A — `CompletableFuture` in `GenerationController` (recommended starting point)

Change the sequential `for` loop in `generate()` to fan out with a bounded thread pool:

```java
// GenerationController.generate() — replace the for loop
int poolSize = Math.min(request.getQuestions().size(), 5);
ExecutorService executor = Executors.newFixedThreadPool(poolSize);
try {
    List<CompletableFuture<GenerationResult>> futures = request.getQuestions().stream()
        .map(sel -> CompletableFuture.supplyAsync(() -> {
            QuestionConfig qc = allQuestions.stream()
                .filter(q -> q.getQuestionId().equals(sel.getQuestionId()))
                .findFirst().orElse(null);
            if (qc == null) {
                return new GenerationResult(sel.getQuestionId(), List.of(), false,
                    "Unknown question: " + sel.getQuestionId(), "");
            }
            Path existingTester = null;
            if (testersDir != null) {
                Path candidate = testersDir.resolve(qc.getTesterClassName() + ".java");
                if (Files.exists(candidate)) existingTester = candidate;
            }
            try {
                return generationService.generateForQuestion(
                    qc, examPdf, existingTester, sel.getNumCases(), templateDir);
            } catch (Exception e) {
                return new GenerationResult(sel.getQuestionId(), List.of(), false,
                    "Generation failed: " + e.getMessage(), "");
            }
        }, executor))
        .toList();

    List<GenerationResult> results = futures.stream()
        .map(CompletableFuture::join)  // preserves insertion order
        .toList();
    return ResponseEntity.ok(results);
} finally {
    executor.shutdown();
}
```

**Properties:**
- Single-file change, easy to review and revert
- Errors are isolated per question — if Q1a fails, Q2a still proceeds
- Result order is preserved (futures list is ordered by question, `.join()` collects in order)
- Pool capped at 5 to avoid hammering the OpenRouter rate limit

### Approach B — `sendAsync()` in `ClaudeApiClient` (follow-up, more idiomatic)

Java's `HttpClient` has a native `sendAsync()` that returns `CompletableFuture<HttpResponse<String>>` — no threads are blocked waiting on I/O during the 120s HTTP timeout:

```java
// ClaudeApiClient — add async variant
public CompletableFuture<String> generateTesterCodeAsync(String questionId,
        String testerClassName, String examContext,
        String existingTesterCode, int numCases, String additionalContext) {

    // same request-building logic as generateTesterCode()...

    return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
        .thenApply(response -> {
            if (response.statusCode() != 200) {
                throw new RuntimeException(
                    "OpenRouter API error " + response.statusCode() + ": " + response.body());
            }
            try {
                return stripMarkdownFences(extractContent(response.body()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
}
```

`TestGenerationService` would then expose `generateForQuestionAsync()` returning `CompletableFuture<GenerationResult>`, and `GenerationController` would collect with `CompletableFuture.allOf()`.

**Note:** `thenApply` stages run on `ForkJoinPool.commonPool()` by default. This is fine for I/O-bound completion work.

### What NOT to parallelize in this area

The `/generate-question` endpoint is already designed for per-question calls from the frontend — parallelism there belongs at the HTTP client layer, not the server.

---

## Area 2 — Student Grading Pipeline

### Current bottleneck

`GradingPipeline.run()` processes students sequentially (line 103). With 40 students × 5 questions × compile+run, this is wall-clock heavy.

### Why the fix is non-trivial: ConsoleReporter

`ConsoleReporter` has **deeply sequential assumptions** that make a simple `synchronized` wrapper insufficient:

```java
// ConsoleReporter.java — none of these are volatile or synchronized
private String pendingStudentHeader = null;  // set by beginStudentLog()
private boolean inStudentGroup = false;       // flipped on first log call
private final List<String> logLines = new ArrayList<>();  // plain ArrayList
private int currentProgress = 0;              // raw int
```

| Shared state problem | Why it breaks under parallelism |
|---|---|
| `pendingStudentHeader` / `inStudentGroup` | Correlated two-field state machine. Thread A sets header = "Alice", Thread B overwrites with "Bob", Thread A reads back "Bob" when it first logs — wrong header gets printed. `synchronized` fixes the race but not the logical interleaving. |
| `logLines` ArrayList | Not thread-safe. Even with `synchronized` adds, the content is wrong due to the header interleaving above. |
| Lanterna "Current: X" display | Shows one student name — meaningless when N students run in parallel. |
| `ConsoleLogCapture` | Redirects `System.out` globally. Multiple threads writing to it produce garbled log output. |
| `GradingEngine` shared instance | Holds a reference to `consoleReporter` and calls `logWarning`/`logError` on it during compilation — same problems propagate. |

### What a correct fix requires

The cleanest approach is a **per-student log buffer with a single renderer**:

```
Each student thread:
  - accumulates its own List<String> of log lines locally
  - on completion, posts a StudentResult(submission, logs) to a BlockingQueue

Render thread (main or dedicated):
  - drains the BlockingQueue
  - updates the progress counter (AtomicInteger is sufficient here)
  - replays log lines to ConsoleReporter
  - updates Lanterna screen once per drain cycle
```

This means `ConsoleReporter` needs a new `recordResult(StudentSubmission, List<String> logs)` method, replacing the incremental `beginStudentLog` / `logWarning` / `logError` pattern used today. The progress bar would display `"Grading N students in parallel..."` rather than `"Current: Alice"`.

### Sketch of the parallel grading loop

```java
// GradingPipeline.run() — after ConsoleReporter redesign
int threads = Runtime.getRuntime().availableProcessors();
ExecutorService executor = Executors.newFixedThreadPool(threads);
AtomicInteger completed = new AtomicInteger(0);
List<Future<StudentGradingResult>> futures = new ArrayList<>();

for (Path zipFile : zipFiles) {
    futures.add(executor.submit(() -> {
        List<String> localLogs = new ArrayList<>();
        StudentSubmission submission = gradeOneStudent(zipFile, localLogs);
        return new StudentGradingResult(submission, localLogs);
    }));
}

List<StudentSubmission> submissions = new ArrayList<>();
for (Future<StudentGradingResult> f : futures) {
    StudentGradingResult r = f.get();
    consoleReporter.recordResult(r.submission(), r.logs(), completed.incrementAndGet(), zipFiles.size());
    submissions.add(r.submission());
}
executor.shutdown();
```

Each `GradingEngine` instance would need to be created per-student (not shared), taking a local log list instead of `ConsoleReporter` directly.

---

## Honest Assessment

| Area | Validity | Status |
|---|---|---|
| Generation API calls — `CompletableFuture` in `GenerationController` (Approach A) | Correct and safe | Ready to implement |
| Generation API calls — `sendAsync()` in `ClaudeApiClient` (Approach B) | Also valid, more efficient | Good follow-up after A |
| Parallel student grading | Right direction, but the described fix was insufficient | Requires `ConsoleReporter` redesign first |

## Suggested Implementation Order

1. **Parallelize `GenerationController.generate()`** with `CompletableFuture.supplyAsync()` — single-file change, highest ROI, no prerequisite work
2. *(Optional)* Migrate `ClaudeApiClient` to `sendAsync()` — fully non-blocking I/O, no threads blocked during HTTP wait
3. **Redesign `ConsoleReporter`** logging model — per-student buffer + single render thread
4. **Parallelize `GradingPipeline`** at student level — bounded `ExecutorService`, one `GradingEngine` per student

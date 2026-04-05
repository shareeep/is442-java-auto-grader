# CLI Guide

## Running the JAR

```bash
java -jar autograder.jar [mode] [options]
```

Three modes:

| Mode | Flag | Description |
|------|------|-------------|
| One-shot | positional args | Provide all params upfront, grading runs immediately |
| Interactive | `--cli` | Step-by-step TUI prompts via Lanterna |
| Web server | `--web` | Starts Spring Boot REST API on port 8080 |

---

## One-Shot Mode

### Parameters

```bash
java -jar autograder.jar \
  --submissions ./student-submissions.zip \
  --testers ./Tester-Files \
  --output ./results \
  [--scoresheet ./IS442-ScoreSheet.csv] \
  [--timeout 30] \
  [--name "IS442 Midterms"]
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `--submissions` | Yes | Path to directory containing student ZIP files (or a single ZIP) |
| `--testers` | Yes | Path to directory containing JUnit `.java` tester files |
| `--output` | No | Output directory for reports and logs. Defaults to `output/` |
| `--scoresheet` | No | Path to the IS442 score sheet CSV for name/ID enrichment |
| `--timeout` | No | Per-question execution timeout in seconds. Default: 30 |
| `--name` | No | Assessment name used in PDF report header |

### Example

```bash
java -jar autograder.jar \
  --submissions ./student-zips \
  --testers ./Tester-Files \
  --output ./results \
  --scoresheet ./IS442-ScoreSheet.csv \
  --timeout 60 \
  --name "IS442 Midterms 2024"
```

### Output

```
Found 18 submission(s) to grade.

[1/18] Processing alice.chan.2023.zip ...
  ✓ alice.chan.2023  Q1a: 5/5  Q1b: 4/5  Q2a: 3/3  Q2b: 0/4

[2/18] Processing ben.tan.2023.zip ...
  ✗ ben.tan.2023  Q1a: ERR-COMP

... (live progress for each student)

────────────────────────────────────────
Summary
  Total: 18  |  Passed: 12  |  Failed: 4  |  Errors: 2
  Average: 71.2%
────────────────────────────────────────

Scoresheet exported to: results/IS442-ScoreSheet-Graded.csv
Detailed report exported to: results/detailed-report.csv
Full logs exported to: results/logs
PDF report exported to: results/instructor-report.pdf
Plagiarism report exported to: results/plagiarism-report.jplag.jplag
```

---

## Interactive Mode

Start with:

```bash
java -jar autograder.jar --cli
```

### Lanterna TUI Layout

```
╔══════════════════════════════════════════════════════╗
║  IS442 Auto-Grader v1.0.0          [Press Q to quit]║
╠══════════════════════════════════════════════════════╣
║                                                      ║
║  Step 1: Select Student Submissions                  ║
║  > Browse to folder containing .zip files            ║
║                                                      ║
║  [Directory: ./student-zips]                         ║
║  Found: 18 ZIP files                                ║
║                                                      ║
║  Step 2: Select Tester Files                        ║
║  > Browse to Tester-Files folder                     ║
║                                                      ║
╚══════════════════════════════════════════════════════╝
```

### Step-by-Step Flow

1. **Select Submissions** — pick the folder containing student ZIP files
2. **Select Testers** — pick the `Tester-Files` folder with `.java` testers
3. **Scoresheet (optional)** — press Enter to skip, or pick a CSV
4. **Confirm and Run** — press Enter to begin grading
5. **Live Output** — each student's result prints as grading progresses
6. **Summary** — final stats, then prompt to open PDF or exit

### Exiting

- Press `Q` (uppercase) at any prompt to quit
- Press `Ctrl+C` to force-terminate (grading stops, partial results preserved)

> **Known issue:** The `Q` character is not visibly echoed in the terminal when exiting. This is a minor Lanterna display quirk — the key is still registered correctly.

---

## Output Artifacts

After grading completes (both one-shot and interactive), the following are written to the output directory:

| File | Description |
|------|-------------|
| `instructor-report.pdf` | Multi-section PDF: overview, question performance, anomalies, per-student results, insights |
| `IS442-ScoreSheet-Graded.csv` | LMS-compatible grade sheet with names, IDs, and scores |
| `detailed-report.csv` | Full per-question breakdown for all students |
| `results.json` | Structured JSON of all results (for frontend consumption) |
| `logs/run.log` | Full pipeline log |
| `logs/{student}/` | Per-student compilation and execution logs |
| `code/{username}/` | Extracted and normalized student Java source files |
| `testers/` | Copy of the input tester files |
| `plagiarism-report.jplag` | JPlag report zip — open with the plagiarism viewer |

---

## Developer Notes

### App.java Dispatch Logic

```
java -jar autograder.jar --web     → starts WebApplication (Spring Boot)
java -jar autograder.jar --cli     → starts ConsoleUI (Lanterna TUI)
java -jar autograder.jar [paths]   → one-shot mode (GradingPipeline.run)
java -jar autograder.jar           → interactive mode (default)
```

The dispatch is in `App.java`:
```java
if (args.length == 1 && "--web".equals(args[0])) {
    new WebApplication().run(args);
} else if (args.length == 1 && "--cli".equals(args[0])) {
    ConsoleUI.run(args);
} else if (args.length >= 2) {
    // one-shot: parse --submissions, --testers, etc.
} else {
    // interactive (no args)
}
```

---

### GradingPipeline Callback API

For streaming use (web SSE), `GradingPipeline.run()` accepts optional callbacks:

```java
public List<StudentSubmission> run(
    Path submissionsDir,
    Path testerFilesDir,
    Path scoresheetPath,
    Path outputDir,
    Consumer<StudentSubmission> onStudentGraded,      // fired after each student
    Consumer<String> onStudentStarted                 // fired when each ZIP is opened
)
```

These are used by `GradingStreamController` to emit SSE events to the frontend in real time.

---

### Cancellation

- CLI: `ConsoleReporter.isStopRequested()` checked after each student; pressing `Q` sets it
- Web: `SseEmitter` handles disconnection; `GradingPipeline.cancel()` sets `AtomicBoolean cancelled`
- Cancelled runs: partial results are preserved, `run.json` updated to `"status": "cancelled"`

# Setup Commands

Quick reference for setting up and running the IS442 Auto-Grading System.

## Prerequisites

```bash
# Check Java version (17+ required)
java -version
```

> **SSL Fix**: If Gradle gives SSL/certificate errors, set `JAVA_HOME` to Temurin JDK:
> ```bash
> export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home
> ```
> Add this to your `~/.zshrc` to make it permanent.

> **No Gradle install needed** — the project includes a Gradle Wrapper (`gradlew`).
> Your team just needs Java 17+; the wrapper downloads the correct Gradle version automatically.

## Build

```bash
# Clean build (compiles, tests, and packages into executable fat JAR)
./gradlew clean build

# Compile only (faster, for development)
./gradlew compileJava

# Package fat JAR without running tests
./gradlew fatJar
```

## Run

### Interactive Mode
```bash
java -jar build/libs/autograder-1.0-SNAPSHOT-all.jar
```
The interactive CLI guides you through entering paths step-by-step. Type `back` at any prompt to return to the previous step.

### CLI Mode (One-Shot)
Skip the interactive prompts by passing all paths as arguments:
```bash
java -jar build/libs/autograder-1.0-SNAPSHOT-all.jar \
  --submissions ./is442-project-materials/student-submission \
  --testers ./is442-project-materials/Tester-Files \
  --scoresheet ./is442-project-materials/IS442-ScoreSheet.csv \
  --output ./output
```

| Argument | Required | Description |
|----------|----------|-------------|
| `--submissions` | Yes | Folder containing student ZIP files |
| `--testers` | Yes | Folder containing tester `.java` files (e.g. `Q1aTester.java`) |
| `--scoresheet` | No | Input scoresheet CSV template (LMS format). If provided, the graded scoresheet and official names/IDs are used in the output |
| `--output` | No | Output directory for results (default: `./output`) |

### Build + Run (One-Liner)
```bash
./gradlew fatJar -q && java -jar build/libs/autograder-1.0-SNAPSHOT-all.jar
```

### Run via Gradle (interactive mode)
```bash
./gradlew run --console=plain
```

## Output Files

| File | Description |
|------|-------------|
| `IS442-ScoreSheet-Graded.csv` | LMS-compatible scoresheet with total scores filled in (only if `--scoresheet` was provided) |
| `detailed-report.csv` | Per-question breakdown with OrgDefinedId, names, scores, and anomaly counts |

## Test

```bash
# Run all unit tests
./gradlew test
```

## Lint / Code Style

```bash
# Auto-fix formatting, imports, and whitespace
./gradlew spotlessApply

# Check for issues without fixing (useful in CI)
./gradlew spotlessCheck

# Run Checkstyle (reports only, no auto-fix)
./gradlew checkstyleMain

# View Checkstyle report at: build/reports/checkstyle/main.html
```

### VSCode Setup
1. Install **Language Support for Java™ by Red Hat**
2. Use `Shift+Option+F` (Mac) to format documents
3. Checkstyle violations will also appear in `./gradlew checkstyleMain` output

## Project Structure

```
src/main/java/com/is442/autograder/
├── App.java               # Entry point (CLI arg parsing)
├── GradingPipeline.java   # Orchestrates the full grading flow
├── config/                # AppConfig (reads config.properties)
├── extraction/            # ZipExtractor, StructureNormalizer, IdentityResolver
├── validation/            # SubmissionValidator (header checks, anomalies)
├── execution/             # GradingEngine + ProcessRunner (compile & run)
├── reporting/             # ConsoleReporter + CSVExporter
├── model/                 # StudentSubmission, QuestionResult, Anomaly, etc.
├── ui/                    # ConsoleUI (interactive mode)
└── util/                  # FileUtils
```

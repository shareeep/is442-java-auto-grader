# Config Externalization Fix — Final

## What Changed

### Problem
`AppConfig` loaded from classpath `config.properties` (bundled in JAR). Inference via `writeQuestionConfigs()` wrote to `src/main/resources/config.properties` (broken for JARs).

### Solution
Config is now **dynamically inferred at runtime** from testers + template folder. No persistence needed.

### Changes Made

1. **`AppConfig.java`** — Loads `./config.properties` first (external), falls back to classpath. Added `hasQuestionsConfigured()` and `reload()`.

2. **`GradingPipeline.java`** — Added `setInferredQuestionConfigs(List<QuestionConfig>)`. Uses inferred configs if set, falls back to `config.getQuestionConfigs()`.

3. **`ConfigInferenceService.java`** — Fixed `ConcurrentModificationException` in Step 3b (iterate over copy of keys).

4. **`InferredQuestionConfig.java`** — Added `toQuestionConfig()` converter.

5. **`ConsoleUI.java`** — Simplified menu (Grade/Exit only). Grading flow: submissions → testers → inference → scoresheet → output.

6. **`App.java`** — `runCli()` and `runGenerateCli()` now infer questions from testers.

7. **`config.properties`** — Question config arrays are empty. General settings preserved.

8. **`.gitignore`** — Added `config.properties`.

## CLI Commands

```bash
# One-shot grading (auto-infers questions)
java -jar build/libs/autograder-1.0-SNAPSHOT-all.jar \
  --submissions ./student-submissions \
  --testers ./Tester-Files \
  --output ./output

# Interactive CLI mode
java -jar build/libs/autograder-1.0-SNAPSHOT-all.jar --cli
```

## How Inference Works

1. Scans tester directory for `*Tester.java` files
2. Extracts question IDs (e.g., `Q1aTester.java` → Q1a)
3. Scans template folder for matching `Q*` subfolders
4. Computes max scores from tester file structure
5. Handles implicit parent questions (Q1 from Q1a/Q1b)

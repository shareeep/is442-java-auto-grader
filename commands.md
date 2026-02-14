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

### CLI Mode
```bash
java -jar build/libs/autograder-1.0-SNAPSHOT-all.jar \
  --submissions ./is442-project-materials/student-submission \
  --testers ./is442-project-materials/Tester-Files \
  --scoresheet ./is442-project-materials/IS442-ScoreSheet.csv \
  --output ./output
```

### Run via Gradle directly (interactive mode)
```bash
./gradlew run --console=plain
```

## Test

```bash
# Run all unit tests
./gradlew test
```

## Lint / Code Style

```bash
# Run Checkstyle
./gradlew checkstyleMain

# View report at: build/reports/checkstyle/main.html
```

### VSCode Setup
1. Install **Language Support for Java™ by Red Hat**
2. Use `Shift+Option+F` (Mac) to format documents
3. Checkstyle violations will also appear in `./gradlew checkstyleMain` output

# Setup Commands

Quick reference for setting up and running the IS442 Auto-Grading System.

## Prerequisites

```bash
# Check Java version (17+ required)
java -version

# Install Maven via Homebrew (macOS)
brew install maven

# Verify Maven is installed
mvn -version
```

> **SSL Fix**: If Maven gives SSL/certificate errors, set `JAVA_HOME` to Temurin JDK:
> ```bash
> export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home
> ```
> Add this to your `~/.zshrc` to make it permanent.

## Build

```bash
# Clean build (compiles and packages into executable JAR)
mvn clean package -q

# Compile only (faster, for development)
mvn clean compile
```

## Run

### Interactive Mode
```bash
java -jar target/autograder-1.0-SNAPSHOT.jar
```

### CLI Mode
```bash
java -jar target/autograder-1.0-SNAPSHOT.jar \
  --submissions ./is442-project-materials/student-submission \
  --testers ./is442-project-materials/Tester-Files \
  --scoresheet ./is442-project-materials/IS442-ScoreSheet.csv \
  --output ./output
```

## Test

```bash
# Run all unit tests
mvn test
```

## Lint / Code Style

```bash
# Run Checkstyle (Google Java Style)
mvn checkstyle:check

# Generate Checkstyle report (HTML)
mvn checkstyle:checkstyle
# Report at: target/site/checkstyle.html
```

### VSCode Setup
1. Install **Language Support for Java™ by Red Hat**
2. Use `Shift+Alt+F` (or `Shift+Option+F` on Mac) to format
3. Checkstyle violations will also appear in the Maven build output

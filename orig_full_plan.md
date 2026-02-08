# IS442 Java Auto-Grading System - Project Plan

## 1. Project Overview

### 1.1 Problem Statement
Manual grading of programming assignments is extremely time-consuming, error-prone, and inconsistent. With large class sizes, instructors must repeatedly:
- Verify folder structures and naming conventions
- Extract and organize student submissions
- Compile and run code against test cases
- Handle infinite loops and execution timeouts
- Manually record and aggregate scores

### 1.2 Solution
An **Auto Grading System** that automates the entire workflow by:
- Ingesting student submissions in bulk (ZIP files)
- Validating file structure and naming conventions
- Compiling and executing code against predefined test cases
- Handling execution timeouts safely
- Producing structured grading reports (CSV format)

### 1.3 Success Criteria (based on project rubrics)
| Component | Weightage | Key Criteria |
|-----------|-----------|--------------|
| Code | 7 | Modular, clean, follows Java conventions, documented |
| Application | 16 | Complete features, usable, handles edge cases robustly |
| Presentation | 7 | OOP design, libraries used, demo quality |

---

## 2. Edge Cases & Submission Issues to Handle

Based on the provided sample submissions and `issues_with_submissions.md`, the system must handle:

### 2.1 Folder Naming Issues
| Issue | Example | Resolution Strategy |
|-------|---------|---------------------|
| Folder not renamed | `RenameToYourUsername/` or `RenameToYourStudentID/` | Extract student ID from Java file header comments |
| Student ID vs Email ID confusion | `01400003/` vs `david.2024/` | Parse header to get correct identifier |
| Year prefix in folders | `2023-2024-chee.teo.2022/` | Normalize by extracting email ID portion |

### 2.2 Archive Issues
| Issue | Example | Resolution Strategy |
|-------|---------|---------------------|
| Files archived directly (no parent folder) | ZIP contains Q1/, Q2/, Q3/ directly | Detect and wrap in a folder based on ZIP filename or header parsing |
| Extra nesting | `folder/folder/Q1/...` | Recursively search for Q1, Q2, Q3 directories |

### 2.3 Content Issues
| Issue | Example | Resolution Strategy |
|-------|---------|---------------------|
| Missing header info | `* Name:` / `* Email ID:` empty | Flag as anomaly, attempt to use folder name |
| Wrong header format | Various comment styles | Use regex patterns to extract info |
| Missing files | No Q3.java | Score 0 for that question, continue grading |
| Extra/unexpected files | ShapeComparator.java, ShapeComparators.java | Allow helper classes, compile all |

### 2.4 Execution Issues
| Issue | Example | Resolution Strategy |
|-------|---------|---------------------|
| Compilation errors | Syntax errors like undeclared `i` | Score 0, log error, continue to next |
| Infinite loops | While(true) without exit | Enforce timeout (e.g., 10 seconds), terminate process |
| Runtime exceptions | NullPointerException, etc. | Catch, score 0 for that test case, continue |

---

## 3. System Architecture

### 3.1 High-Level Design

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           AUTO GRADING SYSTEM                           │
├─────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐          │
│  │   UI Layer      │  │   Core Engine   │  │   Output Layer  │          │
│  │  (Console/GUI)  │◄─┤   Controller    ├──►│  (Reports/CSV)  │          │
│  └────────┬────────┘  └────────┬────────┘  └─────────────────┘          │
│           │                    │                                        │
│           ▼                    ▼                                        │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    PROCESSING PIPELINE                          │   │
│  ├─────────────────────────────────────────────────────────────────┤   │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐         │   │
│  │  │ Extract  │─►│ Validate │─►│ Compile  │─►│ Execute  │         │   │
│  │  │ Module   │  │ Module   │  │ Module   │  │ Module   │         │   │
│  │  └──────────┘  └──────────┘  └──────────┘  └──────────┘         │   │
│  │       │              │             │             │               │   │
│  │       ▼              ▼             ▼             ▼               │   │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐         │   │
│  │  │ Identity │  │ Structure│  │ Compile  │  │  Test    │         │   │
│  │  │ Resolver │  │  Fixer   │  │  Runner  │  │ Runner   │         │   │
│  │  └──────────┘  └──────────┘  └──────────┘  └──────────┘         │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                     DATA MODELS                                  │   │
│  ├─────────────────────────────────────────────────────────────────┤   │
│  │  Student | Submission | Question | TestCase | GradeResult       │   │
│  │  ScoreSheet | Anomaly | Configuration                           │   │
│  └─────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
```

### 3.2 Package Structure

```
src/
├── com.is442.autograder/
│   ├── Main.java                     # Entry point
│   ├── controller/
│   │   ├── GradingController.java    # Orchestrates the grading workflow
│   │   └── ConfigController.java     # Handles configuration
│   │
│   ├── model/
│   │   ├── Student.java              # Student entity (name, email, orgId)
│   │   ├── Submission.java           # A student's submission bundle
│   │   ├── Question.java             # A single question (Q1a, Q1b, etc.)
│   │   ├── TestCase.java             # Individual test case result
│   │   ├── GradeResult.java          # Per-question grade
│   │   ├── ScoreSheet.java           # Aggregated results
│   │   └── Anomaly.java              # Detected issues/warnings
│   │
│   ├── service/
│   │   ├── extraction/
│   │   │   ├── ZipExtractor.java     # Handles ZIP extraction
│   │   │   └── IdentityResolver.java # Extracts student ID from headers
│   │   │
│   │   ├── validation/
│   │   │   ├── SubmissionValidator.java
│   │   │   ├── StructureValidator.java
│   │   │   └── StructureFixer.java   # Normalizes folder structure
│   │   │
│   │   ├── compilation/
│   │   │   └── JavaCompiler.java     # Invokes javac
│   │   │
│   │   ├── execution/
│   │   │   ├── TestExecutor.java     # Runs tests with timeout
│   │   │   ├── ProcessManager.java   # Manages external processes
│   │   │   └── OutputParser.java     # Parses test output for scores
│   │   │
│   │   └── grading/
│   │       ├── GradingService.java   # Core grading logic
│   │       └── ScoreCalculator.java  # Aggregates scores
│   │
│   ├── reporting/
│   │   ├── CSVExporter.java          # Exports to CSV format
│   │   ├── AnomalyReporter.java      # Generates anomaly report
│   │   └── DetailedReporter.java     # Generates detailed reports
│   │
│   ├── ui/
│   │   ├── ConsoleUI.java            # Console interface
│   │   ├── GUIApplication.java       # (Optional) JavaFX/Swing GUI
│   │   └── ProgressTracker.java      # Shows grading progress
│   │
│   └── util/
│       ├── FileUtils.java            # File operations
│       ├── ConfigLoader.java         # Load external config
│       └── Constants.java            # App constants
│
└── resources/
    └── config.properties             # Externalized configuration
```

---

## 4. Core Components Detailed Design

### 4.1 Extraction Module

**Purpose**: Extract ZIP archives and resolve student identity

**Classes**:
- `ZipExtractor` - Handles various ZIP formats, nested structures
- `IdentityResolver` - Parses Java file headers to extract Name/Email ID

**Header Parsing Strategy**:
```java
// Pattern to match various header formats:
// /* Name: John Doe */
// /** Name    : John Doe * Email ID: john.doe.2023 */
// * Name: John Doe
// * Email ID: john.doe.2023

Pattern NAME_PATTERN = Pattern.compile(
    "\\*\\s*Name\\s*:\\s*([^*\\n]+)", 
    Pattern.CASE_INSENSITIVE
);
Pattern EMAIL_PATTERN = Pattern.compile(
    "\\*\\s*Email\\s*ID\\s*:\\s*([^*\\n@]+)", 
    Pattern.CASE_INSENSITIVE
);
```

### 4.2 Validation Module

**Purpose**: Validate submission structure and fix common issues

**Validation Checks**:
1. ✅ Top-level folder exists (not just Q1/Q2/Q3 directly)
2. ✅ Expected question folders present (Q1, Q2, Q3)
3. ✅ Required Java files exist (Q1a.java, Q1b.java, Q2a.java, Q2b.java, Q3.java)
4. ✅ Header comments have Name and Email ID
5. ✅ Files compile successfully

**Structure Normalization**:
```java
public class StructureFixer {
    /**
     * Normalizes various folder structures to expected format:
     * {studentId}/
     *   ├── Q1/
     *   ├── Q2/
     *   └── Q3/
     */
    public Path normalize(Path extractedPath, String studentId) {
        // Handle: RenameToYourUsername -> studentId
        // Handle: Q1,Q2,Q3 directly -> wrap in studentId folder
        // Handle: extra nesting -> flatten
    }
}
```

### 4.3 Compilation Module

**Purpose**: Compile Java files and capture errors

**Process**:
```java
public class JavaCompiler {
    public CompilationResult compile(Path questionFolder, Path testerFile) {
        // 1. Copy tester file to question folder
        // 2. Run: javac *.java
        // 3. Capture stdout/stderr
        // 4. Return success/failure with error messages
    }
}
```

### 4.4 Execution Module

**Purpose**: Run tests with timeout protection

**Key Features**:
- Execute with configurable timeout (default: 10 seconds)
- Capture stdout for score parsing
- Handle infinite loops gracefully
- Parse output to extract scores

```java
public class TestExecutor {
    private static final int DEFAULT_TIMEOUT_SECONDS = 10;
    
    public ExecutionResult execute(Path classPath, String testerClassName) {
        ProcessBuilder pb = new ProcessBuilder("java", testerClassName);
        pb.directory(classPath.toFile());
        
        Process process = pb.start();
        boolean completed = process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        
        if (!completed) {
            process.destroyForcibly();
            return ExecutionResult.timeout();
        }
        
        String output = captureOutput(process);
        double score = parseScore(output);
        return ExecutionResult.success(score, output);
    }
}
```

### 4.5 Grading Service

**Purpose**: Orchestrate the entire grading workflow

```java
public class GradingService {
    public List<GradeResult> gradeSubmissions(
            Path submissionsDir, 
            Path testerFilesDir,
            Path scoreSheetPath) {
        
        List<Submission> submissions = extractSubmissions(submissionsDir);
        Map<String, Student> students = loadStudentInfo(scoreSheetPath);
        
        List<GradeResult> results = new ArrayList<>();
        
        for (Submission sub : submissions) {
            Student student = identifyStudent(sub, students);
            GradeResult result = gradeSubmission(sub, student, testerFilesDir);
            results.add(result);
        }
        
        return results;
    }
}
```

---

## 5. User Interface Options

### 5.1 Option A: Console Application (Recommended for MVP)

**Pros**:
- Faster to implement
- Easier to debug
- Satisfies project requirements
- Better for batch processing

**User Flow**:
```
=======================================================
       IS442 Auto Grading System v1.0
=======================================================

[1] Grade Submissions
[2] View Configuration
[3] Generate Report
[4] Exit

Select option: 1

Enter submissions folder path: ./student-submissions
Enter tester files folder path: ./Tester-Files
Enter scoresheet CSV path: ./IS442-ScoreSheet.csv

Processing...

[==========                    ] 33% | Grading chee.teo.2022...
[====================          ] 66% | Grading david.2024...
[==============================] 100% | Complete!

GRADING SUMMARY
-------------------------------------------------------
| Student          | Q1a | Q1b | Q2a | Q2b | Q3 | Total |
|------------------|-----|-----|-----|-----|-----|-------|
| chee.teo.2022    |  3  |  3  |  5  |  1  |  0  |  12   |
| david.2024       |  3  |  3  |  4  |  0  |  0  |  10   |
| ...              | ... | ... | ... | ... | ... |  ...  |
-------------------------------------------------------

ANOMALIES DETECTED: 3
  ⚠ fen.lai.2022 - Folder was 'RenameToYourStudentID'
  ⚠ jing.lim.2021 - Compilation error in Q1a.java
  ⚠ xing.yan.2023 - Q1a method returned empty result

Export results to CSV? [Y/n]: y
Results exported to: ./grading-results-2024-02-08.csv
```

### 5.2 Option B: JavaFX GUI Application

**Pros**:
- More user-friendly
- Visual progress tracking
- Easier file selection (file dialogs)

**Cons**:
- More development time
- Requires JavaFX dependency
- Overkill for batch processing

**Mockup**:
```
┌─────────────────────────────────────────────────────────────────────┐
│ IS442 Auto Grading System                                    [_][□][X]│
├─────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────┐                                │
│  │ Input Files                     │                                │
│  ├─────────────────────────────────┤                                │
│  │ Submissions: [./submissions    ][Browse]                        │
│  │ Testers:     [./Tester-Files   ][Browse]                        │
│  │ ScoreSheet:  [./ScoreSheet.csv ][Browse]                        │
│  └─────────────────────────────────┘                                │
│                                                                     │
│  [▶ Start Grading]                                                  │
│  ████████████████████████░░░░░░░░░░ 60% - Grading ping.lee.2023    │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────────┐│
│  │ Results                                           [Export CSV]  ││
│  ├──────────────────┬─────┬─────┬─────┬─────┬─────┬───────────────┤│
│  │ Student          │ Q1a │ Q1b │ Q2a │ Q2b │ Q3  │ Total         ││
│  ├──────────────────┼─────┼─────┼─────┼─────┼─────┼───────────────┤│
│  │ chee.teo.2022    │  3  │  3  │  5  │  1  │  0  │ 12            ││
│  │ david.2024       │  3  │  3  │  4  │  0  │  0  │ 10            ││
│  └──────────────────┴─────┴─────┴─────┴─────┴─────┴───────────────┘│
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────────┐│
│  │ ⚠ Anomalies (3)                                                 ││
│  ├─────────────────────────────────────────────────────────────────┤│
│  │ • fen.lai.2022 - Folder name not renamed from template          ││
│  │ • jing.lim.2021 - Compilation error in Q1a.java                 ││
│  └─────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────┘
```

### 5.3 Recommendation

**Start with Console Application**, then optionally add GUI wrapper:

1. **Phase 1**: Build robust core engine + console UI
2. **Phase 2**: (Optional) Add JavaFX GUI that calls the same core engine
3. **Benefit**: Separation of concerns, core logic is UI-agnostic

---

## 6. Configuration Management

### 6.1 Externalized Configuration (`config.properties`)

```properties
# Execution Settings
execution.timeout.seconds=10
execution.max.parallel.threads=4

# Validation Settings
validation.strict.mode=false
validation.allow.extra.files=true

# Question Configuration
questions.list=Q1a,Q1b,Q2a,Q2b,Q3
questions.max.scores=3,3,5,5,4
questions.testers=Q1aTester,Q1bTester,Q2aTester,Q2bTester,Q3Tester
questions.folders=Q1,Q1,Q2,Q2,Q3

# File Patterns
patterns.java.header.name=\\*\\s*Name\\s*:\\s*([^*\\n]+)
patterns.java.header.email=\\*\\s*Email\\s*ID\\s*:\\s*([^*\\n@]+)

# Output Settings
output.csv.format=scoresheet
output.include.anomalies=true
```

---

## 7. Implementation Phases

### Phase 1: Core Infrastructure (Week 1)
- [ ] Set up project structure with Maven/Gradle
- [ ] Implement data models (Student, Submission, Question, etc.)
- [ ] Implement ZipExtractor with nested folder handling
- [ ] Implement IdentityResolver with header parsing
- [ ] Write unit tests for extraction

### Phase 2: Validation & Compilation (Week 2)
- [ ] Implement StructureValidator
- [ ] Implement StructureFixer for common issues
- [ ] Implement JavaCompiler wrapper
- [ ] Implement error capture and reporting
- [ ] Write unit tests for validation

### Phase 3: Execution & Scoring (Week 3)
- [ ] Implement TestExecutor with timeout
- [ ] Implement OutputParser for score extraction
- [ ] Implement ScoreCalculator
- [ ] Implement GradingService orchestration
- [ ] Write integration tests

### Phase 4: Reporting & UI (Week 4)
- [ ] Implement CSVExporter (match provided format)
- [ ] Implement AnomalyReporter
- [ ] Implement ConsoleUI with progress tracking
- [ ] End-to-end testing with sample submissions
- [ ] Performance optimization

### Phase 5: Polish & Bonus Features (Week 5)
- [ ] Add detailed reporting
- [ ] Implement additional edge case handling
- [ ] Documentation and README
- [ ] Presentation preparation
- [ ] (Optional) GUI implementation

---

## 8. Bonus Features to Consider

### 8.1 Additional Edge Cases
- Handle BOMs (Byte Order Marks) in files
- Support different encoding (UTF-8, Latin-1)
- Handle submissions with package declarations
- Support alternative archive formats (.tar.gz, .rar)

### 8.2 Intelligence Features
- Detect if student submitted template folder (unmodified files)
- Warn if code identical to template (no work done)
- Basic similarity detection between submissions

### 8.3 Tester Generation
- Auto-generate tester variations with different inputs
- Parameterized test configuration

### 8.4 Enhanced Reporting
- HTML report with syntax-highlighted code snippets
- Summary statistics (average score, distribution)
- Common mistake analysis

---

## 9. Technology Stack

| Component | Technology | Rationale |
|-----------|------------|-----------|
| Language | Java 17+ | Project requirement |
| Build | Maven or Gradle | Dependency management |
| Testing | JUnit 5 | Industry standard |
| Console UI | ANSI colors, Progress bars | Better UX |
| GUI (Optional) | JavaFX 17 | Modern, maintained |
| Config | java.util.Properties | Simple, no dependencies |
| Logging | java.util.logging | Built-in, no dependencies |

---

## 10. Risk Mitigation

| Risk | Mitigation |
|------|------------|
| Infinite loops crash system | Process timeout + forcible termination |
| Malformed ZIP files | Try-catch with specific error handling |
| Missing student identity | Fallback to ZIP filename/folder name |
| Memory exhaustion (large cohorts) | Process submissions in batches |
| Different Java versions | Compile with backwards-compatible flags |

---

## 11. Testing Strategy

### Unit Tests
- `ZipExtractorTest` - Various ZIP structures
- `IdentityResolverTest` - Different header formats
- `StructureValidatorTest` - Valid and invalid structures
- `JavaCompilerTest` - Compilation success/failure
- `TestExecutorTest` - Normal execution, timeout, errors

### Integration Tests
- End-to-end grading with provided sample submissions
- Verify scores match `groundtruth.txt`

### Manual Testing
- UI usability testing
- Edge case scenarios

---

## 12. Deliverables Checklist

- [ ] Source code (well-documented Java files)
- [ ] README.md with setup and usage instructions
- [ ] Config files (externalized configuration)
- [ ] Unit tests
- [ ] Presentation slides covering:
  - [ ] OOP design / system architecture
  - [ ] Libraries used and rationale
  - [ ] Algorithms explored
  - [ ] Live demo
- [ ] Demo video/walkthrough

---

## 13. Getting Started

### Prerequisites
- Java 17+
- Maven/Gradle

### Quick Start
```bash
# Clone repository
git clone [repo-url]
cd is442-java-auto-grader

# Build project
mvn clean install

# Run grading
java -jar target/auto-grader.jar \
  --submissions ./is442-project-materials/student-submission \
  --testers ./is442-project-materials/Tester-Files \
  --scoresheet ./is442-project-materials/IS442-ScoreSheet.csv \
  --output ./results
```

---

## Appendix A: Sample Code Snippets

### A.1 Header Parser
```java
public class IdentityResolver {
    private static final Pattern NAME_PATTERN = Pattern.compile(
        "\\*\\s*Name\\s*:\\s*([^\\*\\n]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "\\*\\s*Email\\s*ID\\s*:\\s*([\\w.]+)", Pattern.CASE_INSENSITIVE);
    
    public Optional<StudentIdentity> resolveFromFile(Path javaFile) throws IOException {
        String content = Files.readString(javaFile);
        
        Matcher nameMatcher = NAME_PATTERN.matcher(content);
        Matcher emailMatcher = EMAIL_PATTERN.matcher(content);
        
        if (nameMatcher.find() && emailMatcher.find()) {
            String name = nameMatcher.group(1).trim();
            String email = emailMatcher.group(1).trim();
            
            if (!name.isEmpty() && !email.isEmpty()) {
                return Optional.of(new StudentIdentity(name, email));
            }
        }
        
        return Optional.empty();
    }
}
```

### A.2 Process Executor with Timeout
```java
public class TestExecutor {
    private final int timeoutSeconds;
    
    public ExecutionResult execute(Path workDir, String className) {
        try {
            ProcessBuilder pb = new ProcessBuilder("java", className);
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            
            Thread reader = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    br.lines().forEach(line -> output.append(line).append("\n"));
                } catch (IOException e) {
                    output.append("Error reading output: ").append(e.getMessage());
                }
            });
            
            reader.start();
            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            
            if (!completed) {
                process.destroyForcibly();
                reader.interrupt();
                return ExecutionResult.timeout("Execution exceeded " + timeoutSeconds + "s");
            }
            
            reader.join(1000);
            int exitCode = process.exitValue();
            
            return new ExecutionResult(
                exitCode == 0 ? Status.SUCCESS : Status.RUNTIME_ERROR,
                output.toString(),
                exitCode
            );
            
        } catch (Exception e) {
            return ExecutionResult.error(e.getMessage());
        }
    }
}
```

---

## 14. Input Sanitization & Security

Input sanitization is **critical** for this system since we're processing untrusted student code and user input.

### 14.1 Console Input Sanitization

All user input from the console must be validated before use:

#### Path Input Validation

```java
public class InputValidator {
    
    /**
     * Validates and sanitizes file/folder path input from console
     */
    public static Path validatePath(String input, PathType type) throws InvalidInputException {
        // 1. Null/empty check
        if (input == null || input.trim().isEmpty()) {
            throw new InvalidInputException("Path cannot be empty");
        }
        
        String sanitized = input.trim();
        
        // 2. Remove potentially dangerous characters
        // Prevent command injection via paths like "; rm -rf /"
        if (containsDangerousChars(sanitized)) {
            throw new InvalidInputException("Path contains invalid characters");
        }
        
        // 3. Normalize and resolve path
        Path path = Paths.get(sanitized).normalize().toAbsolutePath();
        
        // 4. Prevent path traversal attacks (../../etc/passwd)
        Path baseDir = getWorkingDirectory();
        if (!path.startsWith(baseDir) && !isAbsolutePathAllowed()) {
            throw new InvalidInputException("Path traversal not allowed");
        }
        
        // 5. Existence check
        if (type == PathType.EXISTING_FILE && !Files.isRegularFile(path)) {
            throw new InvalidInputException("File does not exist: " + path);
        }
        if (type == PathType.EXISTING_DIRECTORY && !Files.isDirectory(path)) {
            throw new InvalidInputException("Directory does not exist: " + path);
        }
        
        return path;
    }
    
    private static boolean containsDangerousChars(String input) {
        // Characters that could enable command injection
        String[] dangerous = {";", "|", "&", "$", "`", "(", ")", "{", "}", "[", "]", 
                              "<", ">", "!", "\n", "\r", "\0"};
        for (String c : dangerous) {
            if (input.contains(c)) return true;
        }
        return false;
    }
}
```

#### Menu Option Validation

```java
public class MenuValidator {
    
    public static int validateMenuChoice(String input, int min, int max) 
            throws InvalidInputException {
        // 1. Trim whitespace
        String trimmed = input.trim();
        
        // 2. Parse as integer
        try {
            int choice = Integer.parseInt(trimmed);
            
            // 3. Range check
            if (choice < min || choice > max) {
                throw new InvalidInputException(
                    String.format("Please enter a number between %d and %d", min, max));
            }
            
            return choice;
        } catch (NumberFormatException e) {
            throw new InvalidInputException("Invalid input. Please enter a number.");
        }
    }
    
    public static boolean validateYesNo(String input) throws InvalidInputException {
        String trimmed = input.trim().toLowerCase();
        
        if (trimmed.equals("y") || trimmed.equals("yes")) return true;
        if (trimmed.equals("n") || trimmed.equals("no")) return false;
        
        throw new InvalidInputException("Please enter 'y' or 'n'");
    }
}
```

### 14.2 Student File Content Sanitization

Student-submitted Java files may contain malicious or problematic content:

#### Issue Categories & Mitigations

| Risk | Description | Mitigation |
|------|-------------|------------|
| **Zip Bombs** | Compressed files that expand to huge sizes | Limit extraction size (e.g., 100MB max) |
| **Path Traversal in ZIP** | Entries like `../../etc/passwd` | Validate all ZIP entry names |
| **Symlinks** | Symbolic links to sensitive files | Reject symlinks in archives |
| **Malicious Code Execution** | `Runtime.exec()`, `ProcessBuilder` | Run in sandbox (optional) or document as known limitation |
| **Resource Exhaustion** | Infinite loops, memory allocation | Process timeout + memory limits |
| **File System Access** | Reading/writing arbitrary files | Run tests in isolated temp directory |
| **Network Access** | Making external HTTP calls | (Optional) Run without network access |

#### Safe ZIP Extraction

```java
public class SafeZipExtractor {
    private static final long MAX_TOTAL_SIZE = 100 * 1024 * 1024; // 100 MB
    private static final long MAX_ENTRIES = 1000;
    private static final int MAX_PATH_LENGTH = 255;
    
    public Path extractSafely(Path zipFile, Path targetDir) throws SecurityException, IOException {
        long totalSize = 0;
        int entryCount = 0;
        
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            
            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;
                
                // 1. Limit number of entries (zip bomb protection)
                if (entryCount > MAX_ENTRIES) {
                    throw new SecurityException("Too many entries in archive");
                }
                
                // 2. Validate entry name
                String entryName = entry.getName();
                validateEntryName(entryName, targetDir);
                
                // 3. Reject symbolic links
                if (entry.isDirectory() == false && Files.isSymbolicLink(targetDir.resolve(entryName))) {
                    throw new SecurityException("Symbolic links not allowed: " + entryName);
                }
                
                // 4. Check cumulative size (zip bomb protection)
                totalSize += entry.getSize();
                if (totalSize > MAX_TOTAL_SIZE) {
                    throw new SecurityException("Archive too large (exceeds " + MAX_TOTAL_SIZE + " bytes)");
                }
                
                // 5. Extract safely
                Path destPath = targetDir.resolve(entryName).normalize();
                
                // Double-check path traversal after resolution
                if (!destPath.startsWith(targetDir)) {
                    throw new SecurityException("Path traversal detected: " + entryName);
                }
                
                if (entry.isDirectory()) {
                    Files.createDirectories(destPath);
                } else {
                    Files.createDirectories(destPath.getParent());
                    Files.copy(zis, destPath, StandardCopyOption.REPLACE_EXISTING);
                }
                
                zis.closeEntry();
            }
        }
        
        return targetDir;
    }
    
    private void validateEntryName(String name, Path targetDir) throws SecurityException {
        // Check for path traversal patterns
        if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
            throw new SecurityException("Invalid entry name: " + name);
        }
        
        // Check path length
        if (name.length() > MAX_PATH_LENGTH) {
            throw new SecurityException("Entry name too long: " + name);
        }
        
        // Check for null bytes (can cause issues in file names)
        if (name.contains("\0")) {
            throw new SecurityException("Null byte in entry name");
        }
    }
}
```

#### Java File Content Validation

```java
public class JavaFileValidator {
    
    // Patterns that might indicate problematic code
    private static final List<Pattern> SUSPICIOUS_PATTERNS = Arrays.asList(
        Pattern.compile("Runtime\\.getRuntime\\(\\)"),
        Pattern.compile("ProcessBuilder"),
        Pattern.compile("System\\.exit\\("),
        Pattern.compile("Files\\.delete"),
        Pattern.compile("FileWriter|FileOutputStream"),
        Pattern.compile("new\\s+Socket\\("),
        Pattern.compile("URL\\s*\\("),
        Pattern.compile("HttpURLConnection"),
        Pattern.compile("Class\\.forName"),
        Pattern.compile("Method\\.invoke"),
        Pattern.compile("SecurityManager")
    );
    
    public ValidationResult validateJavaFile(Path javaFile) throws IOException {
        String content = Files.readString(javaFile);
        List<String> warnings = new ArrayList<>();
        
        // Check file encoding (should be valid UTF-8)
        if (!isValidUtf8(javaFile)) {
            warnings.add("File may have encoding issues");
        }
        
        // Check for BOM (Byte Order Mark)
        if (content.startsWith("\uFEFF")) {
            warnings.add("File contains BOM - may cause compilation issues");
        }
        
        // Check for suspicious patterns (informational, not blocking)
        for (Pattern pattern : SUSPICIOUS_PATTERNS) {
            if (pattern.matcher(content).find()) {
                warnings.add("Contains potentially unsafe code: " + pattern.pattern());
            }
        }
        
        // Check for package declarations (might cause compilation issues)
        if (content.contains("package ")) {
            warnings.add("Contains package declaration - may need handling");
        }
        
        return new ValidationResult(warnings);
    }
}
```

### 14.3 CSV Input Sanitization

The scoresheet CSV must be validated:

```java
public class CSVValidator {
    
    public List<Student> parseScoreSheet(Path csvPath) throws IOException, InvalidCSVException {
        List<Student> students = new ArrayList<>();
        
        try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
            String headerLine = reader.readLine();
            validateHeader(headerLine);
            
            String line;
            int lineNum = 1;
            
            while ((line = reader.readLine()) != null) {
                lineNum++;
                
                // Skip empty lines
                if (line.trim().isEmpty()) continue;
                
                try {
                    Student student = parseLine(line, lineNum);
                    
                    // Validate student data
                    validateStudent(student);
                    
                    students.add(student);
                } catch (Exception e) {
                    throw new InvalidCSVException("Error on line " + lineNum + ": " + e.getMessage());
                }
            }
        }
        
        return students;
    }
    
    private void validateStudent(Student student) throws InvalidCSVException {
        // Validate email format
        if (!student.getEmail().matches("^[\\w.]+@[\\w.]+\\.[a-z]+$")) {
            throw new InvalidCSVException("Invalid email format: " + student.getEmail());
        }
        
        // Validate username (alphanumeric + dots)
        if (!student.getUsername().matches("^[\\w.]+$")) {
            throw new InvalidCSVException("Invalid username: " + student.getUsername());
        }
    }
}
```

### 14.4 Execution Sandbox (Advanced)

For maximum security, run student code in an isolated environment:

| Approach | Complexity | Security Level | Recommended |
|----------|------------|----------------|-------------|
| **Process with timeout** | Low | Basic | ✅ Yes (MVP) |
| **Separate JVM with SecurityManager** | Medium | Good | Consider |
| **Docker container** | Medium | Excellent | Bonus feature |
| **chroot/jail** | High | Excellent | Overkill |

**MVP Approach - Process Isolation:**
```java
ProcessBuilder pb = new ProcessBuilder("java", "-Djava.security.manager", className);
pb.directory(isolatedTempDir.toFile());
pb.environment().clear(); // Remove env variables
pb.redirectErrorStream(true);
```

---

## 15. Intelligence Features - Detailed Approaches

### 15.1 Template Submission Detection

**Goal:** Detect if a student submitted the original template without modifications.

**Approach 1: File Hash Comparison**
```java
public class TemplateDetector {
    private Map<String, String> templateHashes = new HashMap<>();
    
    public void loadTemplateHashes(Path templateDir) throws IOException {
        Files.walk(templateDir)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(p -> {
                String hash = computeHash(p);
                templateHashes.put(p.getFileName().toString(), hash);
            });
    }
    
    public boolean isUnmodifiedTemplate(Path studentFile) throws IOException {
        String filename = studentFile.getFileName().toString();
        String studentHash = computeHash(studentFile);
        String templateHash = templateHashes.get(filename);
        
        return templateHash != null && templateHash.equals(studentHash);
    }
    
    private String computeHash(Path file) throws IOException {
        byte[] content = Files.readAllBytes(file);
        return DigestUtils.sha256Hex(content); // Or use MessageDigest
    }
}
```

**Approach 2: Structural Comparison (ignoring whitespace/comments)**
```java
public double computeSimilarityToTemplate(Path studentFile, Path templateFile) {
    // 1. Extract only code (strip comments, normalize whitespace)
    String studentCode = normalizeCode(Files.readString(studentFile));
    String templateCode = normalizeCode(Files.readString(templateFile));
    
    // 2. Compare
    if (studentCode.equals(templateCode)) {
        return 1.0; // Identical
    }
    
    // 3. Compute Levenshtein distance ratio
    int distance = levenshteinDistance(studentCode, templateCode);
    int maxLen = Math.max(studentCode.length(), templateCode.length());
    
    return 1.0 - ((double) distance / maxLen);
}
```

---

### 15.2 Basic Plagiarism Detection

**Goal:** Detect similar code submissions between students.

#### Approach 1: Token-Based Fingerprinting (Recommended)

This is how tools like **Stanford's MOSS** and **JPlag** work.

**How it works:**
1. Tokenize source code (convert to abstract tokens)
2. Generate n-gram fingerprints
3. Compare fingerprints between submissions

```java
public class CodeFingerprinter {
    private static final int NGRAM_SIZE = 5;
    
    /**
     * Convert code to tokens, ignoring identifiers/literals
     * "int x = 5;" -> [TYPE, IDENT, ASSIGN, NUM, SEMI]
     */
    public List<String> tokenize(String code) {
        List<String> tokens = new ArrayList<>();
        
        // Simple tokenization (production: use a real Java tokenizer)
        String[] patterns = {
            "\\bclass\\b", "\\bpublic\\b", "\\bprivate\\b", "\\bstatic\\b",
            "\\bvoid\\b", "\\bint\\b", "\\bString\\b", "\\breturn\\b",
            "\\bif\\b", "\\belse\\b", "\\bfor\\b", "\\bwhile\\b",
            "\\{", "\\}", "\\(", "\\)", ";", "=", "\\+", "-", "\\*"
        };
        
        // Simplified - in practice use javax.tools or JavaParser
        for (String pattern : patterns) {
            if (code.matches(".*" + pattern + ".*")) {
                tokens.add(pattern.replaceAll("\\\\b", ""));
            }
        }
        
        return tokens;
    }
    
    /**
     * Generate rolling hash fingerprints using Winnowing algorithm
     */
    public Set<Long> generateFingerprints(List<String> tokens) {
        Set<Long> fingerprints = new HashSet<>();
        
        for (int i = 0; i <= tokens.size() - NGRAM_SIZE; i++) {
            List<String> ngram = tokens.subList(i, i + NGRAM_SIZE);
            long hash = computeHash(ngram);
            fingerprints.add(hash);
        }
        
        return fingerprints;
    }
    
    /**
     * Compare two submissions and return similarity percentage
     */
    public double compareSubmissions(Path file1, Path file2) throws IOException {
        Set<Long> fp1 = generateFingerprints(tokenize(Files.readString(file1)));
        Set<Long> fp2 = generateFingerprints(tokenize(Files.readString(file2)));
        
        // Jaccard similarity
        Set<Long> intersection = new HashSet<>(fp1);
        intersection.retainAll(fp2);
        
        Set<Long> union = new HashSet<>(fp1);
        union.addAll(fp2);
        
        if (union.isEmpty()) return 0.0;
        
        return (double) intersection.size() / union.size();
    }
}
```

#### Approach 2: AST (Abstract Syntax Tree) Comparison

More robust but more complex. Uses Java parser libraries.

**Libraries:**
- [JavaParser](https://javaparser.org/) - Parse Java source to AST
- [Eclipse JDT](https://www.eclipse.org/jdt/) - Full Java tooling

```java
// Using JavaParser library
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;

public class ASTComparator {
    
    public double compareBySructure(Path file1, Path file2) throws IOException {
        CompilationUnit cu1 = JavaParser.parse(file1);
        CompilationUnit cu2 = JavaParser.parse(file2);
        
        // Extract method signatures, class structure, control flow
        List<String> structure1 = extractStructure(cu1);
        List<String> structure2 = extractStructure(cu2);
        
        // Compare structures
        return computeSimilarity(structure1, structure2);
    }
    
    private List<String> extractStructure(CompilationUnit cu) {
        List<String> structure = new ArrayList<>();
        
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            structure.add("METHOD:" + method.getSignature());
            // Add control flow nodes (if, for, while)
            method.findAll(IfStmt.class).forEach(s -> structure.add("IF"));
            method.findAll(ForStmt.class).forEach(s -> structure.add("FOR"));
            method.findAll(WhileStmt.class).forEach(s -> structure.add("WHILE"));
        });
        
        return structure;
    }
}
```

#### Existing Tools (External Integration)

| Tool | Type | Integration Method | Pros | Cons |
|------|------|-------------------|------|------|
| **[MOSS](https://theory.stanford.edu/~aiken/moss/)** | Web Service | Email submission | Industry standard, accurate | External dependency, queued |
| **[JPlag](https://github.com/jplag/jplag)** | Java Library | Direct JAR | Open source, runs locally | Need to integrate |
| **[Codequiry](https://codequiry.com/)** | API | REST API | Easy integration | Paid service |
| **[Copydetect](https://github.com/blingenf/copydetect)** | Python | CLI | Academic focused | Python dependency |

**JPlag Integration Example:**
```java
// JPlag can be used as a library
// Add dependency: de.jplag:jplag:4.0.0

public void runJPlag(Path submissionsDir, Path reportDir) throws Exception {
    JPlagOptions options = new JPlagOptions(
        submissionsDir.toString(),
        Language.JAVA
    );
    options.setMinimumTokenMatch(9);
    
    JPlag jplag = new JPlag(options);
    JPlagResult result = jplag.run();
    
    // Generate HTML report
    ReportWriter.writeReport(result, reportDir);
}
```

---

### 15.3 Common Mistake Analysis

**Goal:** Identify patterns in errors across submissions to help instructors.

```java
public class MistakeAnalyzer {
    
    private Map<String, Integer> compilationErrors = new HashMap<>();
    private Map<String, Integer> runtimeErrors = new HashMap<>();
    private Map<String, Integer> testFailures = new HashMap<>();
    
    public void recordCompilationError(String errorMessage) {
        // Normalize error (remove line numbers, filenames)
        String normalized = normalizeError(errorMessage);
        compilationErrors.merge(normalized, 1, Integer::sum);
    }
    
    public void recordTestFailure(String question, String testCase, String expected, String actual) {
        String key = question + ":" + testCase;
        testFailures.merge(key, 1, Integer::sum);
    }
    
    public MistakeReport generateReport() {
        MistakeReport report = new MistakeReport();
        
        // Top 5 compilation errors
        report.setTopCompilationErrors(
            compilationErrors.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toList())
        );
        
        // Most failed test cases
        report.setMostFailedTests(
            testFailures.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toList())
        );
        
        return report;
    }
}
```

**Sample Output:**
```
COMMON MISTAKES REPORT
======================

TOP COMPILATION ERRORS:
1. "cannot find symbol: variable i" (12 students)
2. "incompatible types: String cannot be converted to int" (8 students)
3. "missing return statement" (5 students)

MOST FAILED TEST CASES:
1. Q2a Test 3: Edge case with empty input (18/25 failed)
2. Q1b Test 2: Case sensitivity handling (15/25 failed)
3. Q3 Test 1: Null comparator handling (10/25 failed)

RECOMMENDATIONS:
- Consider reviewing: variable declaration scope
- Consider reviewing: ArrayList methods
```

---

### 15.4 Feature Comparison Summary

| Feature | Implementation Effort | Value for Instructors | Recommendation |
|---------|----------------------|----------------------|----------------|
| Template Detection (hash) | Low (2 hours) | High | ✅ Implement |
| Template Detection (structural) | Medium (4 hours) | High | ✅ Implement |
| Plagiarism (fingerprint) | Medium (8 hours) | Very High | ✅ Implement |
| Plagiarism (AST) | High (16 hours) | Very High | Optional |
| JPlag Integration | Medium (4 hours) | Excellent | ✅ Consider |
| Common Mistake Analysis | Low (4 hours) | High | ✅ Implement |

---

## 16. Updated Package Structure

With new components:

```
src/
├── com.is442.autograder/
│   ├── ...
│   ├── security/
│   │   ├── InputValidator.java       # Console input validation
│   │   ├── MenuValidator.java        # Menu choice validation
│   │   ├── SafeZipExtractor.java     # Secure ZIP extraction
│   │   ├── JavaFileValidator.java    # File content checks
│   │   └── CSVValidator.java         # CSV input validation
│   │
│   ├── intelligence/
│   │   ├── TemplateDetector.java     # Unmodified template detection
│   │   ├── CodeFingerprinter.java    # Plagiarism fingerprinting
│   │   ├── SimilarityCalculator.java # Submission comparison
│   │   ├── MistakeAnalyzer.java      # Common mistake patterns
│   │   └── JPlagIntegration.java     # (Optional) JPlag wrapper
│   │
│   └── ...
```

---

## 17. Updated Technology Stack

| Component | Technology | Rationale |
|-----------|------------|-----------|
| Java Parser | [JavaParser 3.x](https://javaparser.org/) | AST-based analysis for plagiarism detection |
| Hashing | `java.security.MessageDigest` | SHA-256 for file fingerprinting |
| Similarity | Custom or Apache Commons Text | Levenshtein/Jaccard algorithms |
| Plagiarism (optional) | [JPlag](https://github.com/jplag/jplag) | Industry-standard tool |

**Maven Dependencies:**
```xml
<!-- JavaParser for AST analysis -->
<dependency>
    <groupId>com.github.javaparser</groupId>
    <artifactId>javaparser-core</artifactId>
    <version>3.25.5</version>
</dependency>

<!-- Apache Commons Text for string similarity -->
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-text</artifactId>
    <version>1.11.0</version>
</dependency>

<!-- Optional: JPlag for plagiarism detection -->
<dependency>
    <groupId>de.jplag</groupId>
    <artifactId>jplag</artifactId>
    <version>4.3.0</version>
</dependency>
```

---

*Last Updated: February 8, 2026*

# IS442 Auto-Grader Test Cases Documentation

**Last Updated:** February 19, 2026  
**Total Test Submissions:** 18 (6 original + 12 generated)

---

## Original Test Cases (6)

These are pre-existing student submissions used for baseline testing. All include complete files with dependencies.

| Student ID | Description |
|------------|-------------|
| `ping.lee.2023` | Perfect submission (reference implementation) |
| `xing.yan.2023` | Contains infinite loop timeout in Q2a |
| `chee.teo.2022` | Standard submission |
| `aaron.lim.2023` | Standard submission |
| `john.doe.2022` | Standard submission |
| `david.2024` | Standard submission |

---

## Generated Test Cases (12)

### 1. alice.wong.2024 - EXTRA_NESTING

**Student:** Alice Wong (alice.wong.2024)

**Edge Case:** Code is buried in deep folder structure `extra/nesting/levels/alice.wong.2024/` instead of at the root level.

**Autograder Handling:** Uses recursive `find` command to locate Q1, Q2, Q3 folders at any depth. Searches the entire ZIP structure instead of assuming fixed paths.

**Code Quality:** All code is fully functional and correct.

---

### 2. bob.tan.2025 - MISSING_QUESTION_FOLDER

**Student:** Bob Tan (bob.tan.2025)

**Edge Case:** The Q2 folder is completely missing from the submission. Only Q1 and Q3 were submitted.

**Autograder Handling:** Grades available questions normally. Missing questions automatically receive 0 points. No crashes or errors - gracefully handles partial submissions.

**Code Quality:** Q1 and Q3 code is correct.

---

### 3. charlie.lim.2023 - MISSING_HEADER

**Student:** Charlie Lim (charlie.lim.2023)

**Edge Case:** All Java files have NO header comments (no Name, no Email ID, no documentation).

**Autograder Handling:** Does not require headers for grading. Focuses purely on code functionality and correctness. Headers are treated as optional.

**Code Quality:** All implementations are fully functional despite missing documentation.

---

### 4. diana.ng.2022 - INCOMPLETE_HEADER

**Student:** Diana Ng (diana.ng.2022)

**Edge Case:** Header comments are incomplete:
- Q1a: Has Name but Email ID is empty
- Q1b: Has Email ID but Name is empty  
- Q2a: Name field is blank
- Q2b: Email ID field is blank

**Autograder Handling:** Does not validate header completeness. Grades based on code functionality only.

**Code Quality:** All implementations are correct.

---

### 5. evan.koh.2024 - COMPILATION_ERROR

**Student:** Evan Koh (evan.koh.2024)

**Edge Case:** Code contains syntax errors:
- Q1a: Missing closing brace `}`
- Q1b: Unclosed string literal

**Autograder Handling:** Attempts to compile each question independently. Compilation failures result in 0 points for that question only. Other questions (Q2, Q3) are compiled and graded normally. Errors are isolated and don't affect other questions.

**Code Quality:** Q1 won't compile. Q2 and Q3 are syntactically correct.

---

### 6. fiona.chen.2023 - EXECUTION_TIMEOUT 

**Student:** Fiona Chen (fiona.chen.2023)

**Edge Case:** Q1a contains an infinite loop (forgot `i++` in while loop). Code will hang indefinitely.

**Autograder Handling:** Implements 10-second timeout on ALL test case executions. If a test doesn't complete within 10 seconds, the process is forcibly terminated and that test case receives 0 points. Prevents the autograder from freezing. Other questions execute normally.

**Code Quality:** Q1a has infinite loop bug. All other questions are correct.

**CRITICAL:** This proves the timeout protection mechanism works.

---

### 7. george.low.2025 - RUNTIME_ERROR

**Student:** George Low (george.low.2025)

**Edge Case:** Code throws runtime exceptions:
- Q1a: Throws `RuntimeException` instead of returning results
- Q1b: ArrayIndexOutOfBoundsException (creates empty array, then accesses it)

**Autograder Handling:** Wraps all test executions in try-catch blocks. Runtime exceptions are caught, logged, and that specific test case receives 0 points. Exception doesn't crash the autograder. May award partial credit if some test cases pass before the exception.

**Code Quality:** Q1 throws exceptions. Q2 and Q3 are correct.

---

### 8. harny.goh.2023 - SPELLING_ERROR

**Student:** Harry Goh (harry.goh.2023)

**Edge Case:** Folder name has typo: `harny.goh.2023` instead of `harry.goh.2023`. Headers correctly say "Harry Goh".

**Autograder Handling:** Matches submissions based on the scoresheet (IS442-ScoreSheet.csv) which contains `harry.goh.2023`. Uses fuzzy matching or processes all submissions regardless of naming discrepancies. Logs the mismatch but continues grading.

**Code Quality:** All code is correct.

---

### 9. iris.lee.2022 - FOLDER_NOT_RENAMED

**Student:** Iris Lee (iris.lee.2022)

**Edge Case:** Top-level folder is still named `RenameToYourUsername` (template default). Student forgot to rename it to their email ID.

**Autograder Handling:** Does not rely on outer folder names. Uses recursive search to find Q1, Q2, Q3 folders and matches to scoresheet entries. Robust folder discovery regardless of naming.

**Code Quality:** All code is correct with proper headers.

---

### 10. john.smith.2023 - MIXED_ANOMALIES 

**Student:** John Smith (john.smith.2023)

**Edge Case:** Multiple catastrophic failures:
- Folder named `RenameMe` (not renamed)
- Q2 folder completely missing
- Q1a: No header + unclosed string (compilation error)
- Q1b: File doesn't exist
- Q3: Incomplete header + infinite loop in sorting logic

**Autograder Handling:** Handles each issue independently:
- Finds code despite wrong folder name (recursive search)
- Missing Q2 → automatic 0 points
- Q1a compilation error → 0 points
- Missing Q1b → 0 points
- Q3 infinite loop → timeout after 10 seconds → 0 points
- System doesn't crash or hang despite multiple failures

**Code Quality:** Intentionally broken to test worst-case resilience.

**VALIDATION:** Proves autograder can handle completely broken submissions without crashing.

---

### 11. kelly.ng.2024 - MISSING_Q2_DEPENDENCIES

**Student:** Kelly Ng (kelly.ng.2024)

**Edge Case:** Q2 folder contains Java source files (Q2a.java, Q2b.java) but missing required dependencies:
- `DataException.class` (required for compilation)
- `persons.txt`, `students.txt` (data files needed for execution)

**Autograder Handling:** 
- Detects missing DataException.class before compilation
- Auto-copies DataException.class from template folder to Q2 directory
- Auto-copies persons.txt and students.txt data files
- Proceeds with compilation and grading normally
- Student not penalized for missing infrastructure files

**Code Quality:** All Q2 code is correct and functional once dependencies are provided.

---

### 12. leo.tan.2025 - MISSING_Q3_DEPENDENCIES

**Student:** Leo Tan (leo.tan.2025)

**Edge Case:** Q3 folder contains Java source files (Q3.java, ShapeComparator.java) but missing required class files:
- `Shape.class` (interface)
- `Circle.class`, `Rectangle.class` (implementing classes)

**Autograder Handling:**
- Detects missing Shape-related class files before compilation
- Auto-copies Shape.class, Circle.class, Rectangle.class from template folder to Q3 directory
- Proceeds with compilation and grading normally
- Student not penalized for missing interface/class files

**Code Quality:** All Q3 code is correct and functional once dependencies are provided.

---



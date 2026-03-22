# Test Case Generation — WIP & Future Improvements

This document tracks planned improvements to the AI-powered test case generation module based on stakeholder feedback. Items are grouped by backend and frontend changes.

---

# Backend Changes

## B1. Structured LLM Output

**Current state:** The LLM returns raw Java code as free-form text. There is no schema enforcement, so the AI can produce syntax errors, wrong brace counts, or malformed code. A brace-balancing post-processor exists as a safety net, but it cannot catch semantic errors (e.g. wrong expected values, `System.out(` instead of `System.out.println(`).

**Proposed change:** Switch to structured output (e.g. JSON schema) where the LLM returns test cases as structured data:
```json
{
  "testCases": [
    {
      "description": "Empty list edge case",
      "setup": "ArrayList<String> inputs = new ArrayList<>();",
      "methodCall": "getIsogramWords(inputs)",
      "expected": "[]",
      "weight": 1.0,
      "comment": "// Edge case: empty input should return empty list"
    }
  ]
}
```
The backend would then assemble the Java code from the structured data, eliminating syntax errors entirely.

**Impact:** Eliminates compile errors from AI output. Enables the frontend to parse and display individual test cases with rich metadata. Makes weight assignment more precise (one weight per structured test case rather than regex-matched placeholders).

**Affected files:** `ClaudeApiClient.java`, `TestGenerationService.java`, `TesterFileWriter.java`

---

## B2. PDF Parser — Image Support

**Current state:** The PDF parser uses Apache PDFBox's `PDFTextStripper`, which extracts **text only**. It cannot read images, diagrams, tables rendered as images, or any non-text content embedded in the PDF.

**Concern:** If the exam PDF contains questions with diagrams, UML class diagrams, or visual examples (common for Q3's Shape/Circle/Rectangle), the AI receives no information about those visuals.

**Proposed options:**
1. **OCR integration** — Add an OCR layer (e.g. Tesseract via tess4j) to extract text from images in the PDF.
2. **Multimodal LLM** — Send the PDF pages as images directly to a vision-capable model (e.g. Claude's vision API) instead of extracting text first.
3. **Manual context supplement** — Allow the professor to paste additional context (e.g. class diagram descriptions) that the AI can't extract from the PDF.

**Investigation needed:** Check what the current exam PDF actually contains — if questions are purely text-based, this may not be a pressing issue. If diagrams are present and critical to understanding the question, option 2 (multimodal) would be the most robust solution.

**Affected files:** `ExamPdfParser.java`, `ClaudeApiClient.java`

---

## B3. AI-Inferred Test Case Count

**Current state:** The professor manually specifies how many test cases to generate per question (default: 3). This is a guess — some questions may need more edge cases than others.

**Proposed change:** Let the AI analyze the question, existing tester code, and student code to recommend how many test cases are appropriate and what topics they should cover. For example:
- Q1a (isogram check): 5 cases recommended — empty string, single char, all unique, mixed case, Unicode
- Q2b (file + exception): 4 cases recommended — valid course, invalid course (DataException), missing file, tie-breaking

The professor would see the AI's recommendation and adjust before generation. This could be a lightweight pre-generation API call that returns suggestions without generating full code.

**Affected files:** `ClaudeApiClient.java` (new prompt), `GenerationController.java` (new endpoint), `TestGenerationService.java`

---

## B4. Auto-Inferred Configuration (Reduce Hardcoding)

**Current state:** `config.properties` requires manual setup of question IDs, folder mappings, tester class names, max scores, and dependency files. This is backend-focused and requires the professor to understand the config format.

**Proposed change:** Infer configuration automatically from the exam paper and sample files:
- **Question list:** Parse the exam PDF for question headings (Q1a, Q1b, Q2a, etc.)
- **Folder structure:** Scan `RenameToYourUsername/` to discover subfolders (Q1, Q2, Q3) and map questions to folders
- **Tester class names:** Scan tester files directory for `*Tester.java` files and match to questions by naming convention
- **Dependency files:** Scan each question folder for `.class` and `.txt` files that aren't student code
- **Max scores:** Count test cases in existing tester files (count `tcNum++` occurrences)

The professor would see the inferred config in the UI and confirm/edit before proceeding, rather than editing a properties file manually.

**Note:** This is a significant feature that touches AppConfig, the UI flow, and potentially the web API. Consider implementing as a "config wizard" step that runs once and generates/updates `config.properties`.

**Affected files:** `AppConfig.java` (new inference logic), `ConfigController.java` (new endpoints), `config.properties`

---

## B5. Missing Data Files — `personstester.txt` / `studentstester.txt`

**Current state:** The original tester files (`Q2aTester.java`, `Q2bTester.java`) reference `personstester.txt` and `studentstester.txt`. These files exist in student submission folders but are **not** included in the reference template directory (`RenameToYourUsername/`). The template only has `persons.txt` and `students.txt`.

**Action required:** Copy `personstester.txt` and `studentstester.txt` into `is442-project-materials/RenameToYourUsername/Q2/` so that:
1. The AI has access to both sets of data files when generating test cases
2. Generated tests can use either filename and compute correct expected values
3. The grading pipeline's dependency copy step can include them if needed

**Note:** The filenames used in test cases are string inputs to the student's method. The actual `.txt` files must exist in the student's folder at grading runtime. Currently, only `persons.txt` and `students.txt` are listed in `config.properties` dependency files — `personstester.txt` and `studentstester.txt` would need to be added if tests reference them.

**Affected files:** `is442-project-materials/RenameToYourUsername/Q2/` (add files), `config.properties` (update dependency list)

---

# Frontend Changes

## F1. IDE-Style Review UI (NeetCode-Inspired)

**Current state:** The review step (Step 6) shows a flat list of test cases with expandable raw code. It works but is not intuitive for debugging or understanding what each test does.

**Proposed change:** Redesign the review UI with a split-pane layout:
- **Left panel:** Question/test case selector — tree view with questions as parents and individual test cases as children. Click to navigate.
- **Right panel:** Code editor component (read-only or editable) showing the generated Java code with syntax highlighting. Include detailed inline comments explaining each test case's purpose, inputs, and expected output.

**Benefits:** Professors can quickly navigate between test cases, understand what each test does at a glance, and spot issues more easily. Closer to familiar developer tooling.

**Depends on:** B1 (Structured LLM Output) — structured data makes it much easier to render individual test cases with metadata in the UI. Without it, the frontend can only show raw code blocks.

**Affected files:** `Step6ReviewResults.tsx` (rewrite), potentially new components for code editor and tree view

---

## F2. Simplified Input Flow — Folder Selection

**Current state:** Step 2 requires the professor to type file paths manually (tester files folder, template directory). This is error-prone and not user-friendly.

**Proposed change:**
- Replace text path inputs with folder/file picker dialogs where possible (browser limitations apply — may need Electron or a backend-assisted file browser API).
- Combine all path inputs (exam PDF, tester folder, template folder, output folder) into a single configuration page, then proceed to the generation page.
- Consider auto-detecting paths based on project structure conventions (e.g. scan for `is442-project-materials/` subdirectories).

**Flow change:**
```
Current:  Step 1 (PDF) → Step 2 (Tester + Template paths) → Step 3 (Questions) → ...
Proposed: Step 1 (All paths on one page) → Step 2 (Question selection + generation config) → ...
```

**Affected files:** `Step1UploadPdf.tsx`, `Step2TesterFolder.tsx` (merge into single step), `StepWizard.tsx`, `TestGenerationWizard.tsx`

---

## F3. Save Page — Summary View

**Current state:** Step 7 (Confirm & Save) shows an output folder input, a checkbox for updating scores, and the save button. After saving, it lists saved file paths.

**Proposed change:** The save/final page should show a comprehensive summary of the entire generation session:
- All input paths used (PDF, tester dir, template dir)
- Per-question breakdown: original test count, generated test count, total weight, any errors
- Before/after max score comparison
- Preview of file changes (which files will be created/modified)
- Link to open generated files after saving

This gives the professor a final review checkpoint before committing changes.

**Affected files:** `Step7ConfirmSave.tsx` (rewrite)

---

# Priority & Sequencing

### Quick Wins
| ID | Improvement | Effort | Impact |
|----|------------|--------|--------|
| B5 | Missing data files | Low | Fixes immediate correctness issue |
| B3 | AI-inferred test count | Low | Better defaults, less guesswork |
| F3 | Save summary page | Low | Better final review experience |

### Medium-Term
| ID | Improvement | Effort | Impact |
|----|------------|--------|--------|
| B1 | Structured LLM output | Medium | Eliminates syntax errors, unlocks F1 |
| F2 | Folder selection UI | Medium | UX polish |
| B2 | PDF image support | Medium | Only needed if exam PDFs have diagrams |

### Larger Initiatives
| ID | Improvement | Effort | Impact |
|----|------------|--------|--------|
| F1 | IDE-style review UI | High | Best review experience (depends on B1) |
| B4 | Auto-inferred config | High | Major UX improvement, reduces manual setup |

### Dependency Chain
```
B1 (Structured Output) ──► F1 (IDE-style UI)
B4 (Auto-inferred Config) ──► F2 (Folder Selection — auto-detect paths)
B3 (AI-inferred Count) ──► F2 (Show recommendations in UI)
```

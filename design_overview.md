# Auto Grading System — Design Overview

## 1. Simple Architecture 
```
┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐
│  EXTRACT │──▶│ VALIDATE │──▶│ COMPILE  │──▶│ EXECUTE  │──▶│  REPORT  │
└──────────┘   └──────────┘   └──────────┘   └──────────┘   └──────────┘
     │              │              │              │              │
     ▼              ▼              ▼              ▼              ▼
  Unzip file    Check folder   Run javac     Run tester    Generate
  + resolve     structure &    on student    with timeout  CSV scores
  identity      fix issues     + tester                    + anomalies
```


## 2. Pipeline Architecture

The system processes submissions through a **linear, staged pipeline** where each stage handles one responsibility:

```
START
  │
  ▼
┌──────────────────────┐
│ Load Configuration   │  ← config.properties (paths, timeout, etc.)
└──────────┬───────────┘
           ▼
┌──────────────────────┐
│ Scan submission dir  │  ← Find all *.zip files
│ for ZIP files        │
└──────────┬───────────┘
           ▼
     ┌─────┴─────┐
     │ For each  │
     │ ZIP file  │──────────────────────────────────┐
     └─────┬─────┘                                  │
           ▼                                        │
  ┌────────────────────┐                            │
  │ Extract ZIP to     │                            │
  │ temp working dir   │                            │
  └────────┬───────────┘                            │
           ▼                                        │
  ┌────────────────────┐                            │
  │ Parse username     │  ← from ZIP filename       │
  │ from filename      │     e.g. "chee.teo.2022"   │
  └────────┬───────────┘                            │
           ▼                                        │
  ┌────────────────────┐     ┌───────────────────┐  │
  │ Validate structure │────→│ Log anomalies     │  │
  │ - folder hierarchy │     │ (missing files,   │  │
  │ - required files   │     │  wrong naming)    │  │
  └────────┬───────────┘     └───────────────────┘  │
           ▼                                        │
  ┌────────────────────┐                            │
  │ Normalize folder   │  ← Fix known issues        │
  │ structure          │                            │
  └────────┬───────────┘                            │
           ▼                                        │
     ┌─────┴──────┐                                 │
     │ For each   │                                 │
     │ question   │──────────────────┐              │
     └─────┬──────┘                  │              │
           ▼                         │              │
  ┌─────────────────────┐            │              │
  │ Copy tester + deps  │            │              │
  │ into question folder│            │              │
  └────────┬────────────┘            │              │
           ▼                         │              │
  ┌─────────────────────┐            │              │
  │ Compile (javac)     │            │              │
  │ via ProcessBuilder  │            │              │
  └────────┬────────────┘            │              │
        ┌──┴──┐                      │              │
        │Pass?│                      │              │
        └──┬──┘                      │              │
     Yes   │   No → score = 0.0      │              │
           ▼          + log error    │              │
  ┌─────────────────────┐            │              │
  │ Run tester (java)   │            │              │
  │ with TIMEOUT        │            │              │
  └────────┬────────────┘            │              │
        ┌──┴──┐                      │              │
        │Done?│                      │              │
        └──┬──┘                      │              │
  OK │  Timeout │  Crash             │              │
     │  → kill  │  → score = 0.0     │              │
     ▼          ▼                    │              │
  ┌─────────────────────┐            │              │
  │ Parse stdout for    │            │              │
  │ score (last line)   │            │              │
  └────────┬────────────┘            │              │
           ▼                         │              │
     Next question ──────────────────┘              │
           │                                        │
           ▼                                        │
  ┌────────────────────┐                            │
  │ Sum per-question   │                            │
  │ scores → total     │                            │
  └────────┬───────────┘                            │
           ▼                                        │
     Next student ──────────────────────────────────┘
           │
           ▼
  ┌────────────────────┐
  │ Export CSV         │  ← Fill "Calculated Final Grade Numerator"
  │ Print summary      │  ← Per-student breakdown + anomalies
  └────────────────────┘
           │
           ▼
         END
```

---

## 3. Separation of Concerns

The codebase is organized into **5 logical layers**:

```
┌─────────────────────────────────────────────────────────────┐
│                      UI LAYER                               │
│   Handles all user interaction (menu, inputs, progress)     │
├─────────────────────────────────────────────────────────────┤
│                   SERVICE LAYER                             │
│   Contains all business logic:                              │
│   - Extract ZIPs, validate structure, compile, execute      │
├─────────────────────────────────────────────────────────────┤
│                    MODEL LAYER                              │
│   Pure data objects (no logic, just state):                 │
│   - Student info, scores, results, anomalies                │
├─────────────────────────────────────────────────────────────┤
│                  REPORTING LAYER                            │
│   Transforms results into output formats:                   │
│   - CSV export, console summary, anomaly reports            │
├─────────────────────────────────────────────────────────────┤
│                   CONFIG LAYER                              │
│   Externalized settings (no hardcoding):                    │
│   - Paths, timeout, question definitions                    │
└─────────────────────────────────────────────────────────────┘
```

### Data Flow Between Layers

```
User Input → UI Layer → Service Layer → Model Objects → Reporting Layer → Output
                              ↑
                        Config Layer
```

### Key Benefit

Each layer has **one job** — changes in one layer don't affect others:
- Change CSV format? → Only touch Reporting Layer
- Add new question type? → Only touch Config + Service Layers
- Switch to GUI? → Only replace UI Layer

---

## 4. CLI Demo Flow

```
=======================================================
       IS442 Auto Grading System v1.0
=======================================================

[1] Grade Submissions
[2] View Configuration
[3] Generate Report
[4] Exit

Select option: 1

Enter submissions folder path: ./student-submission
Enter tester files folder path: ./Tester-Files
Enter scoresheet CSV path: ./IS442-ScoreSheet.csv

Processing...

[==========                    ] 33% | Grading chee.teo.2022...
[====================          ] 66% | Grading david.2024...
[==============================] 100% | Complete!

GRADING SUMMARY
───────────────────────────────────────────────────────
 Student           │ Q1a │ Q1b │ Q2a │ Q2b │ Q3  │ Total
───────────────────┼─────┼─────┼─────┼─────┼─────┼──────
 chee.teo.2022     │ 3.0 │ 3.0 │ 5.0 │ 1.0 │ 0.0 │ 12.0
 david.2024        │ 3.0 │ 3.0 │ 4.0 │ 0.0 │ 0.0 │ 10.0
 fen.lai.2022      │ 1.0 │ 0.0 │ 5.0 │ 4.0 │ 0.0 │ 10.0
 jing.lim.2021     │ 0.0 │ 0.0 │ 0.0 │ 0.0 │ 0.0 │  0.0
 ping.lee.2023     │ 3.0 │ 3.0 │ 5.0 │ 5.0 │ 4.0 │ 20.0
 xing.yan.2023     │ 0.0 │ 1.0 │ 5.0 │ 5.0 │ 4.0 │ 15.0
───────────────────────────────────────────────────────

ANOMALIES DETECTED: 3
  ⚠ fen.lai.2022 - Folder was 'RenameToYourStudentID'
  ⚠ jing.lim.2021 - Compilation error in Q1a.java
  ⚠ xing.yan.2023 - Q1a method returned empty result

Export results to CSV? [Y/n]: y
Results exported to: ./output/IS442-ScoreSheet.csv
```

---

## Annex A: Detailed Class Diagram

_For technical reference — maps to package structure._

```
┌──────────────────────────────────────────────────────────────┐
│                        grading                               │
│                                                              │
│  ┌─────────┐       ┌──────────────────────┐                  │
│  │  App    │──────→│ GradingPipeline      │                  │
│  │ (main)  │       │                      │                  │
│  └─────────┘       │ + run(config): void  │                  │
│       │            └──────┬───────────────┘                  │
│       │                   │ uses                             │
│       ▼                   ▼                                  │
│  ┌──────────┐   ┌──────────────────┐                         │
│  │AppConfig │   │SubmissionExtractor│                        │
│  │          │   │                  │                         │
│  │-zipDir   │   │+extract(zipFile) │                         │
│  │-testerDir│   │ :StudentSubmission│                        │
│  │-csvPath  │   └──────────────────┘                         │
│  │-outputDir│            │                                   │
│  │-timeout  │            ▼                                   │
│  └──────────┘   ┌──────────────────┐                         │
│                 │SubmissionValidator│                        │
│                 │                  │                         │
│                 │+validate(sub)    │                         │
│                 │ :void            │                         │
│                 └──────────────────┘                         │
│                          │                                   │
│                          ▼                                   │
│                 ┌──────────────────┐    ┌─────────────────┐  │
│                 │ GradingEngine    │───→│ ProcessRunner   │  │
│                 │                  │    │                 │  │
│                 │+grade(sub,       │    │+compile(dir,    │  │
│                 │  questions)      │    │  files): Result │  │
│                 │ :void            │    │+run(dir, class, │  │
│                 └──────────────────┘    │  timeout):Result│  │
│                          │              └─────────────────┘  │
│                          ▼                                   │
│                 ┌──────────────────┐                         │
│                 │ ReportExporter   │                         │
│                 │                  │                         │
│                 │+exportCsv(subs,  │                         │
│                 │  templatePath,   │                         │
│                 │  outputPath)     │                         │
│                 │+printSummary()   │                         │
│                 └──────────────────┘                         │
└──────────────────────────────────────────────────────────────┘
```

### Package Structure

```
src/
└── grading/
    ├── App.java                     ← Entry point (main)
    │
    ├── config/
    │   └── AppConfig.java           ← Loads config.properties
    │
    ├── model/
    │   ├── StudentSubmission.java   ← Student entity
    │   ├── QuestionResult.java      ← Per-question result
    │   ├── QuestionConfig.java      ← Question definition
    │   └── ProcessResult.java       ← Compile/run result
    │
    ├── extraction/
    │   └── SubmissionExtractor.java ← Unzip + normalize folders
    │
    ├── validation/
    │   └── SubmissionValidator.java ← Check structure + headers
    │
    ├── execution/
    │   ├── GradingEngine.java       ← Orchestrate grading flow
    │   └── ProcessRunner.java       ← javac/java with timeout
    │
    └── reporting/
        └── ReportExporter.java      ← CSV output + console summary
```

### Responsibility Breakdown (SRP)

| Class | Single Responsibility |
|-------|----------------------|
| `App` | Parse CLI args, load config, wire components, kick off pipeline |
| `AppConfig` | Read and expose values from `config.properties` |
| `SubmissionExtractor` | Unzip a student ZIP, detect and normalize folder structure |
| `SubmissionValidator` | Walk folders, verify required files, check Java headers |
| `GradingEngine` | For one submission + question: copy deps → compile → run → parse |
| `ProcessRunner` | Execute OS process (`javac`/`java`) with timeout, capture output |
| `ReportExporter` | Read CSV template, fill scores, write output; print summary |

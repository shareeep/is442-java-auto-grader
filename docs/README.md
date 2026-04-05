# Documentation

Welcome to the IS442 Auto-Grader documentation.

## What's here

| Guide | Purpose |
|-------|---------|
| [ARCHITECTURE.md](./ARCHITECTURE.md) | System design, package map, key design decisions |
| [design-overview.md](./design-overview.md) | Comprehensive architecture & design deep-dive |
| [CLI-GUIDE.md](./CLI-GUIDE.md) | Command-line usage (one-shot + interactive) |
| [AI-TEST-GENERATION.md](./AI-TEST-GENERATION.md) | AI test case generation — wizard + internals |
| [PARSING.md](./PARSING.md) | PDF parsing, question inference, file upload filtering |
| [API.md](./API.md) | REST API endpoints reference |
| [DEMO-GUIDE.md](./DEMO-GUIDE.md) | Demo presentation script (4 flows) |
| [TESTCASES.md](./TESTCASES.md) | 18 test submissions documentation |

## Diagrams

| Diagram | Shows |
|---------|-------|
| [grading-pipeline.mmd](./diagrams/grading-pipeline.mmd) | Core 5-step grading pipeline (flowchart) |
| [ai-generation-flow.mmd](./diagrams/ai-generation-flow.mmd) | AI test generation workflow (flowchart) |
| [frontend-state.mmd](./diagrams/frontend-state.mmd) | Frontend state: HeyAPI + TanStack Query + Zustand |
| [class-diagram.mmd](./diagrams/class-diagram.mmd) | Key classes and relationships (UML) |
| [grading-sequence.mmd](./diagrams/grading-sequence.mmd) | Grading pipeline sequence diagram |
| [ai-generation-sequence.mmd](./diagrams/ai-generation-sequence.mmd) | AI test generation sequence diagram |
| [deployment-diagram.mmd](./diagrams/deployment-diagram.mmd) | Docker Compose deployment topology |

> Diagrams are rendered in GitHub/GitLab directly. For local preview, use a Mermaid extension (VS Code: `vstirbu.vscode-mermaid-preview`) or any Markdown viewer with Mermaid support.

## Quick links for common tasks

**Demo prep:**
- Full demo walkthrough with 4 flows → [DEMO-GUIDE.md](./DEMO-GUIDE.md)

**Running grading (instructor):**
- CLI one-shot → [CLI-GUIDE.md](./CLI-GUIDE.md#one-shot-mode)
- CLI interactive → [CLI-GUIDE.md](./CLI-GUIDE.md#interactive-mode)
- Web UI → use the Auto-Grader tab in the frontend

**Generating test cases (instructor):**
- 4-step wizard → [AI-TEST-GENERATION.md](./AI-TEST-GENERATION.md#wizard-walkthrough)

**Understanding or extending the codebase (developer):**
- Architecture overview → [ARCHITECTURE.md](./ARCHITECTURE.md)
- Package map → [ARCHITECTURE.md#package-map](./ARCHITECTURE.md#package-map)
- Design decisions → [ARCHITECTURE.md#design-decisions](./ARCHITECTURE.md#design-decisions)
- Question parsing & inference → [PARSING.md](./PARSING.md)
- REST API → [API.md](./API.md)

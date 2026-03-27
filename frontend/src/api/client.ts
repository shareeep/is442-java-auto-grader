import type {
  QuestionConfig,
  GenerateRequest,
  SaveRequest,
  SaveResponse,
  InferredConfig,
} from "../generated/types.gen";
import type {
  AiSettings,
  UploadResponse,
  UploadDirResponse,
  GenerateQuestionRequest,
  GenerationResult,
  TesterInfo,
  AnalyzeSetupRequest,
  ExecuteQuestionRequest,
  RecommendRequest,
  TestCaseRecommendation,
} from "../types";

let BASE = "";

export function setBaseUrl(url: string) {
  BASE = url;
}

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`HTTP ${res.status}: ${body}`);
  }
  return res.json();
}

export async function fetchQuestions(): Promise<QuestionConfig[]> {
  const res = await fetch(`${BASE}/api/generation/config/questions`);
  return json(res);
}

export async function fetchAiSettings(): Promise<AiSettings> {
  const res = await fetch(`${BASE}/api/generation/config/ai`);
  return json(res);
}

export async function uploadExam(file: File): Promise<UploadResponse> {
  const form = new FormData();
  form.append("file", file);
  const res = await fetch(`${BASE}/api/generation/exam/upload`, {
    method: "POST",
    body: form,
  });
  return json(res);
}

/** Pre-parse PDF in background after upload */
export async function preparsePdf(examId: string): Promise<{ status: string }> {
  const res = await fetch(`${BASE}/api/generation/preparse-pdf`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ examId }),
  });
  return json(res);
}

/** Upload a template directory (files from webkitdirectory picker). */
export async function uploadTemplate(files: File[]): Promise<UploadDirResponse> {
  const form = new FormData();
  for (const file of files) {
    // Encode the relative path using __SEP__ so the server can reconstruct directories
    const encodedName = file.webkitRelativePath.replace(/\//g, "__SEP__");
    form.append("files", file, encodedName);
  }
  const res = await fetch(`${BASE}/api/generation/template/upload`, {
    method: "POST",
    body: form,
  });
  return json(res);
}

/** Upload tester files (files from webkitdirectory picker). */
export async function uploadTesters(files: File[]): Promise<UploadDirResponse> {
  const form = new FormData();
  for (const file of files) {
    form.append("files", file, file.name);
  }
  const res = await fetch(`${BASE}/api/generation/testers/upload`, {
    method: "POST",
    body: form,
  });
  return json(res);
}

export async function generate(
  req: GenerateRequest
): Promise<GenerationResult[]> {
  const res = await fetch(`${BASE}/api/generation/generate`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(req),
  });
  return json(res);
}

export async function generateQuestion(
  req: GenerateQuestionRequest
): Promise<GenerationResult> {
  const res = await fetch(`${BASE}/api/generation/generate-question`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(req),
  });
  return json(res);
}

export async function fetchTester(
  className: string,
  testerId: string
): Promise<TesterInfo> {
  const res = await fetch(
    `${BASE}/api/generation/tester/${encodeURIComponent(className)}?testerId=${encodeURIComponent(testerId)}`
  );
  return json(res);
}

export async function saveResults(req: SaveRequest): Promise<SaveResponse> {
  const res = await fetch(`${BASE}/api/generation/save`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(req),
  });
  return json(res);
}

/** Phase 2 — Analyze exam PDF + uploaded directories → InferredConfig */
export async function analyzeSetup(
  req: AnalyzeSetupRequest
): Promise<InferredConfig> {
  const res = await fetch(`${BASE}/api/generation/analyze-setup`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(req),
    signal: AbortSignal.timeout(300000), // 5 min timeout
  });
  return json(res);
}

/** Phase 2/3 — Generate for a single question using confirmed InferredQuestionConfig */
export async function executeQuestion(
  req: ExecuteQuestionRequest
): Promise<GenerationResult> {
  const res = await fetch(`${BASE}/api/generation/execute`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(req),
  });
  return json(res);
}

/** Phase 4 — Ask AI to recommend test count + concepts for a question */
export async function recommend(
  req: RecommendRequest
): Promise<TestCaseRecommendation> {
  const res = await fetch(`${BASE}/api/generation/recommend`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(req),
  });
  return json(res);
}

/** Phase 5 — Refine generated code via LLM */
export async function refineCode(req: {
  examId: string;
  questionId: string;
  currentCode: string;
  refinementPrompt: string;
}): Promise<{ questionId: string; refinedCode: string }> {
  const res = await fetch(`${BASE}/api/generation/refine`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(req),
  });
  return json(res);
}

/** Phase 5 — Fetch student template source files for split-pane viewer */
export async function fetchTemplateSource(
  templateId: string,
  folder: string
): Promise<Record<string, string>> {
  const res = await fetch(
    `${BASE}/api/generation/template-source?templateId=${encodeURIComponent(templateId)}&folder=${encodeURIComponent(folder)}`
  );
  return json(res);
}

/** RunResults — structured per-student scores for a run */
export async function getRunResults(runId: string): Promise<any[]> {
  const res = await fetch(`${BASE}/api/reports/${encodeURIComponent(runId)}/results`);
  return json(res);
}

/** RunResults — .java source files for a specific student in a run */
export async function getStudentCode(runId: string, username: string): Promise<Record<string, string>> {
  const res = await fetch(
    `${BASE}/api/reports/${encodeURIComponent(runId)}/code/${encodeURIComponent(username)}`
  );
  return json(res);
}

/** Phase 7.1 — List past grading runs */
export async function listReports(): Promise<
  Array<{
    id: string;
    timestamp: string;
    hasPdf: boolean;
    hasCsv: boolean;
    studentCount: number;
  }>
> {
  const res = await fetch(`${BASE}/api/reports/list`);
  return json(res);
}

/** Phase 7.1 — Get run logs */
export async function getRunLogs(
  runId: string
): Promise<{
  runLog: string;
  students: Array<{
    username: string;
    logs: Array<{ name: string; content: string }>;
  }>;
}> {
  const res = await fetch(`${BASE}/api/reports/${encodeURIComponent(runId)}/logs`);
  return json(res);
}

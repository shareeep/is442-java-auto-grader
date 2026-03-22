import type {
  QuestionConfig,
  AiSettings,
  UploadResponse,
  GenerateRequest,
  GenerateQuestionRequest,
  GenerationResult,
  SaveRequest,
  SaveResponse,
  TesterInfo,
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
  dir: string
): Promise<TesterInfo> {
  const res = await fetch(
    `${BASE}/api/generation/tester/${encodeURIComponent(className)}?dir=${encodeURIComponent(dir)}`
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

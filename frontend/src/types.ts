import type { TestCaseEntry, InferredQuestionConfig } from './generated/types.gen';

export interface AiSettings {
  model: string;
  maxTokens: number;
  defaultCasesPerQuestion: number;
}

export interface GenerationResult {
  questionId: string;
  cases: TestCaseEntry[];
  compiledOk: boolean;
  compileErrors: string;
  generatedCode: string;
}

export interface GenerateQuestionRequest {
  examId: string;
  testerId: string | null;
  templateId: string | null;
  questionId: string;
  numCases: number;
}

export interface UploadResponse {
  examId: string;
  fileName: string;
}

export interface UploadDirResponse {
  templateId?: string;
  testerId?: string;
  fileCount: number;
}

export interface TesterInfo {
  code: string;
  testCaseCount: number;
}

/** Props for the standalone TestGenerationWizard component */
export interface TestGenerationWizardProps {
  apiBaseUrl?: string;
  onComplete?: (savedPaths: string[]) => void;
}

export interface AnalyzeSetupRequest {
  examId: string;
  templateId: string | null;
  testerId: string | null;
}

export interface ExecuteQuestionRequest {
  examId: string;
  question: InferredQuestionConfig;
  numCases: number;
  templateId: string | null;
  testerId: string | null;
}

export interface RecommendRequest {
  examId: string;
  questionId: string;
}

export interface TestCaseRecommendation {
  questionId: string;
  recommendedCount: number;
  conceptsToCover: string[];
}

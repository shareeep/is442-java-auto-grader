export interface QuestionConfig {
  questionId: string;
  folder: string;
  testerClassName: string;
  maxScore: number;
  dependencyFolder: string | null;
  dependencyFiles: string[];
}

export interface AiSettings {
  model: string;
  maxTokens: number;
  defaultCasesPerQuestion: number;
}

export interface GeneratedTestCase {
  description: string;
  inputArgs: string;
  expectedOutput: string;
  weight: number;
}

export interface GenerationResult {
  questionId: string;
  cases: GeneratedTestCase[];
  compiledOk: boolean;
  compileErrors: string;
  generatedCode: string;
}

export interface QuestionSelection {
  questionId: string;
  numCases: number;
}

export interface GenerateRequest {
  examId: string;
  testersDir: string | null;
  templateDir: string | null;
  questions: QuestionSelection[];
}

export interface GenerateQuestionRequest {
  examId: string;
  testersDir: string | null;
  templateDir: string | null;
  questionId: string;
  numCases: number;
}

export interface SaveRequestEntry {
  questionId: string;
  testerClassName: string;
  generatedCode: string;
  cases: GeneratedTestCase[];
}

export interface SaveRequest {
  examId: string;
  testersDir: string | null;
  outputDir: string;
  results: SaveRequestEntry[];
  updateMaxScores: boolean;
}

export interface SaveResponse {
  savedPaths: string[];
  errors: string[];
}

export interface UploadResponse {
  examId: string;
  fileName: string;
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

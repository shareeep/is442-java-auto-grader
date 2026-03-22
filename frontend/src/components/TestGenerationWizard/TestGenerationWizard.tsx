import { useState, useEffect, useCallback } from "react";
import { setBaseUrl, fetchQuestions, fetchAiSettings } from "../../api/client";
import type {
  QuestionConfig,
  AiSettings,
  GenerationResult,
  QuestionSelection,
  SaveResponse,
  TestGenerationWizardProps,
} from "../../types";
import StepWizard from "../StepWizard";
import Step1UploadPdf from "../steps/Step1UploadPdf";
import Step2TesterFolder from "../steps/Step2TesterFolder";
import Step3SelectQuestions from "../steps/Step3SelectQuestions";
import Step4SetCaseCount from "../steps/Step4SetCaseCount";
import Step5Generate from "../steps/Step5Generate";
import Step6ReviewResults from "../steps/Step6ReviewResults";
import Step7ConfirmSave from "../steps/Step7ConfirmSave";

const TOTAL_STEPS = 7;

export default function TestGenerationWizard({
  apiBaseUrl = "",
  onComplete,
}: TestGenerationWizardProps) {
  // Config state
  const [questions, setQuestions] = useState<QuestionConfig[]>([]);
  const [aiSettings, setAiSettings] = useState<AiSettings | null>(null);
  const [configError, setConfigError] = useState<string | null>(null);

  // Wizard state
  const [step, setStep] = useState(1);
  const [examId, setExamId] = useState<string | null>(null);
  const [examFileName, setExamFileName] = useState<string | null>(null);
  const [testersDir, setTestersDir] = useState<string | null>(
    "is442-project-materials/Tester-Files"
  );
  const [templateDir, setTemplateDir] = useState<string | null>(null);
  const [selectedQuestions, setSelectedQuestions] = useState<Set<string>>(
    new Set()
  );
  const [caseCounts, setCaseCounts] = useState<Record<string, number>>({});
  const [generationResults, setGenerationResults] = useState<
    GenerationResult[] | null
  >(null);
  const [generationError, setGenerationError] = useState<string | null>(null);
  const [weights, setWeights] = useState<Record<string, number[]>>({});

  // Load config on mount
  useEffect(() => {
    setBaseUrl(apiBaseUrl);
    Promise.all([fetchQuestions(), fetchAiSettings()])
      .then(([q, ai]) => {
        setQuestions(q);
        setAiSettings(ai);
      })
      .catch((e) =>
        setConfigError(e instanceof Error ? e.message : "Failed to load config")
      );
  }, [apiBaseUrl]);

  // Navigation
  const canGoNext = (): boolean => {
    switch (step) {
      case 1:
        return !!examId;
      case 2:
        return true;
      case 3:
        return selectedQuestions.size > 0;
      case 4:
        return true;
      case 5:
        return !!generationResults;
      case 6:
        return true;
      default:
        return false;
    }
  };

  const goNext = () => {
    if (step < TOTAL_STEPS && canGoNext()) setStep(step + 1);
  };
  const goBack = () => {
    if (step > 1) setStep(step - 1);
  };

  // Handlers
  const handleUpload = (id: string, name: string) => {
    setExamId(id);
    setExamFileName(name);
  };

  const handleToggleQuestion = (id: string) => {
    setSelectedQuestions((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const handleSelectAll = () => {
    if (selectedQuestions.size === questions.length) {
      setSelectedQuestions(new Set());
    } else {
      setSelectedQuestions(new Set(questions.map((q) => q.questionId)));
    }
  };

  const handleGenerationDone = useCallback((results: GenerationResult[]) => {
    setGenerationResults(results);
    // Initialize weights from results
    const w: Record<string, number[]> = {};
    for (const r of results) {
      w[r.questionId] = r.cases.map((c) => c.weight);
    }
    setWeights(w);
  }, []);

  const handleGenerationError = useCallback((msg: string) => {
    setGenerationError(msg);
  }, []);

  const handleWeightChanged = (
    questionId: string,
    caseIndex: number,
    w: number
  ) => {
    setWeights((prev) => {
      const arr = [...(prev[questionId] ?? [])];
      arr[caseIndex] = w;
      return { ...prev, [questionId]: arr };
    });
  };

  const handleSaved = (resp: SaveResponse) => {
    if (onComplete && resp.savedPaths.length > 0) {
      onComplete(resp.savedPaths);
    }
  };

  // Build question selections for generation
  const selections: QuestionSelection[] = Array.from(selectedQuestions).map(
    (id) => ({
      questionId: id,
      numCases:
        caseCounts[id] ?? aiSettings?.defaultCasesPerQuestion ?? 3,
    })
  );

  if (configError) {
    return (
      <div
        style={{
          padding: "2rem",
          border: "1px solid #fca5a5",
          borderRadius: 10,
          background: "#fef2f2",
          color: "#991b1b",
        }}
      >
        <strong>Failed to load configuration</strong>
        <p>{configError}</p>
        <p style={{ fontSize: "0.85rem" }}>
          Make sure the Spring Boot backend is running on port 8080.
        </p>
      </div>
    );
  }

  if (questions.length === 0) {
    return <div style={{ textAlign: "center", padding: "2rem", color: "#6b7280" }}>Loading configuration...</div>;
  }

  const isGenerateStep = step === 5;
  const isSaveStep = step === 7;

  return (
    <StepWizard
      currentStep={step}
      totalSteps={TOTAL_STEPS}
      onBack={goBack}
      onNext={goNext}
      canGoNext={canGoNext()}
      nextLabel={step === 6 ? "Confirm & Save" : "Next"}
      hideNav={isGenerateStep || isSaveStep}
    >
      {step === 1 && (
        <Step1UploadPdf
          onUploaded={handleUpload}
          currentExamId={examId}
          currentFileName={examFileName}
        />
      )}
      {step === 2 && (
        <Step2TesterFolder
          testersDir={testersDir}
          templateDir={templateDir}
          onTestersChanged={setTestersDir}
          onTemplateChanged={setTemplateDir}
        />
      )}
      {step === 3 && (
        <Step3SelectQuestions
          questions={questions}
          selected={selectedQuestions}
          onToggle={handleToggleQuestion}
          onSelectAll={handleSelectAll}
        />
      )}
      {step === 4 && (
        <Step4SetCaseCount
          questions={questions}
          selectedIds={selectedQuestions}
          caseCounts={caseCounts}
          defaultCount={aiSettings?.defaultCasesPerQuestion ?? 3}
          onCountChanged={(id, count) =>
            setCaseCounts((prev) => ({ ...prev, [id]: count }))
          }
        />
      )}
      {step === 5 && examId && (
        <>
          <Step5Generate
            examId={examId}
            testersDir={testersDir}
            templateDir={templateDir}
            selections={selections}
            onDone={handleGenerationDone}
            onError={handleGenerationError}
            results={generationResults}
          />
          {generationError && (
            <div style={{ color: "#dc2626", marginTop: "1rem" }}>
              {generationError}
            </div>
          )}
          {generationResults && (
            <div style={{ marginTop: "1rem", textAlign: "right" }}>
              <button
                onClick={goNext}
                style={{
                  padding: "0.5rem 1.5rem",
                  border: "none",
                  borderRadius: 6,
                  background: "#2563eb",
                  color: "#fff",
                  fontWeight: 600,
                  cursor: "pointer",
                }}
              >
                Review Results
              </button>
            </div>
          )}
        </>
      )}
      {step === 6 && generationResults && (
        <Step6ReviewResults
          results={generationResults}
          questions={questions}
          weights={weights}
          onWeightChanged={handleWeightChanged}
        />
      )}
      {step === 7 && examId && generationResults && (
        <Step7ConfirmSave
          examId={examId}
          testersDir={testersDir}
          results={generationResults}
          questions={questions}
          weights={weights}
          onSaved={handleSaved}
        />
      )}
    </StepWizard>
  );
}

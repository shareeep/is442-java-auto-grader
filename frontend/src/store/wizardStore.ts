import { create } from 'zustand';
import { devtools, persist, createJSONStorage } from 'zustand/middleware';
import { immer } from 'zustand/middleware/immer';
/** Returned by POST /api/exams/{examId}/analyze — not in the generated SDK
 *  because the endpoint returns a raw Map, so we type it loosely here. */
type InferredConfig = Record<string, any>;

interface WizardStore {
  currentStep: number;
  bootId: string | null;
  examId: string | null;
  templateId: string | null;
  testerId: string | null;
  inferredConfig: InferredConfig | null;
  selectedQs: string[];
  recommendations: Record<string, any>;
  customSuggestions: Record<string, string[]>;
  results: Record<string, any>;
  localCode: Record<string, string>;
  localCases: Record<string, any[]>;
  exportComplete: boolean;
  exportPath: string | null;

  setBootId: (id: string | null) => void;
  setStep: (step: number) => void;
  setExamId: (id: string) => void;
  setTemplateId: (id: string | undefined) => void;
  setTesterId: (id: string | undefined) => void;
  setInferredConfig: (config: InferredConfig) => void;
  setSelectedQs: (qs: string[]) => void;
  setRecommendation: (qid: string, rec: any) => void;
  deleteRecommendedConcept: (qid: string, idx: number) => void;
  addCustomSuggestion: (qid: string, text: string) => void;
  deleteCustomSuggestion: (qid: string, idx: number) => void;
  setResult: (qid: string, result: any) => void;
  resetQuestion: (qid: string) => void;
  setLocalCode: (qid: string, code: string) => void;
  initLocalCode: (code: Record<string, string>) => void;
  setLocalCases: (qid: string, cases: any[]) => void;
  initLocalCases: (cases: Record<string, any[]>) => void;
  setExportComplete: (path: string) => void;
  reset: () => void;
}

const initialState = {
  currentStep: 1,
  bootId: null,
  examId: null,
  templateId: null,
  testerId: null,
  inferredConfig: null,
  selectedQs: [] as string[],
  recommendations: {} as Record<string, any>,
  customSuggestions: {} as Record<string, string[]>,
  results: {} as Record<string, any>,
  localCode: {} as Record<string, string>,
  localCases: {} as Record<string, any[]>,
  exportComplete: false,
  exportPath: null,
};

export const useWizardStore = create<WizardStore>()(
  persist(
    devtools(
      immer((set) => ({
        ...initialState,

        setBootId: (id) =>
          set((s) => { s.bootId = id; }, undefined, 'wizard/setBootId'),

        setStep: (step) =>
          set((s) => { s.currentStep = step; }, undefined, 'wizard/setStep'),

        setExamId: (id) =>
          set((s) => { s.examId = id; }, undefined, 'wizard/setExamId'),

        setTemplateId: (id) =>
          set((s) => { s.templateId = id ?? null; }, undefined, 'wizard/setTemplateId'),

        setTesterId: (id) =>
          set((s) => { s.testerId = id ?? null; }, undefined, 'wizard/setTesterId'),

        setInferredConfig: (config) =>
          set((s) => { s.inferredConfig = config; }, undefined, 'wizard/setInferredConfig'),

        setSelectedQs: (qs) =>
          set((s) => { s.selectedQs = qs; }, undefined, 'wizard/setSelectedQs'),

        setRecommendation: (qid, rec) =>
          set((s) => { s.recommendations[qid] = rec; }, undefined, 'wizard/setRecommendation'),

        deleteRecommendedConcept: (qid, idx) =>
          set((s) => {
            const rec = s.recommendations[qid];
            if (rec?.conceptsToCover) {
              rec.conceptsToCover.splice(idx, 1);
              rec.recommendedCount = rec.conceptsToCover.length;
            }
          }, undefined, 'wizard/deleteRecommendedConcept'),

        addCustomSuggestion: (qid, text) =>
          set((s) => {
            if (!s.customSuggestions[qid]) s.customSuggestions[qid] = [];
            s.customSuggestions[qid].push(text);
          }, undefined, 'wizard/addCustomSuggestion'),

        deleteCustomSuggestion: (qid, idx) =>
          set((s) => {
            if (s.customSuggestions[qid]) {
              s.customSuggestions[qid].splice(idx, 1);
            }
          }, undefined, 'wizard/deleteCustomSuggestion'),

        setResult: (qid, result) =>
          set((s) => { s.results[qid] = result; }, undefined, 'wizard/setResult'),

        resetQuestion: (qid) =>
          set((s) => {
            delete s.recommendations[qid];
            delete s.customSuggestions[qid];
            delete s.results[qid];
            delete s.localCode[qid];
            delete s.localCases[qid];
          }, undefined, 'wizard/resetQuestion'),

        setLocalCode: (qid, code) =>
          set((s) => { s.localCode[qid] = code; }, undefined, 'wizard/setLocalCode'),

        initLocalCode: (code) =>
          set((s) => { s.localCode = { ...s.localCode, ...code }; }, undefined, 'wizard/initLocalCode'),

        setLocalCases: (qid, cases) =>
          set((s) => { s.localCases[qid] = cases; }, undefined, 'wizard/setLocalCases'),

        initLocalCases: (cases) =>
          set((s) => { s.localCases = { ...s.localCases, ...cases }; }, undefined, 'wizard/initLocalCases'),

        setExportComplete: (path) =>
          set((s) => { s.exportComplete = true; s.exportPath = path; }, undefined, 'wizard/setExportComplete'),

        reset: () =>
          set((s) => Object.assign(s, initialState), undefined, 'wizard/reset'),
      })),
      {
        name: 'WizardStore',
        enabled: import.meta.env.DEV,
      }
    ),
    {
      name: 'wizard-storage',
      storage: createJSONStorage(() => sessionStorage),
      version: 4,
      partialize: (state) => ({
        currentStep: state.currentStep,
        bootId: state.bootId,
        examId: state.examId,
        templateId: state.templateId,
        testerId: state.testerId,
        inferredConfig: state.inferredConfig,
        selectedQs: state.selectedQs,
        recommendations: state.recommendations,
        customSuggestions: state.customSuggestions,
        results: state.results,
        localCode: state.localCode,
        localCases: state.localCases,
        exportComplete: state.exportComplete,
        exportPath: state.exportPath,
      }),
    }
  )
);

import { create } from 'zustand';
import { devtools, persist, createJSONStorage } from 'zustand/middleware';
import { immer } from 'zustand/middleware/immer';
import type { InferredConfig } from '../generated/types.gen';

interface WizardStore {
  currentStep: number;
  examId: string | null;
  templateId: string | null;
  testerId: string | null;
  inferredConfig: InferredConfig | null;
  selectedQs: string[];
  recommendations: Record<string, any>;
  results: Record<string, any>;
  localCode: Record<string, string>;
  exportComplete: boolean;
  exportPath: string | null;

  setStep: (step: number) => void;
  setExamId: (id: string) => void;
  setTemplateId: (id: string | undefined) => void;
  setTesterId: (id: string | undefined) => void;
  setInferredConfig: (config: InferredConfig) => void;
  setSelectedQs: (qs: string[]) => void;
  setRecommendation: (qid: string, rec: any) => void;
  setResult: (qid: string, result: any) => void;
  setLocalCode: (qid: string, code: string) => void;
  initLocalCode: (code: Record<string, string>) => void;
  setExportComplete: (path: string) => void;
  reset: () => void;
}

const initialState = {
  currentStep: 1,
  examId: null,
  templateId: null,
  testerId: null,
  inferredConfig: null,
  selectedQs: [] as string[],
  recommendations: {} as Record<string, any>,
  results: {} as Record<string, any>,
  localCode: {} as Record<string, string>,
  exportComplete: false,
  exportPath: null,
};

export const useWizardStore = create<WizardStore>()(
  persist(
    devtools(
      immer((set) => ({
        ...initialState,

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

        setResult: (qid, result) =>
          set((s) => { s.results[qid] = result; }, undefined, 'wizard/setResult'),

        setLocalCode: (qid, code) =>
          set((s) => { s.localCode[qid] = code; }, undefined, 'wizard/setLocalCode'),

        initLocalCode: (code) =>
          set((s) => { s.localCode = { ...s.localCode, ...code }; }, undefined, 'wizard/initLocalCode'),

        setExportComplete: (path) =>
          set((s) => { s.exportComplete = true; s.exportPath = path; }, undefined, 'wizard/setExportComplete'),

        reset: () =>
          set(() => ({ ...initialState }), true, 'wizard/reset'),
      })),
      {
        name: 'WizardStore',
        enabled: import.meta.env.DEV,
      }
    ),
    {
      name: 'wizard-storage',
      storage: createJSONStorage(() => localStorage),
      version: 1,
      partialize: (state) => ({
        currentStep: state.currentStep,
        examId: state.examId,
        templateId: state.templateId,
        testerId: state.testerId,
        inferredConfig: state.inferredConfig,
        selectedQs: state.selectedQs,
        recommendations: state.recommendations,
        results: state.results,
        localCode: state.localCode,
        exportComplete: state.exportComplete,
        exportPath: state.exportPath,
      }),
    }
  )
);

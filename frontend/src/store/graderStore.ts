import { create } from 'zustand';
import { devtools, persist, createJSONStorage } from 'zustand/middleware';
import { immer } from 'zustand/middleware/immer';
import type { Submission } from '../components/SubmissionDetails';

type Phase = 'upload' | 'grading' | 'results';

interface GradingResult {
  status: string;
  submissions: Submission[];
}

interface GraderStore {
  phase: Phase;
  result: GradingResult | null;
  runId: string | null;

  setPhase: (phase: Phase) => void;
  setResult: (result: GradingResult | null) => void;
  setRunId: (id: string | null) => void;
  reset: () => void;
}

const initialState = {
  phase: 'upload' as Phase,
  result: null,
  runId: null,
};

export const useGraderStore = create<GraderStore>()(
  persist(
    devtools(
      immer((set) => ({
        ...initialState,

        setPhase: (phase) =>
          set((s) => { s.phase = phase; }, undefined, 'grader/setPhase'),

        setResult: (result) =>
          set((s) => { s.result = result; }, undefined, 'grader/setResult'),

        setRunId: (id) =>
          set((s) => { s.runId = id; }, undefined, 'grader/setRunId'),

        reset: () =>
          set((s) => Object.assign(s, initialState), undefined, 'grader/reset'),
      })),
      {
        name: 'GraderStore',
        enabled: import.meta.env.DEV,
      }
    ),
    {
      name: 'grader-storage',
      storage: createJSONStorage(() => localStorage),
      version: 1,
      partialize: (state) => ({
        phase: state.phase,
        result: state.result,
        runId: state.runId,
      }),
    }
  )
);

import React from 'react';
import { ArrowLeft, CheckCircle2, XCircle, ShieldAlert, Minus } from 'lucide-react';

interface Anomaly {
  severity: string;
  description: string;
}

interface QuestionResult {
  questionId: string;
  score: number;
  maxScore: number;
}

export interface Submission {
  username: string;
  name: string;
  displayName: string;
  totalScore: number;
  maxPossibleScore: number;
  anomalies?: Anomaly[];
  results?: QuestionResult[];
}

interface SubmissionDetailsProps {
  submission: Submission;
  onBack: () => void;
}

const SubmissionDetails: React.FC<SubmissionDetailsProps> = ({ submission, onBack }) => {
  const isPerfect = submission.totalScore === submission.maxPossibleScore;
  const results = submission.results ?? [];
  const anomalies = submission.anomalies ?? [];

  // Match anomalies to a question row by checking if the question ID appears in the description
  const getQuestionAnomalies = (questionId: string) =>
    anomalies.filter(a => a.description.toLowerCase().includes(questionId.toLowerCase()));

  // Anomalies that don't match any specific question
  const allMatchedAnomalies = new Set(
    results.flatMap(r => getQuestionAnomalies(r.questionId).map(a => a.description))
  );
  const globalAnomalies = anomalies.filter(a => !allMatchedAnomalies.has(a.description));

  return (
    <div className="flex flex-col gap-4">

      {/* Compact header */}
      <div className="flex items-center justify-between rounded-xl border border-border bg-card px-5 py-4">
        <div className="flex items-center gap-4">
          <button
            onClick={onBack}
            className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground hover:text-foreground transition-colors shrink-0"
          >
            <ArrowLeft size={13} /> Back
          </button>
          <div className="w-px h-6 bg-border" />
          <div>
            <h2 className="text-base font-outfit font-bold text-foreground leading-tight">
              {submission.name || 'Unknown Student'}
            </h2>
            <span className="font-mono text-[10px] text-muted-foreground">
              {submission.username || submission.displayName}
            </span>
          </div>
        </div>
        <div className={`flex items-baseline gap-1 px-4 py-2 rounded-lg border font-mono ${
          isPerfect ? 'border-vsc-green/30 bg-vsc-green/10' : 'border-border bg-secondary'
        }`}>
          <span className={`text-2xl font-bold ${isPerfect ? 'text-vsc-green' : 'text-primary'}`}>
            {submission.totalScore}
          </span>
          <span className="text-sm text-muted-foreground">/{submission.maxPossibleScore}</span>
        </div>
      </div>

      {/* Evaluation matrix with inline anomalies */}
      <div className="rounded-xl border border-border bg-card overflow-hidden">
        <div className="grid grid-cols-12 px-5 py-2.5 border-b border-border bg-secondary/50">
          <div className="col-span-3 text-[10px] font-bold uppercase tracking-wider text-muted-foreground">Question</div>
          <div className="col-span-5 text-[10px] font-bold uppercase tracking-wider text-muted-foreground">Score</div>
          <div className="col-span-4 text-[10px] font-bold uppercase tracking-wider text-muted-foreground text-right">Result</div>
        </div>

        {results.length === 0 ? (
          <div className="py-10 text-center text-xs font-mono text-muted-foreground">No results recorded</div>
        ) : (
          results.map((r, i) => {
            const pct = r.maxScore > 0 ? (r.score / r.maxScore) * 100 : 0;
            const passed = r.score === r.maxScore;
            const partial = r.score > 0 && !passed;
            const qAnomalies = getQuestionAnomalies(r.questionId);

            return (
              <div key={i} className="border-b border-border/40 last:border-0">
                {/* Question row */}
                <div className="grid grid-cols-12 px-5 py-3 items-center">
                  <div className="col-span-3 font-mono text-sm font-semibold text-foreground/80">
                    {r.questionId}
                  </div>
                  <div className="col-span-5 flex items-center gap-2.5">
                    <div className="flex-1 h-1.5 rounded-full bg-secondary overflow-hidden">
                      <div
                        className={`h-full rounded-full transition-all ${
                          passed ? 'bg-vsc-green' : partial ? 'bg-yellow-500' : 'bg-accent'
                        }`}
                        style={{ width: `${pct}%` }}
                      />
                    </div>
                    <span className="font-mono text-xs text-muted-foreground shrink-0">
                      {r.score}/{r.maxScore}
                    </span>
                  </div>
                  <div className="col-span-4 flex items-center justify-end gap-1.5">
                    {passed ? (
                      <span className="flex items-center gap-1 text-[10px] font-bold text-vsc-green">
                        <CheckCircle2 size={11} /> Pass
                      </span>
                    ) : partial ? (
                      <span className="flex items-center gap-1 text-[10px] font-bold text-yellow-500">
                        <Minus size={11} /> Partial
                      </span>
                    ) : (
                      <span className="flex items-center gap-1 text-[10px] font-bold text-accent">
                        <XCircle size={11} /> Fail
                      </span>
                    )}
                  </div>
                </div>

                {/* Inline anomalies for this question */}
                {qAnomalies.map((a, ai) => (
                  <div key={ai} className="flex items-start gap-3 px-5 py-2 bg-accent/5 border-t border-accent/10">
                    <ShieldAlert size={11} className="text-accent shrink-0 mt-0.5" />
                    <div className="flex items-baseline gap-2 min-w-0">
                      <span className="font-mono text-[9px] font-bold text-accent uppercase tracking-wider shrink-0">
                        {a.severity}
                      </span>
                      <p className="text-xs text-foreground/70 leading-relaxed truncate">{a.description}</p>
                    </div>
                  </div>
                ))}
              </div>
            );
          })
        )}
      </div>

      {/* Global anomalies — not tied to any specific question */}
      {globalAnomalies.length > 0 && (
        <div className="rounded-xl border border-accent/20 bg-accent/5 overflow-hidden">
          <div className="px-5 py-2.5 border-b border-accent/15 bg-accent/5">
            <span className="text-[10px] font-bold uppercase tracking-wider text-accent">Submission Warnings</span>
          </div>
          {globalAnomalies.map((a, i) => (
            <div key={i} className="flex items-start gap-3 px-5 py-3 border-b border-accent/10 last:border-0">
              <ShieldAlert size={13} className="text-accent shrink-0 mt-0.5" />
              <div className="flex items-baseline gap-2 min-w-0">
                <span className="font-mono text-[9px] font-bold text-accent uppercase tracking-wider shrink-0">
                  {a.severity}
                </span>
                <p className="text-sm text-foreground/80 leading-relaxed">{a.description}</p>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default SubmissionDetails;

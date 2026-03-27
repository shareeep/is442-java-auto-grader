import React, { useState, useMemo } from 'react';
import { Users, AlertTriangle, Activity, CheckCircle2, ChevronRight } from 'lucide-react';
import SubmissionDetails, { Submission } from './SubmissionDetails';

interface ResultsTableProps {
  data: {
    status?: string;
    submissions?: Submission[];
    message?: string;
  };
}

const ResultsTable: React.FC<ResultsTableProps> = ({ data }) => {
  const [selectedSubmission, setSelectedSubmission] = useState<Submission | null>(null);

  if (data.status === 'error') {
    return (
      <div className="px-5 py-4 rounded-xl border border-destructive/30 bg-destructive/10 text-destructive text-sm flex items-start gap-3">
        <AlertTriangle size={16} className="shrink-0 mt-0.5" />
        <div>
          <p className="font-semibold mb-1">Pipeline Error</p>
          <p className="font-mono text-xs opacity-80">{data.message}</p>
        </div>
      </div>
    );
  }

  const submissions = useMemo(() =>
    [...(data.submissions || [])].sort((a, b) => {
      const na = a.name || a.username || a.displayName || '';
      const nb = b.name || b.username || b.displayName || '';
      return na.localeCompare(nb);
    }),
    [data.submissions]
  );
  return (
    <div className="flex flex-col gap-5">

      {selectedSubmission ? (
        <SubmissionDetails submission={selectedSubmission} onBack={() => setSelectedSubmission(null)} />
      ) : (
        <div className="rounded-xl border border-border bg-card overflow-hidden">
          <div className="grid grid-cols-12 px-5 py-3 border-b border-border bg-secondary/50">
            <div className="col-span-5 text-[10px] font-bold uppercase tracking-wider text-muted-foreground">Student</div>
            <div className="col-span-4 text-[10px] font-bold uppercase tracking-wider text-muted-foreground">Status</div>
            <div className="col-span-3 text-[10px] font-bold uppercase tracking-wider text-muted-foreground text-right">Score</div>
          </div>

          <div className="flex flex-col">
            {submissions.map((s, i) => {
              const hasAnomalies = (s.anomalies?.length ?? 0) > 0;
              return (
                <div
                  key={i}
                  onClick={() => setSelectedSubmission(s)}
                  className="grid grid-cols-12 gap-4 px-5 py-3.5 border-b border-border/50 last:border-0 items-center transition-colors hover:bg-secondary/50 cursor-pointer"
                >
                  <div className="col-span-5 flex items-center gap-3">
                    <div className="w-8 h-8 rounded-full bg-secondary flex items-center justify-center text-muted-foreground shrink-0">
                      <Users size={14} />
                    </div>
                    <div className="flex flex-col truncate">
                      <span className="font-semibold text-sm text-foreground truncate">
                        {s.name || 'Unknown Student'}
                      </span>
                      <span className="font-mono text-[10px] text-muted-foreground truncate">
                        {s.username || s.displayName}
                      </span>
                    </div>
                  </div>

                  <div className="col-span-4 flex items-center">
                    {hasAnomalies ? (
                      <span className="flex items-center gap-1.5 px-2.5 py-1 bg-accent/10 text-accent text-[10px] font-bold uppercase tracking-wide rounded border border-accent/20">
                        <AlertTriangle size={10} /> Anomalies
                      </span>
                    ) : (
                      <span className="flex items-center gap-1.5 px-2.5 py-1 bg-vsc-green/10 text-vsc-green text-[10px] font-bold uppercase tracking-wide rounded border border-vsc-green/20">
                        <Activity size={10} /> Nominal
                      </span>
                    )}
                  </div>

                  <div className="col-span-3 flex justify-end items-center gap-3">
                    <div className="flex items-baseline gap-0.5">
                      <span className={`font-mono text-base font-bold ${s.totalScore === s.maxPossibleScore ? 'text-vsc-green' : 'text-primary'}`}>
                        {s.totalScore}
                      </span>
                      <span className="font-mono text-xs text-muted-foreground">/{s.maxPossibleScore}</span>
                    </div>
                    <ChevronRight size={14} className="text-muted-foreground" />
                  </div>
                </div>
              );
            })}

            {submissions.length === 0 && (
              <div className="py-12 text-center text-sm text-muted-foreground flex flex-col items-center gap-2">
                <CheckCircle2 size={24} className="opacity-20" />
                No submissions processed
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};

export default ResultsTable;

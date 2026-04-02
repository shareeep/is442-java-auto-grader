import React, { useState, useMemo } from 'react';
import { AlertTriangle, Activity, CheckCircle2, ChevronRight, Code, ChevronsUpDown, ChevronUp, ChevronDown } from 'lucide-react';
import SubmissionDetails, { Submission } from './SubmissionDetails';

interface ResultsTableProps {
  data: {
    status?: string;
    submissions?: Submission[];
    message?: string;
  };
}

type SortField = 'name' | 'score';
type SortDir = 'asc' | 'desc';

const ResultsTable: React.FC<ResultsTableProps> = ({ data }) => {
  const [selectedSubmission, setSelectedSubmission] = useState<Submission | null>(null);
  const [sortField, setSortField] = useState<SortField>('name');
  const [sortDir, setSortDir] = useState<SortDir>('asc');

  const handleSort = (field: SortField) => {
    if (sortField === field) {
      setSortDir(d => d === 'asc' ? 'desc' : 'asc');
    } else {
      setSortField(field);
      setSortDir('asc');
    }
  };

  const SortIcon = ({ field }: { field: SortField }) => {
    if (sortField !== field) return <ChevronsUpDown size={10} className="text-muted-foreground/40" />;
    return sortDir === 'asc' ? <ChevronUp size={10} /> : <ChevronDown size={10} />;
  };

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
      let cmp = 0;
      if (sortField === 'name') {
        const na = a.name || a.username || a.displayName || '';
        const nb = b.name || b.username || b.displayName || '';
        cmp = na.localeCompare(nb);
      } else {
        const pa = a.maxPossibleScore > 0 ? a.totalScore / a.maxPossibleScore : 0;
        const pb = b.maxPossibleScore > 0 ? b.totalScore / b.maxPossibleScore : 0;
        cmp = pa - pb;
      }
      return sortDir === 'asc' ? cmp : -cmp;
    }),
    [data.submissions, sortField, sortDir]
  );
  return (
    <div className="flex flex-col gap-5">

      {selectedSubmission ? (
        <SubmissionDetails submission={selectedSubmission} onBack={() => setSelectedSubmission(null)} />
      ) : (
        <div className="rounded-xl border border-border bg-card overflow-hidden">
          <div className="grid grid-cols-12 px-5 py-3 border-b border-border bg-secondary/50">
            <div className="col-span-1 text-[10px] font-bold uppercase tracking-wider text-muted-foreground">No.</div>
            <button
              onClick={() => handleSort('name')}
              className={`col-span-4 flex items-center gap-1 text-[10px] font-bold uppercase tracking-wider transition-colors ${sortField === 'name' ? 'text-foreground' : 'text-muted-foreground hover:text-foreground'}`}
            >
              Student <SortIcon field="name" />
            </button>
            <div className="col-span-4 text-[10px] font-bold uppercase tracking-wider text-muted-foreground">Status</div>
            <button
              onClick={() => handleSort('score')}
              className={`col-span-3 flex items-center justify-end gap-1 text-[10px] font-bold uppercase tracking-wider transition-colors ${sortField === 'score' ? 'text-foreground' : 'text-muted-foreground hover:text-foreground'}`}
            >
              Score <SortIcon field="score" />
            </button>
          </div>

          <div className="flex flex-col">
            {submissions.map((s, i) => {
              const hasAnomalies = (s.anomalies?.length ?? 0) > 0;
              const hasCompileError = s.anomalies?.some(a => a.description.startsWith('Compilation error')) ?? false;
              const hasSubmissionError = s.anomalies?.some(a => !a.description.startsWith('Compilation error')) ?? false;
              return (
                <div
                  key={i}
                  onClick={() => setSelectedSubmission(s)}
                  className="grid grid-cols-12 gap-4 px-5 py-3.5 border-b border-border/50 last:border-0 items-center transition-colors hover:bg-secondary/50 cursor-pointer"
                >
                  <div className="col-span-1 font-mono text-xs text-muted-foreground">{i + 1}</div>
                  <div className="col-span-4 flex items-center gap-3">
                    <div className="flex flex-col truncate">
                      <span className="font-semibold text-sm text-foreground truncate">
                        {s.name || 'Unknown Student'}
                      </span>
                      <span className="font-mono text-[10px] text-muted-foreground truncate">
                        {s.username || s.displayName}
                      </span>
                    </div>
                  </div>

                  <div className="col-span-4 flex items-center gap-1.5">
                    {!hasAnomalies && (
                      <span className="flex items-center gap-1.5 px-2.5 py-1 bg-vsc-green/10 text-vsc-green text-[10px] font-bold uppercase tracking-wide rounded border border-vsc-green/20">
                        <Activity size={10} /> No Error
                      </span>
                    )}
                    {hasCompileError && (
                      <span className="flex items-center gap-1.5 px-2.5 py-1 bg-destructive text-destructive-foreground text-[10px] font-bold uppercase tracking-wide rounded">
                        <Code size={10} /> Compilation Error
                      </span>
                    )}
                    {hasSubmissionError && (
                      <span className="flex items-center gap-1.5 px-2.5 py-1 bg-vsc-yellow/10 text-vsc-yellow text-[10px] font-bold uppercase tracking-wide rounded border border-vsc-yellow/20">
                        <AlertTriangle size={10} /> Submission Error
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

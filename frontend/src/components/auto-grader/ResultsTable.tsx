import React, { useState, useMemo, useRef } from 'react';
import { AlertTriangle, Activity, CheckCircle2, ChevronRight, Code, ChevronsUpDown, ChevronUp, ChevronDown, Search } from 'lucide-react';
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

const getSubmissionFlags = (submission: Submission) => {
  const hasAnomalies = (submission.anomalies?.length ?? 0) > 0;
  const hasCompileError = submission.anomalies?.some(a => a.description.startsWith('Compilation error')) ?? false;
  const hasSubmissionError = submission.anomalies?.some(a => !a.description.startsWith('Compilation error')) ?? false;
  return { hasAnomalies, hasCompileError, hasSubmissionError };
};

const SubmissionStatusBadges: React.FC<{ submission: Submission }> = ({ submission }) => {
  const { hasAnomalies, hasCompileError, hasSubmissionError } = getSubmissionFlags(submission);

  return (
    <div className="flex flex-wrap items-center gap-1.5">
      {!hasAnomalies && (
        <span className="flex items-center gap-1.5 rounded border border-vsc-green/20 bg-vsc-green/10 px-2.5 py-1 text-[10px] font-bold uppercase tracking-wide text-vsc-green">
          <Activity size={10} /> No Error
        </span>
      )}
      {hasCompileError && (
        <span className="flex items-center gap-1.5 rounded bg-destructive px-2.5 py-1 text-[10px] font-bold uppercase tracking-wide text-destructive-foreground">
          <Code size={10} /> Compilation Error
        </span>
      )}
      {hasSubmissionError && (
        <span className="flex items-center gap-1.5 rounded border border-vsc-yellow/20 bg-vsc-yellow/10 px-2.5 py-1 text-[10px] font-bold uppercase tracking-wide text-vsc-yellow">
          <AlertTriangle size={10} /> Submission Error
        </span>
      )}
    </div>
  );
};

const ResultsTable: React.FC<ResultsTableProps> = ({ data }) => {
  const [selectedSubmission, setSelectedSubmission] = useState<Submission | null>(null);
  const [sortField, setSortField] = useState<SortField>('name');
  const [sortDir, setSortDir] = useState<SortDir>('asc');
  const [search, setSearch] = useState('');
  const containerRef = useRef<HTMLDivElement>(null);
  const savedScroll = useRef(0);

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
    [...(data.submissions || [])]
      .filter(s => {
        if (!search) return true;
        const q = search.toLowerCase();
        return (s.name || '').toLowerCase().includes(q)
          || (s.username || '').toLowerCase().includes(q)
          || (s.displayName || '').toLowerCase().includes(q);
      })
      .sort((a, b) => {
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
    [data.submissions, sortField, sortDir, search]
  );
  return (
    <div className="flex flex-col gap-5" ref={containerRef}>

      {selectedSubmission ? (
        <SubmissionDetails
          submission={selectedSubmission}
          onBack={() => {
            setSelectedSubmission(null);
            requestAnimationFrame(() => {
              containerRef.current?.closest('.overflow-auto')?.scrollTo({ top: savedScroll.current });
            });
          }}
        />
      ) : (
        <>
          <div className="relative w-full sm:max-w-sm">
            <Search size={13} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground pointer-events-none" />
            <input
              type="search"
              placeholder="Search students..."
              value={search}
              onChange={e => setSearch(e.target.value)}
              className="w-full pl-8 pr-4 py-2 text-sm bg-card border border-border rounded-lg text-foreground placeholder:text-muted-foreground outline-none focus:border-primary/50"
            />
          </div>
          <div className="rounded-xl border border-border bg-card overflow-hidden">
            <div className="hidden grid-cols-12 border-b border-border bg-secondary/50 px-5 py-3 lg:grid">
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

            {submissions.length === 0 ? (
              <div className="flex flex-col items-center gap-2 py-12 text-center text-sm text-muted-foreground">
                <CheckCircle2 size={24} className="opacity-20" />
                No submissions processed
              </div>
            ) : (
              <>
                <div className="flex flex-col lg:hidden">
                  {submissions.map((s, i) => (
                    <button
                      key={i}
                      type="button"
                      onClick={() => {
                        savedScroll.current = containerRef.current?.closest('.overflow-auto')?.scrollTop ?? 0;
                        setSelectedSubmission(s);
                      }}
                      className="flex flex-col gap-3 border-b border-border/50 px-4 py-4 text-left transition-colors last:border-0 hover:bg-secondary/50"
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <div className="flex items-center gap-2">
                            <span className="font-mono text-[11px] text-muted-foreground">#{i + 1}</span>
                            <span className="truncate text-sm font-semibold text-foreground">
                              {s.name || 'Unknown Student'}
                            </span>
                          </div>
                          <p className="mt-1 truncate font-mono text-[11px] text-muted-foreground">
                            {s.username || s.displayName}
                          </p>
                        </div>
                        <div className="flex shrink-0 items-baseline gap-0.5">
                          <span className={`font-mono text-lg font-bold ${s.totalScore === s.maxPossibleScore ? 'text-vsc-green' : 'text-primary'}`}>
                            {s.totalScore}
                          </span>
                          <span className="font-mono text-xs text-muted-foreground">/{s.maxPossibleScore}</span>
                        </div>
                      </div>

                      <SubmissionStatusBadges submission={s} />

                      <div className="flex items-center justify-between text-xs text-muted-foreground">
                        <span>Tap to view details</span>
                        <ChevronRight size={14} />
                      </div>
                    </button>
                  ))}
                </div>

                <div className="hidden flex-col lg:flex">
                  {submissions.map((s, i) => (
                    <div
                      key={i}
                      onClick={() => {
                        savedScroll.current = containerRef.current?.closest('.overflow-auto')?.scrollTop ?? 0;
                        setSelectedSubmission(s);
                      }}
                      className="grid grid-cols-12 items-center gap-4 border-b border-border/50 px-5 py-3.5 transition-colors last:border-0 hover:bg-secondary/50 cursor-pointer"
                    >
                      <div className="col-span-1 font-mono text-xs text-muted-foreground">{i + 1}</div>
                      <div className="col-span-4 flex items-center gap-3">
                        <div className="flex flex-col truncate">
                          <span className="truncate text-sm font-semibold text-foreground">
                            {s.name || 'Unknown Student'}
                          </span>
                          <span className="truncate font-mono text-[10px] text-muted-foreground">
                            {s.username || s.displayName}
                          </span>
                        </div>
                      </div>

                      <div className="col-span-4 flex items-center gap-1.5">
                        <SubmissionStatusBadges submission={s} />
                      </div>

                      <div className="col-span-3 flex items-center justify-end gap-3">
                        <div className="flex items-baseline gap-0.5">
                          <span className={`font-mono text-base font-bold ${s.totalScore === s.maxPossibleScore ? 'text-vsc-green' : 'text-primary'}`}>
                            {s.totalScore}
                          </span>
                          <span className="font-mono text-xs text-muted-foreground">/{s.maxPossibleScore}</span>
                        </div>
                        <ChevronRight size={14} className="text-muted-foreground" />
                      </div>
                    </div>
                  ))}
                </div>
              </>
            )}
          </div>
        </>
      )}
    </div>
  );
};

export default ResultsTable;

import React, { useState, useRef, useEffect } from 'react';
import {
  FolderArchive, FileCode2, FileSpreadsheet, FileText,
  Play, History, ChevronDown, RotateCcw, Terminal,
  CheckCircle2, AlertCircle, Loader2, Users, ArrowRight,
  TrendingUp, BarChart2, TriangleAlert,
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import GradingTerminal from '../components/GradingTerminal';
import ResultsTable from '../components/ResultsTable';
import type { Submission } from '../components/SubmissionDetails';
import { listRunsOptions } from '../generated/@tanstack/react-query.gen';
import { getStudentCode } from '../api/client';
import { formatRunTimestamp } from '../lib/utils';

type Phase = 'upload' | 'grading' | 'results';

interface GradingResult {
  status: string;
  submissions: Submission[];
}

interface PastRun {
  id: string;
  timestamp: string;
  hasPdf: boolean;
  hasCsv: boolean;
  studentCount: number;
}

interface UploadCardProps {
  label: string;
  hint: string;
  icon: React.ReactNode;
  required?: boolean;
  isDirectory?: boolean;
  accept?: string;
  name: string;
  count: number;
  onChange: (files: File[]) => void;
}

const UploadCard: React.FC<UploadCardProps> = ({
  label, hint, icon, required, isDirectory, accept, name, count, onChange,
}) => {
  const inputRef = useRef<HTMLInputElement>(null);
  const dirProps = isDirectory ? { webkitdirectory: 'true', directory: 'true' } as any : {};

  return (
    <div
      onClick={() => inputRef.current?.click()}
      className={`relative flex items-center gap-4 px-5 py-4 rounded-xl border cursor-pointer transition-all duration-200 group ${
        count > 0
          ? 'border-primary/40 bg-primary/5 hover:border-primary/60'
          : 'border-border bg-card hover:border-border/80 hover:bg-card/80'
      }`}
    >
      <input
        ref={inputRef}
        type="file"
        name={name}
        multiple
        accept={accept}
        {...dirProps}
        className="hidden"
        onChange={e => onChange(Array.from(e.target.files || []))}
      />
      <div className={`shrink-0 transition-colors ${count > 0 ? 'text-primary' : 'text-muted-foreground group-hover:text-foreground'}`}>
        {icon}
      </div>
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <span className="text-sm font-semibold text-foreground">{label}</span>
          {required && (
            <span className="text-[10px] font-bold uppercase tracking-wider text-accent bg-accent/10 px-1.5 py-0.5 rounded">
              Required
            </span>
          )}
        </div>
        <p className="text-xs text-muted-foreground mt-0.5">{hint}</p>
      </div>
      <div className="shrink-0">
        {count > 0 ? (
          <span className="flex items-center gap-1.5 text-xs font-semibold text-primary">
            <CheckCircle2 size={14} />
            {count} {isDirectory ? 'files' : 'file'}
          </span>
        ) : (
          <span className="text-xs text-muted-foreground group-hover:text-foreground transition-colors">
            Choose {isDirectory ? 'folder' : 'file'}
          </span>
        )}
      </div>
    </div>
  );
};

const PastRunsDropdown: React.FC<{ runs: PastRun[]; loading: boolean; onLoad: (runId: string) => void }> = ({
  runs, loading, onLoad,
}) => {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen(o => !o)}
        className="flex items-center gap-2 px-3 py-2 text-sm font-medium rounded-lg border border-border bg-card hover:bg-secondary transition-colors text-muted-foreground hover:text-foreground"
      >
        <History size={14} />
        Past Runs
        {loading ? <Loader2 size={12} className="animate-spin" /> : <ChevronDown size={12} />}
      </button>

      {open && (
        <div className="absolute right-0 top-full mt-1 w-72 bg-card border border-border rounded-xl shadow-2xl z-50 overflow-hidden">
          <div className="px-3 py-2 border-b border-border">
            <span className="text-[10px] font-bold uppercase tracking-wider text-muted-foreground">Recent Sessions</span>
          </div>
          {runs.length === 0 ? (
            <div className="px-4 py-6 text-center text-sm text-muted-foreground">No past runs found</div>
          ) : (
            <div className="max-h-64 overflow-auto">
              {runs.map(run => (
                <button
                  key={run.id}
                  onClick={() => { onLoad(run.id); setOpen(false); }}
                  className="w-full flex items-center justify-between px-4 py-3 hover:bg-secondary transition-colors text-left border-b border-border/50 last:border-0"
                >
                  <div>
                    <p className="text-sm font-medium text-foreground">{formatRunTimestamp(run.timestamp)}</p>
                    <p className="text-xs text-muted-foreground flex items-center gap-1 mt-0.5">
                      <Users size={10} /> {run.studentCount} students
                    </p>
                  </div>
                  <div className="flex gap-1">
                    {run.hasPdf && (
                      <span className="text-[10px] px-1.5 py-0.5 bg-primary/10 text-primary rounded font-bold">PDF</span>
                    )}
                    {run.hasCsv && (
                      <span className="text-[10px] px-1.5 py-0.5 bg-vsc-green/10 text-vsc-green rounded font-bold">CSV</span>
                    )}
                  </div>
                </button>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
};

const StatCard: React.FC<{ icon: React.ReactNode; label: string; value: string; sub?: string }> = ({ icon, label, value, sub }) => (
  <div className="flex items-center gap-4 px-5 py-4 rounded-xl border border-border bg-card">
    <div className="text-primary">{icon}</div>
    <div>
      <p className="text-[10px] font-bold uppercase tracking-wider text-muted-foreground">{label}</p>
      <p className="text-xl font-bold text-foreground font-mono">{value}</p>
      {sub && <p className="text-[10px] text-muted-foreground mt-0.5">{sub}</p>}
    </div>
  </div>
);

const GraderWorkspace: React.FC = () => {
  const navigate = useNavigate();
  const [submissionFiles, setSubmissionFiles] = useState<File[]>([]);
  const [testerFiles, setTesterFiles] = useState<File[]>([]);
  const [scoresheetFiles, setScoresheetFiles] = useState<File[]>([]);
  const [examFiles, setExamFiles] = useState<File[]>([]);
  const [phase, setPhase] = useState<Phase>('upload');
  const [streamFormData, setStreamFormData] = useState<FormData | null>(null);
  const [result, setResult] = useState<GradingResult | null>(null);
  const [runId, setRunId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [terminalOpen, setTerminalOpen] = useState(true);

  const { data: pastRunsData, isLoading: runsLoading, refetch: refetchRuns } = useQuery(listRunsOptions());
  const pastRuns: PastRun[] = (pastRunsData as unknown as PastRun[]) ?? [];

  const canRun = submissionFiles.length > 0 && testerFiles.length > 0;

  const handleRun = () => {
    if (!canRun) return;
    const form = new FormData();
    submissionFiles.forEach(f => form.append('submissions', f, (f as any).webkitRelativePath || f.name));
    testerFiles.forEach(f => form.append('testers', f, (f as any).webkitRelativePath || f.name));
    if (scoresheetFiles.length > 0) form.append('scoresheet', scoresheetFiles[0], scoresheetFiles[0].name);
    setError(null);
    setResult(null);
    setStreamFormData(form);
    setPhase('grading');
  };

  const handleStreamComplete = (submissions: Submission[], completedRunId?: string) => {
    setResult({ status: 'success', submissions });
    setRunId(completedRunId ?? null);
    setPhase('results');
    refetchRuns();
  };

  const handleStreamError = (msg: string) => {
    setError(msg);
    setPhase('upload');
    setStreamFormData(null);
  };

  const handleReset = () => {
    setPhase('upload');
    setResult(null);
    setRunId(null);
    setError(null);
    setStreamFormData(null);
    setSubmissionFiles([]);
    setTesterFiles([]);
    setScoresheetFiles([]);
    setExamFiles([]);
    setTerminalOpen(true);
  };

  // Derived summary stats
  const submissions: Submission[] = result?.submissions ?? [];
  const avgPct = submissions.length > 0
    ? Math.round(submissions.reduce((s, x) => s + (x.maxPossibleScore > 0 ? x.totalScore / x.maxPossibleScore : 0), 0) / submissions.length * 100)
    : 0;
  const passCount = submissions.filter(x => x.maxPossibleScore > 0 && x.totalScore / x.maxPossibleScore >= 0.5).length;
  const anomalyCount = submissions.reduce((s, x) => s + (x.anomalies?.length ?? 0), 0);

  return (
    <div className="flex flex-col gap-6 max-w-5xl mx-auto w-full px-8 py-6 pb-16">
      <header className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-3xl font-outfit font-bold text-foreground tracking-tight">Auto-Grader</h1>
          <p className="text-muted-foreground text-sm mt-1">
            Compile, test, and grade student Java submissions automatically.
          </p>
        </div>
        <div className="flex items-center gap-2 shrink-0 mt-1">
          <PastRunsDropdown runs={pastRuns} loading={runsLoading} onLoad={id => navigate(`/results/${id}`)} />
          {phase === 'results' && !terminalOpen && (
            <button
              onClick={() => setTerminalOpen(true)}
              className="flex items-center gap-2 px-3 py-2 text-sm font-medium rounded-lg border border-border bg-card hover:bg-secondary transition-colors text-muted-foreground hover:text-foreground"
            >
              <Terminal size={14} /> Show Log
            </button>
          )}
          {phase === 'results' && (
            <button
              onClick={handleReset}
              className="flex items-center gap-2 px-3 py-2 text-sm font-medium rounded-lg border border-border bg-card hover:bg-secondary transition-colors text-muted-foreground hover:text-foreground"
            >
              <RotateCcw size={14} /> New Run
            </button>
          )}
        </div>
      </header>

      {error && (
        <div className="flex items-center gap-3 px-4 py-3 rounded-xl border border-destructive/30 bg-destructive/10 text-destructive text-sm">
          <AlertCircle size={16} className="shrink-0" />
          {error}
        </div>
      )}

      {phase === 'upload' && (
        <div className="flex flex-col gap-6">
          <div className="flex flex-col gap-3">
            <span className="text-xs font-bold uppercase tracking-wider text-muted-foreground">Required</span>
            <UploadCard
              label="Student Submissions"
              hint="Select the student-submissions folder containing individual ZIP files"
              icon={<FolderArchive size={20} />}
              required isDirectory name="submissions"
              count={submissionFiles.length}
              onChange={files => { setSubmissionFiles(files.filter(f => f.name.toLowerCase().endsWith('.zip'))); setError(null); }}
            />
            <UploadCard
              label="Test Cases (Testers)"
              hint="Select the Tester-Files folder with JUnit test files"
              icon={<FileCode2 size={20} />}
              required isDirectory name="testers"
              count={testerFiles.length}
              onChange={files => { setTesterFiles(files); setError(null); }}
            />
          </div>

          <div className="flex flex-col gap-3">
            <span className="text-xs font-bold uppercase tracking-wider text-muted-foreground">Optional</span>
            <UploadCard
              label="Grade Mapping (CSV)"
              hint="IS442-ScoreSheet.csv — maps student IDs to names"
              icon={<FileSpreadsheet size={20} />}
              accept=".csv" name="scoresheet"
              count={scoresheetFiles.length}
              onChange={files => { setScoresheetFiles(files); setError(null); }}
            />
            <UploadCard
              label="Exam PDF"
              hint="Upload the exam PDF to enable AI test generation"
              icon={<FileText size={20} />}
              accept=".pdf" name="exam"
              count={examFiles.length}
              onChange={files => { setExamFiles(files); setError(null); }}
            />
          </div>

          <div className="flex items-center justify-between pt-2">
            {!canRun && (
              <p className="text-xs text-muted-foreground">Add submissions and testers to enable grading.</p>
            )}
            <button
              onClick={handleRun}
              disabled={!canRun}
              className="flex items-center gap-2.5 px-6 py-3 bg-primary text-primary-foreground font-semibold text-sm rounded-xl shadow-lg shadow-primary/20 hover:bg-primary/90 transition-all duration-200 disabled:opacity-40 disabled:cursor-not-allowed disabled:shadow-none ml-auto"
            >
              <Play size={16} fill="currentColor" />
              Run Auto-Grader
            </button>
          </div>
        </div>
      )}

      {/* Terminal — mounted once grading starts */}
      {streamFormData && (
        <div className={phase === 'grading' ? '' : (terminalOpen ? 'mb-2' : 'hidden')}>
          <GradingTerminal
            formData={streamFormData}
            onComplete={handleStreamComplete}
            onError={handleStreamError}
            onClose={phase === 'results' ? () => setTerminalOpen(false) : undefined}
          />
        </div>
      )}

      {/* High-level results summary */}
      {phase === 'results' && result && (
        <div className="flex flex-col gap-4">
          {/* Summary stats */}
          <div className="grid grid-cols-3 gap-3">
            <StatCard icon={<TrendingUp size={20} />} label="Avg Score" value={`${avgPct}%`} sub={`${submissions.length} students`} />
            <StatCard icon={<BarChart2 size={20} />} label="Pass Rate" value={`${passCount}/${submissions.length}`} sub="scored ≥ 50%" />
            <StatCard icon={<TriangleAlert size={20} />} label="Anomalies" value={String(anomalyCount)} sub="across all students" />
          </div>

          {/* Score table */}
          <ResultsTable data={result} />

          {/* Deep-dive CTA */}
          {runId && (
            <div className="flex justify-end">
              <button
                onClick={() => navigate(`/results/${runId}`)}
                className="flex items-center gap-2 px-5 py-2.5 bg-primary text-primary-foreground font-semibold text-sm rounded-xl hover:bg-primary/90 transition-colors"
              >
                View Full Results
                <ArrowRight size={15} />
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default GraderWorkspace;

import React, { useState, useCallback, useEffect, useRef, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  ArrowLeft, Download, Eye, ChevronRight, ChevronDown, ChevronUp,
  FileCode2, Loader2, AlertCircle, User, CheckCircle2,
  TriangleAlert, X, File as FileIcon, ExternalLink, Clock,
} from 'lucide-react';
import { getStudentCode } from '../generated/sdk.gen';
import { getResultsOptions, listRunsOptions } from '../generated/@tanstack/react-query.gen';
import { formatRunTimestamp, pdfUrl } from '../lib/utils';

interface QResult {
  questionId: string;
  score: number;
  maxScore: number;
  compiled: boolean;
  executed: boolean;
  output: string;
  errorMessage: string;
}

interface Anomaly { severity: string; description: string; questionId?: string; }
interface Student {
  username: string; name: string; displayName: string;
  totalScore: number; maxPossibleScore: number;
  results: QResult[]; anomalies: Anomaly[];
}
interface OpenTab { key: string; label: string; content: string; }

// ── Inline code viewer with line numbers ─────────────────────────────────
interface CodeViewerProps {
  code: string;
}

const CodeViewer: React.FC<CodeViewerProps> = ({ code }) => {
  const [htmlLines, setHtmlLines] = useState<string[]>([]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const { codeToHtml } = await import('shiki');
        const html = await codeToHtml(code, {
          lang: 'java',
          theme: 'github-dark',
        });
        if (cancelled) return;
        const rawSegments = html.split('<span class="line">').slice(1);
        const lines = rawSegments.length > 0
          ? rawSegments.map(seg => {
              const close = seg.lastIndexOf('</span>');
              return close >= 0 ? seg.slice(0, close) : seg;
            })
          : code.split('\n').map(l => l.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'));
        setHtmlLines(lines);
      } catch {
        setHtmlLines(code.split('\n').map(l =>
          l.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        ));
      }
    })();
    return () => { cancelled = true; };
  }, [code]);

  return (
    <div className="flex flex-col flex-1 min-h-0 overflow-hidden">
      <div className="shrink-0 flex items-center gap-3 px-4 py-1 bg-[#0d1117] border-t border-white/[0.06] text-[10px] text-[#4d5566]">
        <span>Java</span>
        <span>·</span>
        <span>{htmlLines.length} lines</span>
      </div>

      <div className="flex-1 overflow-auto bg-[#0d1117]">
        {htmlLines.map((lineHtml, i) => {
          const ln = i + 1;
          return (
            <div key={i} className="flex items-stretch">
              <div className="w-14 shrink-0 flex items-center justify-end pr-3 select-none">
                <span className="text-right text-[12px] font-mono leading-6 min-w-[2ch] text-[#3d4451]">{ln}</span>
              </div>
              <div
                className="flex-1 pl-1 pr-8 leading-6 text-[13px] font-mono whitespace-pre"
                dangerouslySetInnerHTML={{ __html: lineHtml || '\u00a0' }}
              />
            </div>
          );
        })}

        {htmlLines.length === 0 && (
          <div className="flex items-center justify-center h-32">
            <Loader2 size={16} className="animate-spin text-[#4d5566]" />
          </div>
        )}
      </div>
    </div>
  );
};

// ── Test output panel (bottom of center pane) ─────────────────────────────
interface TestOutputPanelProps { qResult: QResult; }

const TestOutputPanel: React.FC<TestOutputPanelProps> = ({ qResult }) => {
  const [expanded, setExpanded] = useState(false);
  const [panelHeight, setPanelHeight] = useState(200);
  const isDragging = useRef(false);
  const dragStartY = useRef(0);
  const dragStartHeight = useRef(0);

  useEffect(() => {
    const onMove = (e: MouseEvent) => {
      if (!isDragging.current) return;
      const delta = dragStartY.current - e.clientY;
      setPanelHeight(Math.max(80, Math.min(600, dragStartHeight.current + delta)));
    };
    const onUp = () => { isDragging.current = false; };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
    return () => {
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
    };
  }, []);

  // compiled/executed are undefined on runs made before this feature was added — hide panel entirely
  if (qResult.compiled === undefined) return null;

  const rawContent = qResult.compiled === false ? qResult.errorMessage : qResult.output;
  const canExpand = !!rawContent?.trim();

  const handleResizeStart = (e: React.MouseEvent) => {
    isDragging.current = true;
    dragStartY.current = e.clientY;
    dragStartHeight.current = panelHeight;
    e.preventDefault();
    if (!expanded && canExpand) setExpanded(true);
  };

  const passedCount = (qResult.output ?? '').split('\n').filter(l => l.trim() === 'Passed').length;
  const totalTests = Math.round(qResult.maxScore);
  const earnedScore = Math.round(qResult.score);

  let statusColor: string;
  let statusText: string;
  let StatusIcon: React.ElementType;

  if (qResult.compiled === false) {
    statusColor = 'text-vsc-red';
    statusText = 'Compilation failed — no tests run';
    StatusIcon = AlertCircle;
  } else if (qResult.executed === false) {
    statusColor = 'text-vsc-orange';
    statusText = `Execution timed out${earnedScore > 0 ? ` · partial score ${earnedScore}/${totalTests}` : ''}`;
    StatusIcon = Clock;
  } else if (qResult.errorMessage?.trim()) {
    statusColor = 'text-vsc-red';
    const passed = passedCount > 0 ? passedCount : earnedScore;
    statusText = `Runtime error · ${passed}/${totalTests} test${totalTests !== 1 ? 's' : ''} passed`;
    StatusIcon = TriangleAlert;
  } else if (earnedScore === totalTests && totalTests > 0) {
    statusColor = 'text-vsc-green';
    statusText = `All ${totalTests} test${totalTests !== 1 ? 's' : ''} passed`;
    StatusIcon = CheckCircle2;
  } else {
    const passed = passedCount > 0 ? passedCount : earnedScore;
    statusColor = 'text-vsc-yellow';
    statusText = `${passed}/${totalTests} test${totalTests !== 1 ? 's' : ''} passed`;
    StatusIcon = TriangleAlert;
  }

  return (
    <div className="shrink-0 bg-[#161b22] flex flex-col">
      {/* Drag handle */}
      <div
        onMouseDown={handleResizeStart}
        className="h-[5px] cursor-ns-resize group flex items-center justify-center border-t border-white/[0.06] hover:border-white/20 transition-colors"
      >
        <div className="w-8 h-[2px] rounded-full bg-white/[0.12] group-hover:bg-white/30 transition-colors" />
      </div>
      {/* Header */}
      <button
        className="w-full flex items-center gap-2 px-4 py-2 text-xs hover:bg-white/[0.03] transition-colors disabled:cursor-default"
        onClick={() => canExpand && setExpanded(v => !v)}
        disabled={!canExpand}
      >
        <span className={`flex items-center gap-1.5 font-medium ${statusColor}`}>
          <StatusIcon size={11} />
          {statusText}
        </span>
        {canExpand && (
          <span className="ml-auto text-[#4d5566]">
            {expanded ? <ChevronDown size={11} /> : <ChevronUp size={11} />}
          </span>
        )}
      </button>
      {/* Content */}
      {expanded && canExpand && (
        <div
          className="overflow-auto border-t border-white/[0.06]"
          style={{ height: panelHeight }}
        >
          <pre className="px-4 py-3 text-[11px] font-mono text-[#8b949e] whitespace-pre-wrap leading-relaxed">
            {rawContent?.trim()}
          </pre>
        </div>
      )}
    </div>
  );
};

// ── Main page ──────────────────────────────────────────────────────────────
const RunResults: React.FC = () => {
  const { runId } = useParams<{ runId: string }>();
  const navigate = useNavigate();

  const { data: resultsData, isLoading: loading, error: queryError } = useQuery(
    getResultsOptions({ path: { id: runId! } })
  );
  const students: Student[] = (resultsData as unknown as Student[]) ?? [];
  const error = queryError?.message ?? null;

  const { data: runsData } = useQuery(listRunsOptions());
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const runMeta = (runsData as unknown as any[])?.find((r: any) => r.id === runId);
  const hasPlagiarism: boolean = runMeta?.hasPlagiarism ?? false;

  const [expandedStudents, setExpandedStudents] = useState<Set<string>>(new Set());
  const [studentCode, setStudentCode] = useState<Record<string, Record<string, string>>>({});
  const [loadingCode, setLoadingCode] = useState<Set<string>>(new Set());
  const [studentSearch, setStudentSearch] = useState('');
  const [openTabs, setOpenTabs] = useState<OpenTab[]>([]);
  const [activeTabKey, setActiveTabKey] = useState<string | null>(null);
  const [expandedScore, setExpandedScore] = useState<string | null>(null);

  const toggleStudent = useCallback(async (username: string) => {
    setExpandedStudents(prev => {
      const next = new Set(prev);
      next.has(username) ? next.delete(username) : next.add(username);
      return next;
    });
    if (!studentCode[username] && !loadingCode.has(username)) {
      setLoadingCode(prev => new Set(prev).add(username));
      try {
        const { data: code } = await getStudentCode({ path: { id: runId!, username }, throwOnError: true }) as { data: Record<string, string> };
        setStudentCode(prev => ({ ...prev, [username]: code }));
      } catch {
        setStudentCode(prev => ({ ...prev, [username]: {} }));
      } finally {
        setLoadingCode(prev => { const n = new Set(prev); n.delete(username); return n; });
      }
    }
  }, [runId, studentCode, loadingCode]);

  const openFile = (username: string, filename: string, content: string) => {
    const key = `${username}::${filename}`;
    if (!openTabs.find(t => t.key === key)) {
      const shortName = filename.split(/[\\/]/).pop() ?? filename;
      setOpenTabs(prev => [...prev, { key, label: `${username} / ${shortName}`, content }]);
    }
    setActiveTabKey(key);
  };

  const closeTab = (key: string) => {
    setOpenTabs(prev => {
      const next = prev.filter(t => t.key !== key);
      if (activeTabKey === key) setActiveTabKey(next[next.length - 1]?.key ?? null);
      return next;
    });
  };

  const activeTab = openTabs.find(t => t.key === activeTabKey) ?? null;

  // Find the QResult that corresponds to the currently active tab (for test output panel)
  const activeQResult = useMemo(() => {
    if (!activeTabKey) return null;
    const [username, filepath] = activeTabKey.split('::');
    const student = students.find(s => s.username === username);
    if (!student) return null;
    const filename = filepath.split(/[\\/]/).pop()?.replace(/\.java$/i, '').toLowerCase() ?? '';
    return student.results.find(r => r.questionId.toLowerCase() === filename) ?? null;
  }, [activeTabKey, students]);

  // Auto-expand the matching student in the Scores panel when the active file tab changes
  useEffect(() => {
    if (activeTabKey) {
      const username = activeTabKey.split('::')[0];
      setExpandedScore(username);
    }
  }, [activeTabKey]);

  if (loading) {
    return (
      <div className="flex h-[calc(100dvh-57px)] items-center justify-center bg-[#0d1117]">
        <Loader2 size={28} className="animate-spin text-primary/40" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex h-[calc(100dvh-57px)] flex-col items-center justify-center gap-3 bg-[#0d1117] text-destructive">
        <AlertCircle size={28} />
        <p className="text-sm">{error}</p>
        <button onClick={() => navigate(-1)} className="text-xs text-muted-foreground underline">Go back</button>
      </div>
    );
  }

  return (
    <div className="flex h-[calc(100dvh-57px)] min-h-[calc(100dvh-57px)] flex-col overflow-hidden bg-[#0d1117]">
      {/* ── Header ── */}
      <div className="shrink-0 flex flex-col gap-3 border-b border-white/[0.08] bg-[#161b22] px-4 py-3 sm:px-6 lg:px-8 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex flex-wrap items-center gap-2 sm:gap-3">
          <button
            onClick={() => navigate(-1)}
            className="flex items-center gap-1.5 text-xs text-[#8b949e] hover:text-[#c9d1d9] transition-colors"
          >
            <ArrowLeft size={13} /> Back
          </button>
          <span className="text-white/10">|</span>
          <span className="text-sm font-semibold text-[#c9d1d9]">
            {runId ? formatRunTimestamp(runId) : 'Run Results'}
          </span>
          <span className="text-[10px] px-2 py-0.5 bg-white/[0.06] rounded-full font-mono text-[#8b949e] border border-white/[0.08]">
            {students.length} students
          </span>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {runId && (
            <>
              <button
                onClick={() => window.open(pdfUrl(runId!, (runMeta as any)?.pdfFilename), '_blank')}
                className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg border border-white/[0.08] bg-white/[0.04] hover:bg-white/[0.08] transition-colors text-[#8b949e] hover:text-[#c9d1d9]"
              >
                <Eye size={12} /> PDF Report
              </button>
              <button
                onClick={() => window.open(`/api/reports/${runId}/csv`, '_blank')}
                className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg border border-white/[0.08] bg-white/[0.04] hover:bg-white/[0.08] transition-colors text-[#8b949e] hover:text-[#c9d1d9]"
              >
                <Download size={12} /> CSV
              </button>
              {hasPlagiarism && (
                <>
                  <button
                    onClick={() => window.open(`http://localhost:1996/?file=/api/reports/${runId}/plagiarism`, '_blank')}
                    className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg border border-white/[0.08] bg-white/[0.04] hover:bg-white/[0.08] transition-colors text-[#8b949e] hover:text-[#c9d1d9]"
                  >
                    <ExternalLink size={12} /> Plagiarism Report
                  </button>
                  <button
                    onClick={() => window.open(`/api/reports/${runId}/plagiarism`, '_blank')}
                    className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg border border-white/[0.08] bg-white/[0.04] hover:bg-white/[0.08] transition-colors text-[#8b949e] hover:text-[#c9d1d9]"
                    title="Download raw .jplag file"
                  >
                    <Download size={12} /> .jplag
                  </button>
                </>
              )}
            </>
          )}
        </div>
      </div>

      {/* ── Three-pane body ── */}
      <div className="flex flex-1 min-h-0 flex-col overflow-y-auto lg:flex-row lg:overflow-hidden">

        {/* ── Left: File Tree ── */}
        <div className="flex w-full max-h-[34vh] shrink-0 flex-col overflow-hidden border-b border-white/[0.06] bg-[#161b22] lg:max-h-none lg:w-[220px] lg:border-b-0 lg:border-r">
          <div className="px-3 py-2 border-b border-white/[0.06] shrink-0 flex flex-col gap-1.5">
            <span className="text-[10px] font-bold uppercase tracking-widest text-[#4d5566]">Files</span>
            <input
              type="search"
              placeholder="Filter students..."
              value={studentSearch}
              onChange={e => setStudentSearch(e.target.value)}
              className="w-full text-[11px] bg-[#0d1117] border border-white/[0.06] rounded px-2 py-1 text-[#8b949e] outline-none focus:border-white/20 placeholder:text-[#3d4451]"
            />
          </div>
          <div className="flex-1 overflow-auto py-1.5 font-mono text-xs">
            {students.filter(s =>
              (s.displayName || s.username).toLowerCase().includes(studentSearch.toLowerCase())
            ).map(s => {
              const expanded = expandedStudents.has(s.username);
              const files = studentCode[s.username] ?? {};
              const isLoading = loadingCode.has(s.username);
              return (
                <div key={s.username}>
                  <button
                    onClick={() => toggleStudent(s.username)}
                    className="flex items-center gap-1.5 w-full text-left px-2 py-1 transition-colors text-[#8b949e] hover:text-[#c9d1d9] hover:bg-white/[0.04]"
                  >
                    {expanded
                      ? <ChevronDown size={11} className="shrink-0 text-[#4d5566]" />
                      : <ChevronRight size={11} className="shrink-0 text-[#4d5566]" />}
                    <User size={11} className="shrink-0 text-[#e8e3d5]/50" />
                    <span className="truncate flex-1">{s.displayName || s.username}</span>
                    {isLoading && <Loader2 size={10} className="animate-spin shrink-0 text-[#4d5566]" />}
                  </button>
                  {expanded && !isLoading && Object.keys(files).length === 0 && (
                    <p className="pl-8 py-0.5 text-[10px] text-[#4d5566] italic">No code saved</p>
                  )}
                  {expanded && Object.entries(files).map(([path, content]) => {
                    const key = `${s.username}::${path}`;
                    const name = path.split(/[\\/]/).pop() ?? path;
                    const isActive = activeTabKey === key;
                    return (
                      <button
                        key={path}
                        onClick={() => openFile(s.username, path, content)}
                        className={`flex items-center gap-1.5 w-full text-left py-0.5 pl-8 pr-2 rounded-sm transition-colors ${
                          isActive
                            ? 'bg-[#e8e3d5]/10 text-[#e8e3d5]'
                            : 'text-[#8b949e] hover:text-[#c9d1d9] hover:bg-white/[0.04]'
                        }`}
                      >
                        <FileCode2 size={10} className="text-[#f78166] shrink-0" />
                        <span className="truncate">{name}</span>
                      </button>
                    );
                  })}
                </div>
              );
            })}
          </div>
        </div>

        {/* ── Center: Code Editor ── */}
        <div className="flex min-h-[320px] flex-1 flex-col overflow-hidden border-b border-white/[0.06] lg:border-b-0">
          {/* Tab bar */}
          <div className="shrink-0 flex items-center bg-[#161b22] border-b border-white/[0.06] overflow-x-auto">
            {openTabs.length === 0 ? (
              <div className="px-4 py-2 text-[10px] text-[#4d5566] italic">No files open</div>
            ) : openTabs.map(t => (
              <div
                key={t.key}
                onClick={() => setActiveTabKey(t.key)}
                className={`group flex items-center gap-1.5 px-3 py-2.5 border-r border-white/[0.06] cursor-pointer text-xs font-mono shrink-0 transition-colors relative ${
                  t.key === activeTabKey
                    ? 'bg-[#0d1117] text-[#c9d1d9]'
                    : 'text-[#4d5566] hover:bg-[#0d1117]/50 hover:text-[#8b949e]'
                }`}
              >
                {/* active tab indicator */}
                {t.key === activeTabKey && (
                  <span className="absolute top-0 left-0 right-0 h-[1.5px] bg-[#e8e3d5]" />
                )}
                <FileCode2 size={11} className="text-[#f78166] shrink-0" />
                <span className="max-w-[180px] truncate">{t.label}</span>
                <button
                  onClick={e => { e.stopPropagation(); closeTab(t.key); }}
                  className="ml-0.5 text-[#4d5566] hover:text-[#c9d1d9] opacity-0 group-hover:opacity-100 transition-all"
                >
                  <X size={11} />
                </button>
              </div>
            ))}
          </div>

          {/* Code viewer or empty state */}
          {activeTab ? (
            <div className="flex-1 flex flex-col overflow-hidden min-h-0">
              <CodeViewer code={activeTab.content} />
              {activeQResult && <TestOutputPanel qResult={activeQResult} />}
            </div>
          ) : (
            <div className="flex min-h-[260px] flex-col items-center justify-center flex-1 gap-3 text-center bg-[#0d1117]">
              <FileIcon size={32} className="text-[#3d4451]" />
              <div>
                <p className="text-sm text-[#4d5566]">Select a file to view</p>
                <p className="text-[10px] text-[#3d4451] mt-1">Click a student in the file tree to expand their files</p>
              </div>
            </div>
          )}
        </div>

        {/* ── Right: Scores Panel ── */}
        <div className="flex w-full max-h-[40vh] shrink-0 flex-col overflow-hidden border-t border-white/[0.06] bg-[#161b22] lg:max-h-none lg:w-[300px] lg:border-l lg:border-t-0">
          <div className="px-3 py-2 border-b border-white/[0.06] shrink-0">
            <span className="text-[10px] font-bold uppercase tracking-widest text-[#4d5566]">Scores</span>
          </div>
          <div className="flex-1 overflow-auto">
            {students.map(s => {
              const pct = s.maxPossibleScore > 0 ? s.totalScore / s.maxPossibleScore : 0;
              const pass = pct >= 0.5;
              const isOpen = expandedScore === s.username;
              return (
                <div key={s.username} className="border-b border-white/[0.06] last:border-0">
                  <button
                    onClick={() => setExpandedScore(isOpen ? null : s.username)}
                    className="w-full flex items-center justify-between px-3 py-2.5 hover:bg-white/[0.03] transition-colors text-left"
                  >
                    <div className="flex items-center gap-2 min-w-0 flex-1">
                      {isOpen
                        ? <ChevronDown size={11} className="text-[#4d5566] shrink-0" />
                        : <ChevronRight size={11} className="text-[#4d5566] shrink-0" />}
                      <div className="min-w-0">
                        <p className="text-xs font-medium text-[#c9d1d9] truncate">{s.name || s.displayName || s.username}</p>
                        <p className="text-[10px] text-[#4d5566] truncate font-mono">{s.username}</p>
                      </div>
                    </div>
                    <div className="flex items-center gap-2 shrink-0 ml-2">
                      {s.anomalies.length > 0 && (
                        <span className="flex items-center gap-0.5 text-[10px] text-vsc-yellow">
                          <TriangleAlert size={9} />{s.anomalies.length}
                        </span>
                      )}
                      <span className={`text-xs font-mono font-bold ${pass ? 'text-vsc-green' : 'text-vsc-red'}`}>
                        {s.totalScore}/{s.maxPossibleScore}
                      </span>
                    </div>
                  </button>

                  {isOpen && (() => {
                    const generalAnomalies = s.anomalies.filter(a => !a.questionId);
                    const anomalyByQ = s.anomalies.reduce<Record<string, Anomaly[]>>((acc, a) => {
                      if (a.questionId) {
                        (acc[a.questionId] ??= []).push(a);
                      }
                      return acc;
                    }, {});
                    const AnomalyRow = ({ a }: { a: Anomaly }) => (
                      <div className="flex items-start gap-1.5 text-[10px]">
                        {a.severity === 'ERROR'
                          ? <AlertCircle size={10} className="text-vsc-red shrink-0 mt-0.5" />
                          : a.severity === 'WARNING'
                            ? <TriangleAlert size={10} className="text-vsc-yellow shrink-0 mt-0.5" />
                            : <CheckCircle2 size={10} className="text-[#4d5566] shrink-0 mt-0.5" />}
                        <span className="text-[#8b949e] leading-tight">{a.description}</span>
                      </div>
                    );
                    return (
                      <div className="px-3 pb-3 space-y-1.5">
                        {/* General (non-question) anomalies at top */}
                        {generalAnomalies.length > 0 && (
                          <div className="space-y-1 pb-1.5 mb-0.5 border-b border-white/[0.06]">
                            {generalAnomalies.map((a, i) => <AnomalyRow key={i} a={a} />)}
                          </div>
                        )}
                        {/* Question rows with their own anomalies underneath */}
                        {s.results.map(r => {
                          const qPct = r.maxScore > 0 ? r.score / r.maxScore : 0;
                          const qid = r.questionId.toLowerCase();
                          const activeFilename = activeTabKey?.split('::')[1]?.split(/[\\/]/).pop()?.replace(/\.java$/i, '').toLowerCase() ?? '';
                          const isActiveQ = expandedScore === s.username && activeFilename === qid;
                          const qAnomalies = anomalyByQ[r.questionId] ?? [];
                          return (
                            <div key={r.questionId}>
                              <div
                                className={`flex items-center justify-between text-xs gap-2 rounded px-1.5 py-0.5 -mx-1.5 transition-colors ${isActiveQ ? 'bg-white/[0.07] border-l-2 border-[#e8e3d5]/60 pl-2' : ''}`}
                              >
                                <span className={`font-mono w-8 shrink-0 ${isActiveQ ? 'text-[#e8e3d5]' : 'text-[#4d5566]'}`}>{r.questionId}</span>
                                <div className="flex-1 h-1.5 bg-white/[0.06] rounded-full overflow-hidden">
                                  <div
                                    className={`h-full rounded-full transition-all ${qPct === 1 ? 'bg-vsc-green' : qPct > 0 ? 'bg-[#e8e3d5]' : 'bg-vsc-red/40'}`}
                                    style={{ width: `${qPct * 100}%` }}
                                  />
                                </div>
                                <span className={`font-mono text-right w-10 shrink-0 ${qPct === 1 ? 'text-vsc-green' : qPct > 0 ? 'text-[#c9d1d9]' : 'text-vsc-red'}`}>
                                  {r.score}/{r.maxScore}
                                </span>
                              </div>
                              {qAnomalies.length > 0 && (
                                <div className="ml-2 mt-0.5 mb-1 space-y-0.5 pl-2 border-l border-white/[0.08]">
                                  {qAnomalies.map((a, i) => <AnomalyRow key={i} a={a} />)}
                                </div>
                              )}
                            </div>
                          );
                        })}
                      </div>
                    );
                  })()}
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
};

export default RunResults;

import React, { useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  ArrowLeft, Download, Eye, ChevronRight, ChevronDown,
  FileCode2, Loader2, AlertCircle, User, CheckCircle2,
  TriangleAlert, X, File as FileIcon,
} from 'lucide-react';
import { CodeEditor } from '../components/ui/code-editor';
import { getStudentCode } from '../api/client';
import { getResultsOptions } from '../generated/@tanstack/react-query.gen';
import { formatRunTimestamp } from '../lib/utils';

interface QResult { questionId: string; score: number; maxScore: number; }
interface Anomaly { severity: string; description: string; }
interface Student {
  username: string; name: string; displayName: string;
  totalScore: number; maxPossibleScore: number;
  results: QResult[]; anomalies: Anomaly[];
}

interface OpenTab { key: string; label: string; content: string; }

const RunResults: React.FC = () => {
  const { runId } = useParams<{ runId: string }>();
  const navigate = useNavigate();

  const { data: resultsData, isLoading: loading, error: queryError } = useQuery(
    getResultsOptions({ path: { id: runId! } })
  );
  const students: Student[] = (resultsData as unknown as Student[]) ?? [];
  const error = queryError?.message ?? null;

  // File tree state
  const [expandedStudents, setExpandedStudents] = useState<Set<string>>(new Set());
  const [studentCode, setStudentCode] = useState<Record<string, Record<string, string>>>({});
  const [loadingCode, setLoadingCode] = useState<Set<string>>(new Set());

  // Editor state
  const [openTabs, setOpenTabs] = useState<OpenTab[]>([]);
  const [activeTabKey, setActiveTabKey] = useState<string | null>(null);

  // Scores panel state
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
        const code = await getStudentCode(runId!, username);
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

  if (loading) {
    return (
      <div className="flex items-center justify-center h-full">
        <Loader2 size={28} className="animate-spin text-primary/40" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center h-full gap-3 text-destructive">
        <AlertCircle size={28} />
        <p className="text-sm">{error}</p>
        <button onClick={() => navigate(-1)} className="text-xs text-muted-foreground underline">Go back</button>
      </div>
    );
  }

  return (
    <div className="flex flex-col h-full overflow-hidden">
      {/* ── Header ── */}
      <div className="shrink-0 flex items-center justify-between px-4 py-2 border-b border-border bg-card">
        <div className="flex items-center gap-3">
          <button
            onClick={() => navigate(-1)}
            className="flex items-center gap-1.5 text-xs text-muted-foreground hover:text-foreground transition-colors"
          >
            <ArrowLeft size={14} /> Back
          </button>
          <span className="text-border">|</span>
          <span className="text-sm font-semibold text-foreground">
            {runId ? formatRunTimestamp(runId) : 'Run Results'}
          </span>
          <span className="text-[10px] px-2 py-0.5 bg-secondary rounded font-mono text-muted-foreground">
            {students.length} students
          </span>
        </div>
        <div className="flex items-center gap-2">
          {runId && (
            <>
              <button
                onClick={() => window.open(`/api/reports/${runId}/pdf`, '_blank')}
                className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg border border-border bg-card hover:bg-secondary transition-colors text-muted-foreground hover:text-foreground"
              >
                <Eye size={13} /> PDF Report
              </button>
              <button
                onClick={() => window.open(`/api/reports/${runId}/csv`, '_blank')}
                className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg border border-border bg-card hover:bg-secondary transition-colors text-muted-foreground hover:text-foreground"
              >
                <Download size={13} /> CSV
              </button>
            </>
          )}
        </div>
      </div>

      {/* ── Three-pane body ── */}
      <div className="flex flex-1 overflow-hidden">

        {/* ── Left: File Tree (220px) ── */}
        <div className="w-[220px] shrink-0 border-r border-border bg-card flex flex-col overflow-hidden">
          <div className="px-3 py-2 border-b border-border shrink-0">
            <span className="text-[10px] font-bold uppercase tracking-wider text-muted-foreground">Files</span>
          </div>
          <div className="flex-1 overflow-auto p-1.5 font-mono text-xs">
            {students.map(s => {
              const expanded = expandedStudents.has(s.username);
              const files = studentCode[s.username] ?? {};
              const isLoading = loadingCode.has(s.username);
              return (
                <div key={s.username}>
                  <button
                    onClick={() => toggleStudent(s.username)}
                    className="flex items-center gap-1.5 w-full text-left px-2 py-1 rounded transition-colors text-vsc-text-dim hover:text-vsc-text hover:bg-white/5"
                  >
                    {expanded ? <ChevronDown size={11} className="shrink-0" /> : <ChevronRight size={11} className="shrink-0" />}
                    <User size={11} className="text-primary/60 shrink-0" />
                    <span className="truncate flex-1">{s.displayName || s.username}</span>
                    {isLoading && <Loader2 size={10} className="animate-spin shrink-0" />}
                  </button>
                  {expanded && !isLoading && Object.keys(files).length === 0 && (
                    <p className="pl-7 py-0.5 text-[10px] text-muted-foreground/50 italic">No code saved</p>
                  )}
                  {expanded && Object.entries(files).map(([path, content]) => {
                    const key = `${s.username}::${path}`;
                    const name = path.split(/[\\/]/).pop() ?? path;
                    return (
                      <button
                        key={path}
                        onClick={() => openFile(s.username, path, content)}
                        className={`flex items-center gap-1.5 w-full text-left px-2 py-0.5 rounded transition-colors ${
                          activeTabKey === key
                            ? 'bg-primary/15 text-primary'
                            : 'text-vsc-text-dim hover:text-vsc-text hover:bg-white/5'
                        }`}
                        style={{ paddingLeft: '24px' }}
                      >
                        <FileCode2 size={10} className="text-vsc-orange shrink-0" />
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
        <div className="flex-1 flex flex-col overflow-hidden">
          {/* Tab bar */}
          {openTabs.length > 0 && (
            <div className="flex items-center bg-[#161b22] border-b border-[#30363d] overflow-x-auto shrink-0">
              {openTabs.map(t => (
                <div
                  key={t.key}
                  onClick={() => setActiveTabKey(t.key)}
                  className={`flex items-center gap-1.5 px-3 py-2 border-r border-[#30363d] cursor-pointer text-xs font-mono shrink-0 transition-colors ${
                    t.key === activeTabKey
                      ? 'bg-[#0d1117] text-[#c9d1d9]'
                      : 'text-[#8b949e] hover:bg-[#0d1117]/60 hover:text-[#c9d1d9]'
                  }`}
                >
                  <FileCode2 size={11} className="text-vsc-orange shrink-0" />
                  <span className="max-w-[160px] truncate">{t.label.split('/').pop()?.trim() ?? t.label}</span>
                  <button
                    onClick={e => { e.stopPropagation(); closeTab(t.key); }}
                    className="ml-0.5 text-[#8b949e]/50 hover:text-[#c9d1d9] transition-colors"
                  >
                    <X size={11} />
                  </button>
                </div>
              ))}
            </div>
          )}

          {activeTab ? (
            <CodeEditor
              lang="java"
              writing={false}
              header={false}
              copyButton
              className="rounded-none border-none flex-1 min-h-0"
            >
              {activeTab.content}
            </CodeEditor>
          ) : (
            <div className="flex flex-col items-center justify-center flex-1 gap-3 text-center bg-[#0d1117]">
              <FileIcon size={28} className="text-muted-foreground/20" />
              <p className="text-xs text-muted-foreground">Select a file from the tree to view</p>
            </div>
          )}
        </div>

        {/* ── Right: Scores Panel (320px) ── */}
        <div className="w-[320px] shrink-0 border-l border-border bg-card flex flex-col overflow-hidden">
          <div className="px-3 py-2 border-b border-border shrink-0">
            <span className="text-[10px] font-bold uppercase tracking-wider text-muted-foreground">Scores</span>
          </div>
          <div className="flex-1 overflow-auto">
            {students.map(s => {
              const pct = s.maxPossibleScore > 0 ? s.totalScore / s.maxPossibleScore : 0;
              const pass = pct >= 0.5;
              const isOpen = expandedScore === s.username;
              return (
                <div key={s.username} className="border-b border-border/50 last:border-0">
                  <button
                    onClick={() => setExpandedScore(isOpen ? null : s.username)}
                    className="w-full flex items-center justify-between px-3 py-2.5 hover:bg-secondary/50 transition-colors text-left"
                  >
                    <div className="flex items-center gap-2 min-w-0">
                      {isOpen ? <ChevronDown size={12} className="text-muted-foreground shrink-0" /> : <ChevronRight size={12} className="text-muted-foreground shrink-0" />}
                      <span className="text-xs font-medium text-foreground truncate">{s.displayName || s.username}</span>
                    </div>
                    <div className="flex items-center gap-2 shrink-0 ml-2">
                      {s.anomalies.length > 0 && (
                        <span className="flex items-center gap-0.5 text-[10px] text-vsc-yellow">
                          <TriangleAlert size={10} />{s.anomalies.length}
                        </span>
                      )}
                      <span className={`text-xs font-mono font-bold ${pass ? 'text-vsc-green' : 'text-vsc-red'}`}>
                        {s.totalScore}/{s.maxPossibleScore}
                      </span>
                    </div>
                  </button>

                  {isOpen && (
                    <div className="px-3 pb-3 space-y-1.5">
                      {/* Per-question rows */}
                      {s.results.map(r => {
                        const qPct = r.maxScore > 0 ? r.score / r.maxScore : 0;
                        return (
                          <div key={r.questionId} className="flex items-center justify-between text-xs">
                            <span className="font-mono text-muted-foreground w-10">{r.questionId}</span>
                            <div className="flex-1 mx-2 h-1.5 bg-secondary rounded-full overflow-hidden">
                              <div
                                className={`h-full rounded-full transition-all ${qPct === 1 ? 'bg-vsc-green' : qPct > 0 ? 'bg-primary' : 'bg-vsc-red/40'}`}
                                style={{ width: `${qPct * 100}%` }}
                              />
                            </div>
                            <span className={`font-mono text-right w-12 ${qPct === 1 ? 'text-vsc-green' : qPct > 0 ? 'text-foreground' : 'text-vsc-red'}`}>
                              {r.score}/{r.maxScore}
                            </span>
                          </div>
                        );
                      })}
                      {/* Anomalies */}
                      {s.anomalies.length > 0 && (
                        <div className="mt-2 space-y-1">
                          {s.anomalies.map((a, i) => (
                            <div key={i} className="flex items-start gap-1.5 text-[10px]">
                              {a.severity === 'ERROR'
                                ? <AlertCircle size={10} className="text-vsc-red shrink-0 mt-0.5" />
                                : a.severity === 'WARNING'
                                  ? <TriangleAlert size={10} className="text-vsc-yellow shrink-0 mt-0.5" />
                                  : <CheckCircle2 size={10} className="text-muted-foreground shrink-0 mt-0.5" />
                              }
                              <span className="text-muted-foreground leading-tight">{a.description}</span>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  )}
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

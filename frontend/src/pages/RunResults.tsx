import React, { useState, useCallback, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  ArrowLeft, Download, Eye, ChevronRight, ChevronDown,
  FileCode2, Loader2, AlertCircle, User, CheckCircle2,
  TriangleAlert, X, File as FileIcon, Plus, MessageSquare,
} from 'lucide-react';
import { getStudentCode } from '../generated/sdk.gen';
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
interface LineComment { text: string; timestamp: string; }

// ── Inline code viewer with line numbers + professor comments ──────────────
interface CodeViewerProps {
  code: string;
  comments: Record<number, LineComment[]>;
  onCommentsChange: (updater: (prev: Record<number, LineComment[]>) => Record<number, LineComment[]>) => void;
}

const CodeViewer: React.FC<CodeViewerProps> = ({ code, comments, onCommentsChange }) => {
  const [htmlLines, setHtmlLines] = useState<string[]>([]);
  const [addingComment, setAddingComment] = useState<number | null>(null);
  const [draft, setDraft] = useState('');
  const [hovered, setHovered] = useState<number | null>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

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
        // Extract per-line HTML: split on the line-open tag, then strip the
        // line-closing </span> using lastIndexOf (non-greedy regex wrongly
        // stops at the first inner token </span>).
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

  useEffect(() => {
    if (addingComment !== null) textareaRef.current?.focus();
  }, [addingComment]);

  const submit = (line: number) => {
    if (!draft.trim()) return;
    onCommentsChange(prev => ({
      ...prev,
      [line]: [...(prev[line] ?? []), {
        text: draft.trim(),
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      }],
    }));
    setDraft('');
    setAddingComment(null);
  };

  const deleteComment = (line: number, idx: number) => {
    onCommentsChange(prev => ({
      ...prev,
      [line]: (prev[line] ?? []).filter((_, j) => j !== idx),
    }));
  };

  const totalComments = Object.values(comments).reduce((s, arr) => s + arr.length, 0);

  return (
    <div className="flex flex-col flex-1 min-h-0 overflow-hidden">
      {/* mini status bar */}
      <div className="shrink-0 flex items-center gap-3 px-4 py-1 bg-[#0d1117] border-t border-white/[0.06] text-[10px] text-[#4d5566]">
        <span>Java</span>
        <span>·</span>
        <span>{htmlLines.length} lines</span>
        {totalComments > 0 && (
          <>
            <span>·</span>
            <span className="flex items-center gap-1 text-vsc-yellow">
              <MessageSquare size={9} /> {totalComments} comment{totalComments !== 1 ? 's' : ''}
            </span>
          </>
        )}
        <span className="ml-auto">Hover a line · click <span className="text-[#e8e3d5]">+</span> to annotate</span>
      </div>

      {/* code lines */}
      <div className="flex-1 overflow-auto bg-[#0d1117]">
        {htmlLines.map((lineHtml, i) => {
          const ln = i + 1;
          const lineComments = comments[ln] ?? [];
          const isAdding = addingComment === ln;
          const isHov = hovered === ln;
          return (
            <div key={i}>
              <div
                className={`flex items-stretch group ${isHov || isAdding ? 'bg-white/[0.03]' : ''}`}
                onMouseEnter={() => setHovered(ln)}
                onMouseLeave={() => setHovered(null)}
              >
                {/* Gutter */}
                <div className="w-14 shrink-0 flex items-center justify-end gap-1.5 pr-3 select-none">
                  <button
                    onClick={() => { setAddingComment(ln); setDraft(''); }}
                    className={`transition-all ${isHov || isAdding ? 'opacity-100' : 'opacity-0'} text-[#e8e3d5] hover:scale-110`}
                    title="Add comment"
                  >
                    <Plus size={10} />
                  </button>
                  <span className="text-[#3d4451] text-right text-[12px] font-mono leading-6 min-w-[2ch]">{ln}</span>
                </div>
                {/* Line content */}
                <div
                  className="flex-1 pl-1 pr-8 leading-6 text-[13px] font-mono whitespace-pre"
                  dangerouslySetInnerHTML={{ __html: lineHtml || '\u00a0' }}
                />
                {/* Comment indicator */}
                {lineComments.length > 0 && !isAdding && (
                  <div className="shrink-0 flex items-center pr-3">
                    <span className="flex items-center gap-1 text-[10px] text-vsc-yellow/60">
                      <MessageSquare size={9} /> {lineComments.length}
                    </span>
                  </div>
                )}
              </div>

              {/* Existing comments */}
              {lineComments.map((c, ci) => (
                <div
                  key={ci}
                  className="mx-4 my-1.5 rounded-lg border border-vsc-yellow/20 bg-vsc-yellow/5"
                >
                  <div className="flex items-center gap-2 px-3 pt-2.5 pb-1">
                    <span className="text-[10px] font-bold bg-vsc-yellow/20 text-vsc-yellow px-1.5 py-0.5 rounded-md tracking-wide">
                      PROF
                    </span>
                    <span className="text-[10px] text-[#4d5566]">{c.timestamp}</span>
                    <button
                      onClick={() => deleteComment(ln, ci)}
                      className="ml-auto text-[#4d5566] hover:text-[#8b949e] transition-colors"
                    >
                      <X size={10} />
                    </button>
                  </div>
                  <p className="px-3 pb-2.5 text-xs text-[#c9d1d9] leading-relaxed">{c.text}</p>
                </div>
              ))}

              {/* Add comment box */}
              {isAdding && (
                <div className="mx-4 my-1.5 rounded-lg border border-[#e8e3d5]/30 bg-[#e8e3d5]/5">
                  <div className="flex items-center gap-2 px-3 pt-2.5 pb-1">
                    <span className="text-[10px] font-bold bg-[#e8e3d5]/20 text-[#e8e3d5] px-1.5 py-0.5 rounded-md tracking-wide">
                      PROF
                    </span>
                    <span className="text-[10px] text-[#4d5566]">line {ln}</span>
                  </div>
                  <textarea
                    ref={textareaRef}
                    value={draft}
                    onChange={e => setDraft(e.target.value)}
                    onKeyDown={e => {
                      if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) submit(ln);
                      if (e.key === 'Escape') setAddingComment(null);
                    }}
                    placeholder="Leave a comment… (⌘↵ to submit, Esc to cancel)"
                    className="w-full bg-transparent text-xs text-[#c9d1d9] placeholder:text-[#4d5566] resize-none outline-none min-h-[64px] px-3 pb-2 font-sans leading-relaxed"
                  />
                  <div className="flex justify-end gap-2 px-3 pb-2.5">
                    <button
                      onClick={() => setAddingComment(null)}
                      className="text-[10px] text-[#4d5566] hover:text-[#8b949e] transition-colors px-2 py-1"
                    >
                      Cancel
                    </button>
                    <button
                      onClick={() => submit(ln)}
                      disabled={!draft.trim()}
                      className="text-[10px] bg-[#e8e3d5] text-[#0d1117] px-2.5 py-1 rounded-md font-semibold disabled:opacity-40 transition-opacity"
                    >
                      Comment
                    </button>
                  </div>
                </div>
              )}
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

// ── Main page ──────────────────────────────────────────────────────────────
const RunResults: React.FC = () => {
  const { runId } = useParams<{ runId: string }>();
  const navigate = useNavigate();

  const { data: resultsData, isLoading: loading, error: queryError } = useQuery(
    getResultsOptions({ path: { id: runId! } })
  );
  const students: Student[] = (resultsData as unknown as Student[]) ?? [];
  const error = queryError?.message ?? null;

  const [expandedStudents, setExpandedStudents] = useState<Set<string>>(new Set());
  const [studentCode, setStudentCode] = useState<Record<string, Record<string, string>>>({});
  const [loadingCode, setLoadingCode] = useState<Set<string>>(new Set());
  const [studentSearch, setStudentSearch] = useState('');
  const [openTabs, setOpenTabs] = useState<OpenTab[]>([]);
  const [activeTabKey, setActiveTabKey] = useState<string | null>(null);
  const [expandedScore, setExpandedScore] = useState<string | null>(null);
  // comments keyed by tabKey → line number → comments array (survives tab switches)
  const [allComments, setAllComments] = useState<Record<string, Record<number, LineComment[]>>>({});

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

  // Auto-expand the matching student in the Scores panel when the active file tab changes
  useEffect(() => {
    if (activeTabKey) {
      const username = activeTabKey.split('::')[0];
      setExpandedScore(username);
    }
  }, [activeTabKey]);

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
    <div className="flex flex-col h-full overflow-hidden bg-[#0d1117]">
      {/* ── Header ── */}
      <div className="shrink-0 flex items-center justify-between px-4 py-2.5 border-b border-white/[0.08] bg-[#161b22]">
        <div className="flex items-center gap-3">
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
        <div className="flex items-center gap-2">
          {runId && (
            <>
              <button
                onClick={() => window.open(`/api/reports/${runId}/pdf`, '_blank')}
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
            </>
          )}
        </div>
      </div>

      {/* ── Three-pane body ── */}
      <div className="flex flex-1 overflow-hidden">

        {/* ── Left: File Tree ── */}
        <div className="w-[220px] shrink-0 border-r border-white/[0.06] bg-[#161b22] flex flex-col overflow-hidden">
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
        <div className="flex-1 flex flex-col overflow-hidden">
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
            <CodeViewer
              code={activeTab.content}
              comments={allComments[activeTab.key] ?? {}}
              onCommentsChange={updater =>
                setAllComments(prev => ({
                  ...prev,
                  [activeTab.key]: updater(prev[activeTab.key] ?? {}),
                }))
              }
            />
          ) : (
            <div className="flex flex-col items-center justify-center flex-1 gap-3 text-center bg-[#0d1117]">
              <FileIcon size={32} className="text-[#3d4451]" />
              <div>
                <p className="text-sm text-[#4d5566]">Select a file to view</p>
                <p className="text-[10px] text-[#3d4451] mt-1">Click a student in the file tree to expand their files</p>
              </div>
            </div>
          )}
        </div>

        {/* ── Right: Scores Panel ── */}
        <div className="w-[300px] shrink-0 border-l border-white/[0.06] bg-[#161b22] flex flex-col overflow-hidden">
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

                  {isOpen && (
                    <div className="px-3 pb-3 space-y-1.5">
                      {s.results.map(r => {
                        const qPct = r.maxScore > 0 ? r.score / r.maxScore : 0;
                        const qid = r.questionId.toLowerCase();
                        const activeFilename = activeTabKey?.split('::')[1]?.split(/[\\/]/).pop()?.replace(/\.java$/i, '').toLowerCase() ?? '';
                        const isActiveQ = expandedScore === s.username && activeFilename === qid;
                        return (
                          <div
                            key={r.questionId}
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
                        );
                      })}
                      {s.anomalies.length > 0 && (
                        <div className="mt-2 space-y-1 pt-2 border-t border-white/[0.06]">
                          {s.anomalies.map((a, i) => (
                            <div key={i} className="flex items-start gap-1.5 text-[10px]">
                              {a.severity === 'ERROR'
                                ? <AlertCircle size={10} className="text-vsc-red shrink-0 mt-0.5" />
                                : a.severity === 'WARNING'
                                  ? <TriangleAlert size={10} className="text-vsc-yellow shrink-0 mt-0.5" />
                                  : <CheckCircle2 size={10} className="text-[#4d5566] shrink-0 mt-0.5" />}
                              <span className="text-[#8b949e] leading-tight">{a.description}</span>
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

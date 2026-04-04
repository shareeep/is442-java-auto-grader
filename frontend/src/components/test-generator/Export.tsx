import React, { useState, useEffect } from 'react';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';
import {
  Download, ChevronLeft, CheckCircle2, AlertCircle, FileText, Zap,
  Loader2, ChevronDown, ChevronUp, Trash2, ArrowRight
} from 'lucide-react';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';
import { useNavigate } from 'react-router-dom';
import { save, getTester } from '@/generated/sdk.gen';
import { useWizardStore } from '../../store/wizardStore';
import { useShallow } from 'zustand/react/shallow';
import { toast } from '@/components/ui/toast';

interface FinalizeExportProps {
  onBack: () => void;
}

// Remove the N-th generated test case block from Java code.
// Blocks are identified by lines containing exactly 8-space-indented `{` (top-level inside grade()).
// We only look after the "Generated test cases" marker so original test cases are untouched.
function removeNthGeneratedBlock(code: string, blockIdx: number): string {
  const genMarker = '// ── Generated test cases';
  const markerPos = code.indexOf(genMarker);
  const lines = code.split('\n');

  // Determine which line the marker is on; scan for blocks only after it
  let scanFrom = 0;
  if (markerPos !== -1) {
    scanFrom = code.substring(0, markerPos).split('\n').length - 1;
  }

  const blocks: Array<{ start: number; end: number }> = [];
  for (let i = scanFrom; i < lines.length; i++) {
    if (lines[i] === '        {') {
      let depth = 1;
      let j = i + 1;
      while (j < lines.length && depth > 0) {
        for (const ch of lines[j]) {
          if (ch === '{') depth++;
          else if (ch === '}') depth--;
        }
        if (depth > 0) j++;
      }
      blocks.push({ start: i, end: j });
      i = j; // skip past this block
    }
  }

  if (blockIdx >= blocks.length) return code;

  const { start, end } = blocks[blockIdx];
  const result = [...lines];
  result.splice(start, end - start + 1);
  // Remove trailing blank line left behind
  if (start < result.length && result[start].trim() === '') {
    result.splice(start, 1);
  }
  return result.join('\n');
}

const FinalizeExport: React.FC<FinalizeExportProps> = ({ onBack }) => {
  const navigate = useNavigate();
  const { examId, testerId } = useWizardStore(
    useShallow((s) => ({ examId: s.examId, testerId: s.testerId }))
  );
  const { exportComplete, exportPath } = useWizardStore(
    useShallow((s) => ({ exportComplete: s.exportComplete, exportPath: s.exportPath }))
  );
  const results = useWizardStore((s) => s.results);
  const inferredConfig = useWizardStore((s) => s.inferredConfig);
  const selectedQs = useWizardStore((s) => s.selectedQs);
  const localCode = useWizardStore((s) => s.localCode);
  const setLocalCode = useWizardStore((s) => s.setLocalCode);
  const initLocalCode = useWizardStore((s) => s.initLocalCode);
  const setExportComplete = useWizardStore((s) => s.setExportComplete);
  const reset = useWizardStore((s) => s.reset);

  const questionIds = selectedQs.filter((qid) => !!results[qid]);
  const [activeQid, setActiveQid] = useState<string | null>(questionIds[0] || null);
  const [exporting, setExporting] = useState(false);
  const [sessionError, setSessionError] = useState<string | null>(null);
  // Local mutable copy of cases per question (supports delete + weight edit)
  const localCases = useWizardStore((s) => s.localCases);
  const setLocalCases = useWizardStore((s) => s.setLocalCases);
  const initLocalCases = useWizardStore((s) => s.initLocalCases);
  const [casesExpanded, setCasesExpanded] = useState(true);

  const handleStaleSession = (err: any) => {
    if (err?.message?.includes('404') || err?.message?.toLowerCase().includes('not found')) {
      reset();
      setSessionError('Session expired — files were cleared (e.g. server restart). Please re-upload.');
      return true;
    }
    return false;
  };

  // Initialise localCode and localCases from results on first load
  useEffect(() => {
    const initialCode: Record<string, string> = {};
    const initialCases: Record<string, any[]> = {};
    let needsCodeInit = false;
    let needsCasesInit = false;

    for (const qid of questionIds) {
      if (results[qid]?.generatedCode && localCode[qid] == null) {
        initialCode[qid] = results[qid].generatedCode;
        needsCodeInit = true;
      }
      if (localCases[qid] == null) {
        initialCases[qid] = (results[qid]?.cases || []).map((c: any) => ({ ...c }));
        needsCasesInit = true;
      }
    }
    if (needsCodeInit) initLocalCode({ ...localCode, ...initialCode });
    if (needsCasesInit) initLocalCases(initialCases);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [results]);

  const handleDeleteCase = (qid: string, caseIdx: number) => {
    // Remove from cases list
    const updated = [...(localCases[qid] || [])];
    updated.splice(caseIdx, 1);
    setLocalCases(qid, updated);
    // Remove corresponding block from Java code
    const patched = removeNthGeneratedBlock(localCode[qid] || '', caseIdx);
    setLocalCode(qid, patched);
  };

  const handleExport = async () => {
    if (!('showDirectoryPicker' in window)) {
      toast({ title: 'Browser not supported', description: 'Use Chrome or Edge to save files locally.', variant: 'destructive' });
      return;
    }

    setExporting(true);
    try {
      // 1. Call backend to get fully merged .java content (merges original + generated for Path 1)
      const entries = questionIds.map(qid => {
        const r = results[qid];
        const cases = localCases[qid] || r.cases || [];
        return {
          questionId: qid,
          testerClassName: qid + 'Tester',
          generatedCode: localCode[qid] || r.generatedCode || '',
          cases: cases.map((c: any) => ({
            description: c.description || '',
            inputArgs: c.inputArgs || '',
            expectedOutput: c.expectedOutput || '',
            weight: 1,
          })),
        };
      });

      const response = await save({
        body: { examId: examId!, testerId: testerId ?? undefined, outputDir: 'generated-testers', results: entries, updateMaxScores: true },
        throwOnError: true,
      });

      const fileContents: Record<string, string> = (response.data as any)?.fileContents ?? {};

      // Fetch original tester files for questions that were NOT selected for generation
      if (testerId) {
        const nonGeneratedWithTesters = (inferredConfig?.questions || []).filter(
          (q: any) => q.tester && !questionIds.includes(q.questionId)
        );
        await Promise.all(nonGeneratedWithTesters.map(async (q: any) => {
          try {
            const { data } = await getTester({
              path: { className: q.tester },
              query: { testerId },
            });
            if ((data as any)?.code) {
              fileContents[q.tester] = (data as any).code;
            }
          } catch {
            // skip if original tester can't be fetched
          }
        }));
      }

      // 2. User picks a folder via native OS picker → write all files flat into Tester-Files/
      const dirHandle = await (window as any).showDirectoryPicker({ mode: 'readwrite' });

      // If an existing Tester-Files folder exists, rename it to Tester-Files (Original)
      try {
        const existingDir = await dirHandle.getDirectoryHandle('Tester-Files', { create: false });
        // It exists — copy all files into Tester-Files (Original), then remove the old dir
        const originalDir = await dirHandle.getDirectoryHandle('Tester-Files (Original)', { create: true });
        for await (const [name, entry] of existingDir.entries()) {
          if (entry.kind === 'file') {
            const file = await entry.getFile();
            const dest = await originalDir.getFileHandle(name, { create: true });
            const writable = await dest.createWritable();
            await writable.write(await file.arrayBuffer());
            await writable.close();
          }
        }
        await dirHandle.removeEntry('Tester-Files', { recursive: true });
      } catch (_e) {
        // Tester-Files doesn't exist — nothing to rename
      }

      const testerFilesHandle = await dirHandle.getDirectoryHandle('Tester-Files', { create: true });

      // Write generated questions (merged original + AI test cases)
      for (const qid of questionIds) {
        const testerClassName = qid + 'Tester';
        const content = fileContents[testerClassName] || localCode[qid] || results[qid]?.generatedCode || '';
        const fileHandle = await testerFilesHandle.getFileHandle(`${testerClassName}.java`, { create: true });
        const writable = await fileHandle.createWritable();
        await writable.write(content);
        await writable.close();
        if (fileContents[testerClassName]) {
          setLocalCode(qid, fileContents[testerClassName]);
        }
      }

      // Write untouched original tester files for non-selected questions
      for (const [className, content] of Object.entries(fileContents)) {
        const qid = className.replace('Tester', '');
        if (questionIds.includes(qid)) continue; // already written above
        const fileHandle = await testerFilesHandle.getFileHandle(`${className}.java`, { create: true });
        const writable = await fileHandle.createWritable();
        await writable.write(content);
        await writable.close();
      }

      setExportComplete('Tester-Files/');
    } catch (err: any) {
      if (err?.name === 'AbortError') return;
      if (!handleStaleSession(err)) {
        toast({ title: 'Export failed', description: err?.message || 'Unknown error.', variant: 'destructive' });
      }
    } finally {
      setExporting(false);
    }
  };

  const activeResult = activeQid ? results[activeQid] : null;
  const activeCode = activeQid ? (localCode[activeQid] || activeResult?.generatedCode || '') : '';
  const activeCases = activeQid ? (localCases[activeQid] || activeResult?.cases || []) : [];
  const generatedWeight = activeCases.length;
  const originalWeight = activeQid
    ? (inferredConfig?.questions?.find((q: any) => q.questionId === activeQid)?.maxScore ?? 0)
    : 0;
  const totalWeight = originalWeight + generatedWeight;

  if (sessionError) {
    return (
      <div className="flex flex-col items-center justify-center p-16 text-center gap-4">
        <p className="text-destructive font-mono text-sm">{sessionError}</p>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4 pb-20 animate-in fade-in slide-in-from-left-4">
      <div className="flex justify-between items-center">
        <div>
          <h2 className="text-2xl font-bold font-outfit text-foreground">Export</h2>
          <p className="text-muted-foreground text-sm">
            Review generated code, delete unwanted test cases, then export.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" onClick={onBack} disabled={exporting} className="rounded-md">
            <ChevronLeft size={16} className="mr-1" /> Generation Hub
          </Button>
          <div className="flex items-center gap-2">
            <Button
              onClick={handleExport}
              disabled={exporting || questionIds.length === 0}
              className={`rounded-md px-6 ${exportComplete ? 'bg-vsc-green hover:bg-vsc-green/90' : 'glow-accent bg-accent hover:bg-accent/90'}`}
            >
              {exporting ? (
                <><Loader2 size={14} className="mr-1.5 animate-spin" /> Exporting...</>
              ) : exportComplete ? (
                <span className="flex items-center gap-1.5"><CheckCircle2 size={14} /> Re-export</span>
              ) : (
                <><Download size={16} className="mr-1.5" /> Export Project</>
              )}
            </Button>
          </div>
        </div>
      </div>

      {exportComplete && exportPath && (
        <div className="flex items-center justify-between gap-3 p-3 bg-vsc-green/10 border border-vsc-green/20 rounded-md text-vsc-green text-sm">
          <div className="flex items-center gap-2">
            <CheckCircle2 size={16} />
            <span>Saved to <span className="font-mono font-bold">{exportPath}</span></span>
          </div>
          <Button
            size="sm"
            className="shrink-0 bg-vsc-green hover:bg-vsc-green/90 text-white rounded-md font-mono text-xs px-3"
            onClick={() => navigate('/grader')}
          >
            Proceed to Auto-Grader <ArrowRight size={13} className="ml-1" />
          </Button>
        </div>
      )}

      {questionIds.length === 0 ? (
        <div className="flex flex-col items-center justify-center p-16 text-center border-2 border-dashed border-border rounded-lg bg-secondary/50">
          <AlertCircle size={28} className="mb-2 text-muted-foreground" />
          <p className="text-sm font-mono text-muted-foreground">No generated results found. Go back and generate first.</p>
        </div>
      ) : (
        <div className="flex gap-4">
          {/* Question sidebar */}
          <div className="w-44 shrink-0 space-y-1.5">
            <p className="text-[9px] uppercase font-mono text-muted-foreground tracking-wider mb-2">Questions</p>
            {questionIds.map(qid => (
              <button
                key={qid}
                onClick={() => setActiveQid(qid)}
                className={`w-full flex items-center gap-2.5 p-2.5 rounded-md text-left transition-all text-sm ${
                  activeQid === qid
                    ? 'bg-primary/15 text-primary border border-primary/30 glow-blue'
                    : 'bg-card hover:bg-secondary border border-border'
                }`}
              >
                <FileText size={14} />
                <div>
                  <p className="font-mono font-bold text-xs">{qid}</p>
                  <p className={`text-[9px] font-mono ${activeQid === qid ? 'text-primary/70' : 'text-muted-foreground'}`}>
                    {(localCases[qid] || results[qid]?.cases || []).length} cases
                  </p>
                </div>
              </button>
            ))}

            <Separator className="my-3" />

            <div className="p-3 rounded-md bg-accent/5 border border-accent/10">
              <div className="flex items-center gap-1.5 text-accent mb-1.5">
                <Zap size={12} fill="currentColor" />
                <p className="text-[9px] uppercase font-mono font-bold tracking-wider">Steps</p>
              </div>
              <ul className="space-y-1 text-[9px] text-muted-foreground font-mono">
                <li>1. Review code</li>
                <li>2. Delete / adjust cases</li>
                <li>3. Export</li>
              </ul>
            </div>
          </div>

          {/* Main pane */}
          <div className="flex-1 min-w-0 flex flex-col gap-3">
            {activeQid && activeResult && (
              <>
                {/* Read-only code viewer */}
                <Card className="border-border bg-card overflow-hidden">
                  <div className="flex items-center justify-between px-4 py-2.5 bg-secondary border-b border-border">
                    <div className="flex items-center gap-2.5">
                      <div className="w-7 h-7 bg-primary text-primary-foreground rounded flex items-center justify-center font-mono font-bold text-xs">
                        {activeQid}
                      </div>
                      <span className="text-xs font-bold font-mono text-foreground">Generated Code</span>
                      {activeResult.compiledOk && (
                        <span className="text-[9px] px-2 py-0.5 bg-vsc-green/10 text-vsc-green rounded font-mono">COMPILED</span>
                      )}
                      <span className="text-[9px] px-2 py-0.5 bg-secondary text-muted-foreground rounded font-mono border border-border">READ-ONLY</span>
                    </div>
                  </div>
                  <div className="h-[360px] overflow-auto">
                    <SyntaxHighlighter
                      language="java"
                      style={vscDarkPlus}
                      showLineNumbers
                      wrapLines
                      wrapLongLines
                      customStyle={{ margin: 0, fontSize: '11px', background: '#0D1117', minHeight: '100%' }}
                      lineNumberStyle={{ minWidth: '2.5em', color: '#484f58' }}
                    >
                      {activeCode}
                    </SyntaxHighlighter>
                  </div>
                </Card>

                {/* Test case manager: delete + weight */}
                {activeCases.length > 0 && (
                  <Card className="border-border bg-card overflow-hidden">
                    <button
                      className="w-full flex items-center justify-between px-4 py-2.5 bg-secondary border-b border-border hover:bg-secondary/80 transition-colors"
                      onClick={() => setCasesExpanded(v => !v)}
                    >
                      <span className="text-xs font-bold font-mono text-foreground">
                        Test Cases
                        <span className="ml-2 text-[10px] font-mono text-muted-foreground">({activeCases.length})</span>
                      </span>
                      <div className="flex items-center gap-3">
                        <span className="text-[10px] font-mono text-muted-foreground">
                          {originalWeight > 0 && (
                            <>Original: <span className="text-foreground font-bold">{originalWeight}</span> · </>
                          )}
                          Generated: <span className="text-foreground font-bold">{generatedWeight}</span>
                          {originalWeight > 0 && (
                            <> · Total: <span className="text-vsc-green font-bold">{totalWeight}</span></>
                          )}
                        </span>
                        {casesExpanded ? <ChevronUp size={14} className="text-muted-foreground" /> : <ChevronDown size={14} className="text-muted-foreground" />}
                      </div>
                    </button>
                    {casesExpanded && (
                      <CardContent className="p-0">
                        <div className="divide-y divide-border">
                          {activeCases.map((tc: any, i: number) => (
                            <div key={i} className="flex items-center gap-3 px-4 py-2.5 group">
                              <span className="w-5 h-5 shrink-0 bg-secondary rounded flex items-center justify-center font-mono text-[9px] text-muted-foreground border border-border">{i + 1}</span>
                              <p className="flex-1 text-xs text-muted-foreground truncate font-mono">{tc.description || `Test case ${i + 1}`}</p>
                              <button
                                onClick={() => handleDeleteCase(activeQid, i)}
                                className="opacity-0 group-hover:opacity-100 transition-opacity text-muted-foreground hover:text-destructive"
                                title="Delete this test case"
                              >
                                <Trash2 size={13} />
                              </button>
                            </div>
                          ))}
                        </div>
                      </CardContent>
                    )}
                  </Card>
                )}
              </>
            )}
          </div>
        </div>
      )}
    </div>
  );
};

export default FinalizeExport;

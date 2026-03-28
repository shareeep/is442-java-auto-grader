import React, { useState, useEffect } from 'react';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Separator } from '@/components/ui/separator';
import {
  Download, ChevronLeft, CheckCircle2, AlertCircle, FileText, Zap,
  Send, Loader2, Code
} from 'lucide-react';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';
import { save, refine } from '@/generated/sdk.gen';
import { useWizardStore } from '../../store/wizardStore';
import { useShallow } from 'zustand/react/shallow';

interface FinalizeExportProps {
  onBack: () => void;
}

const FinalizeExport: React.FC<FinalizeExportProps> = ({ onBack }) => {
  const { examId, testerId } = useWizardStore(
    useShallow((s) => ({ examId: s.examId, testerId: s.testerId }))
  );
  const { exportComplete, exportPath } = useWizardStore(
    useShallow((s) => ({ exportComplete: s.exportComplete, exportPath: s.exportPath }))
  );
  const results = useWizardStore((s) => s.results);
  const localCode = useWizardStore((s) => s.localCode);
  const setLocalCode = useWizardStore((s) => s.setLocalCode);
  const initLocalCode = useWizardStore((s) => s.initLocalCode);
  const setExportComplete = useWizardStore((s) => s.setExportComplete);
  const reset = useWizardStore((s) => s.reset);

  const questionIds = Object.keys(results);
  const [activeQid, setActiveQid] = useState<string | null>(questionIds[0] || null);
  const [refinePrompt, setRefinePrompt] = useState('');
  const [refining, setRefining] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [sessionError, setSessionError] = useState<string | null>(null);

  const handleStaleSession = (err: any) => {
    if (err?.message?.includes('404') || err?.message?.toLowerCase().includes('not found')) {
      reset();
      setSessionError('Session expired — files were cleared (e.g. server restart). Please re-upload.');
      return true;
    }
    return false;
  };

  // Initialise localCode from results on first load (if not already set)
  useEffect(() => {
    const initial: Record<string, string> = {};
    let needsInit = false;
    for (const qid of questionIds) {
      if (results[qid]?.generatedCode && !localCode[qid]) {
        initial[qid] = results[qid].generatedCode;
        needsInit = true;
      }
    }
    if (needsInit) {
      initLocalCode({ ...localCode, ...initial });
    }
  }, []);

  const handleRefine = async () => {
    if (!activeQid || !refinePrompt.trim() || !localCode[activeQid]) return;
    setRefining(true);
    try {
      const { data: res } = await refine({
        body: { examId: examId!, questionId: activeQid, currentCode: localCode[activeQid], refinementPrompt: refinePrompt.trim() },
        throwOnError: true,
      });
      setLocalCode(activeQid, (res as any).refinedCode);
      setRefinePrompt('');
    } catch (err) {
      if (handleStaleSession(err)) return;
      console.error('Refine failed:', err);
    } finally {
      setRefining(false);
    }
  };

  const handleExport = async () => {
    setExporting(true);
    try {
      const entries = questionIds.map(qid => {
        const r = results[qid];
        return {
          questionId: qid,
          testerClassName: qid + 'Tester',
          generatedCode: localCode[qid] || r.generatedCode || '',
          cases: (r.cases || []).map((c: any) => ({
            description: c.description || '',
            inputArgs: c.inputArgs || '',
            expectedOutput: c.expectedOutput || '',
            weight: c.weight || 1,
          })),
        };
      });

      await save({
        body: { examId: examId!, testerId: testerId ?? undefined, outputDir: 'generated-testers', results: entries, updateMaxScores: true },
        throwOnError: true,
      });
      setExportComplete('generated-testers/');
    } catch (err: any) {
      if (handleStaleSession(err)) return;
      console.error('Export failed:', err);
    } finally {
      setExporting(false);
    }
  };

  const activeResult = activeQid ? results[activeQid] : null;
  const activeCode = activeQid ? (localCode[activeQid] || activeResult?.generatedCode || '') : '';

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
          <h2 className="text-2xl font-bold font-outfit text-foreground">Finalize & Export</h2>
          <p className="text-muted-foreground text-sm">
            Review generated code side-by-side, refine with AI, then export.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" onClick={onBack} disabled={exporting} className="rounded-md">
            <ChevronLeft size={16} className="mr-1" /> Generation Hub
          </Button>
          <Button
            onClick={handleExport}
            disabled={exporting || exportComplete || questionIds.length === 0}
            className={`rounded-md px-6 ${exportComplete ? 'bg-vsc-green hover:bg-vsc-green/90' : 'glow-accent bg-accent hover:bg-accent/90'}`}
          >
            {exporting ? (
              <><Loader2 size={14} className="mr-1.5 animate-spin" /> Exporting...</>
            ) : exportComplete ? (
              <span className="flex items-center gap-1.5"><CheckCircle2 size={14} /> Exported</span>
            ) : (
              <><Download size={16} className="mr-1.5" /> Export Project</>
            )}
          </Button>
        </div>
      </div>

      {exportComplete && exportPath && (
        <div className="flex items-center gap-2 p-3 bg-vsc-green/10 border border-vsc-green/20 rounded-md text-vsc-green text-sm">
          <CheckCircle2 size={16} />
          <span>Exported to <span className="font-mono font-bold">{exportPath}</span></span>
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
                    {results[qid]?.cases?.length || 0} cases
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
                <li>1. Review generated code</li>
                <li>2. Refine with AI if needed</li>
                <li>3. Export</li>
              </ul>
            </div>
          </div>

          {/* Split-pane code viewer */}
          <div className="flex-1 min-w-0">
            {activeQid && activeResult && (
              <Card className="border-border bg-card overflow-hidden">
                <div className="flex items-center justify-between px-4 py-2.5 bg-secondary border-b border-border">
                  <div className="flex items-center gap-2.5">
                    <div className="w-7 h-7 bg-primary text-primary-foreground rounded flex items-center justify-center font-mono font-bold text-xs">
                      {activeQid}
                    </div>
                    <span className="text-xs font-bold font-mono text-foreground">Code Review</span>
                    {activeResult.compiledOk && (
                      <span className="text-[9px] px-2 py-0.5 bg-vsc-green/10 text-vsc-green rounded font-mono">COMPILED</span>
                    )}
                  </div>
                </div>

                <div className="h-[420px]">
                  <div className="flex-1 flex flex-col min-w-0 h-full">
                    <div className="px-3 py-1.5 bg-vsc-bg border-b border-border flex items-center gap-1.5">
                      <Code size={10} className="text-primary" />
                      <span className="text-[9px] uppercase font-mono text-primary tracking-wider">
                        Generated Tester
                      </span>
                    </div>
                    <div className="flex-1 overflow-auto">
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
                  </div>
                </div>

                {/* Refine loop */}
                <div className="px-4 py-3 border-t border-border bg-secondary">
                  <div className="flex items-center gap-2">
                    <Input
                      value={refinePrompt}
                      onChange={(e: React.ChangeEvent<HTMLInputElement>) => setRefinePrompt(e.target.value)}
                      placeholder="Refine: e.g. 'Add a null check for input X'"
                      className="flex-1 bg-card border-border font-mono text-xs h-9"
                      onKeyDown={(e: React.KeyboardEvent) => e.key === 'Enter' && !refining && handleRefine()}
                      disabled={refining}
                    />
                    <Button
                      onClick={handleRefine}
                      disabled={refining || !refinePrompt.trim()}
                      size="sm"
                      className="gap-1.5 px-3 h-9 glow-blue text-xs"
                    >
                      {refining ? (
                        <><Loader2 size={12} className="animate-spin" /> Refining...</>
                      ) : (
                        <><Send size={12} /> Refine</>
                      )}
                    </Button>
                  </div>
                  <p className="text-[9px] text-muted-foreground mt-1.5 font-mono">
                    Ask the AI to modify the generated code. Changes are included in the export.
                  </p>
                </div>
              </Card>
            )}
          </div>
        </div>
      )}
    </div>
  );
};

export default FinalizeExport;

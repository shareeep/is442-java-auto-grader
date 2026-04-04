import React, { useState, useEffect, useRef } from 'react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Input } from '@/components/ui/input';
import { Progress } from '@/components/ui/progress';
import { Separator } from '@/components/ui/separator';
import { FastForward, Play, ChevronLeft, ChevronRight, BrainCircuit, Code, ListChecks, CheckCircle2, X, Plus, RotateCcw, Loader2 } from 'lucide-react';
import { recommend, generateTests } from '@/generated/sdk.gen';
import { useWizardStore } from '../../store/wizardStore';
import { useShallow } from 'zustand/react/shallow';
import { toast } from '@/components/ui/toast';

interface GenerationHubProps {
  onNext: () => void;
  onBack: () => void;
}

const GEN_MESSAGES = [
  'cooking...',
  'tomfoolering...',
  'thinking really hard...',
  'consulting the oracle...',
  'writing Java at 3am...',
  'vibing with the AI...',
];

function useAnimatedText(active: boolean, msgs: string[], ms = 2000) {
  const [idx, setIdx] = useState(0);
  useEffect(() => {
    if (!active) { setIdx(0); return; }
    const t = setInterval(() => setIdx((i) => (i + 1) % msgs.length), ms);
    return () => clearInterval(t);
  }, [active, msgs, ms]);
  return msgs[idx];
}

function CustomSuggestionInput({ qid, disabled }: { qid: string; disabled?: boolean }) {
  const [value, setValue] = useState('');
  const inputRef = useRef<HTMLInputElement>(null);
  const addCustomSuggestion = useWizardStore((s) => s.addCustomSuggestion);

  const handleAdd = () => {
    const trimmed = value.trim();
    if (!trimmed) return;
    addCustomSuggestion(qid, trimmed);
    setValue('');
    inputRef.current?.focus();
  };

  if (disabled) return null;

  return (
    <div className="flex gap-2 mt-2">
      <Input
        ref={inputRef}
        value={value}
        onChange={(e) => setValue(e.target.value)}
        onKeyDown={(e) => { if (e.key === 'Enter') handleAdd(); }}
        placeholder="Add custom test instruction..."
        className="h-7 text-xs font-mono bg-card border-border"
      />
      <Button
        variant="outline"
        size="sm"
        className="h-7 px-2 text-xs shrink-0"
        onClick={handleAdd}
        disabled={!value.trim()}
      >
        <Plus size={12} />
      </Button>
    </div>
  );
}

const GenerationHub: React.FC<GenerationHubProps> = ({ onNext, onBack }) => {
  const { examId, templateId, testerId } = useWizardStore(
    useShallow((s) => ({ examId: s.examId, templateId: s.templateId, testerId: s.testerId }))
  );
  const inferredConfig = useWizardStore((s) => s.inferredConfig);
  const selectedQs = useWizardStore((s) => s.selectedQs);
  const recommendations = useWizardStore((s) => s.recommendations);
  const customSuggestions = useWizardStore((s) => s.customSuggestions);
  const results = useWizardStore((s) => s.results);
  const setSelectedQs = useWizardStore((s) => s.setSelectedQs);
  const setRecommendation = useWizardStore((s) => s.setRecommendation);
  const deleteRecommendedConcept = useWizardStore((s) => s.deleteRecommendedConcept);
  const deleteCustomSuggestion = useWizardStore((s) => s.deleteCustomSuggestion);
  const setResult = useWizardStore((s) => s.setResult);
  const resetQuestion = useWizardStore((s) => s.resetQuestion);
  const reset = useWizardStore((s) => s.reset);

  const [sessionError, setSessionError] = useState<string | null>(null);
  const [generating, setGenerating] = useState<Record<string, boolean>>({});
  const [loadingRec, setLoadingRec] = useState<Record<string, boolean>>({});
  const [genAllProgress, setGenAllProgress] = useState<{ total: number; done: number } | null>(null);
  const [recProgress, setRecProgress] = useState<{ total: number; done: number } | null>(null);
  const hasAutoRecommended = useRef(false);
  const cancelledQids = useRef<Set<string>>(new Set());

  // Auto-clear progress bars shortly after all done
  useEffect(() => {
    if (genAllProgress && genAllProgress.done === genAllProgress.total) {
      const t = setTimeout(() => setGenAllProgress(null), 1200);
      return () => clearTimeout(t);
    }
  }, [genAllProgress]);

  useEffect(() => {
    if (recProgress && recProgress.done === recProgress.total) {
      const t = setTimeout(() => setRecProgress(null), 1200);
      return () => clearTimeout(t);
    }
  }, [recProgress]);

  // When no tester dir: show all non-parent questions (maxScore = 0 since no testers to count from)
  // When tester dir provided: only show questions with maxScore > 0 (matched testers)
  const allQuestions = (inferredConfig?.questions || []).filter((q: any) =>
    !q.implicitParent && (testerId ? q.maxScore > 0 : true)
  );

  // Initialize all questions as selected on first load
  useEffect(() => {
    if (allQuestions.length > 0 && selectedQs.length === 0) {
      setSelectedQs(allQuestions.map((q: any) => q.questionId));
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [inferredConfig]);

  // Auto-recommend for all selected questions when landing on this step
  useEffect(() => {
    if (hasAutoRecommended.current || selectedQs.length === 0) return;
    hasAutoRecommended.current = true;
    const toRecommend = selectedQs.filter((qid) => !recommendations[qid]);
    if (toRecommend.length === 0) return;
    setRecProgress({ total: toRecommend.length, done: 0 });
    toRecommend.forEach((qid) =>
      getRecommendation(qid, () =>
        setRecProgress((prev) => (prev ? { ...prev, done: prev.done + 1 } : null))
      )
    );
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedQs]);

  const handleStaleSession = (err: any) => {
    if (err?.message?.includes('404') || err?.message?.toLowerCase().includes('not found')) {
      reset();
      setSessionError('Session expired — files were cleared (e.g. server restart). Please re-upload.');
      return true;
    }
    return false;
  };

  const toggleQ = (qid: string) => {
    setSelectedQs(
      selectedQs.includes(qid) ? selectedQs.filter((q) => q !== qid) : [...selectedQs, qid]
    );
  };

  const toggleAll = () => {
    setSelectedQs(
      selectedQs.length === allQuestions.length ? [] : allQuestions.map((q: any) => q.questionId)
    );
  };

  const getRecommendation = async (qid: string, onDone?: () => void) => {
    setLoadingRec((prev) => ({ ...prev, [qid]: true }));
    try {
      const { data: rec } = await recommend({ body: { examId: examId!, questionId: qid }, throwOnError: true });
      setRecommendation(qid, rec);
    } catch (err) {
      if (!handleStaleSession(err)) {
        toast({ title: `Recommendation failed for ${qid}`, description: 'Please try again.', variant: 'destructive' });
      }
    }
    setLoadingRec((prev) => { const next = { ...prev }; delete next[qid]; return next; });
    onDone?.();
  };

  const executeGen = async (qid: string, onDone?: () => void) => {
    setGenerating((prev) => ({ ...prev, [qid]: true }));
    const question = inferredConfig?.questions?.find((q: any) => q.questionId === qid);
    const rec = recommendations[qid];
    const customs = customSuggestions[qid] ?? [];
    const conceptsToCover = rec?.conceptsToCover ?? [];
    // numCases = one per concept/instruction (min 3, max 5)
    const totalSuggestions = conceptsToCover.length + customs.length;
    const numCases = Math.min(5, totalSuggestions > 0 ? totalSuggestions : Math.max(3, rec?.recommendedCount || 3));

    try {
      const { data: result } = await generateTests({
        body: {
          examId: examId!,
          testerId: testerId ?? undefined,
          templateId: templateId ?? undefined,
          numCases,
          question: question!,
          conceptsToCover,
          customSuggestions: customs,
        },
        throwOnError: true,
      });
      if (!cancelledQids.current.has(qid)) setResult(qid, result);
    } catch (err) {
      if (!cancelledQids.current.has(qid) && !handleStaleSession(err)) {
        toast({ title: `Generation failed for ${qid}`, description: 'Click Generate to try again.', variant: 'destructive' });
      }
    }
    cancelledQids.current.delete(qid);
    setGenerating((prev) => ({ ...prev, [qid]: false }));
    onDone?.();
  };

  const isAnyGenerating = Object.values(generating).some(Boolean);
  const isAnyRecLoading = Object.values(loadingRec).some(Boolean);
  const ungeneratedSelected = selectedQs.filter((qid) => !results[qid]);

  const generateAll = () => {
    if (isAnyGenerating) {
      // Cancel all in-progress generations
      Object.entries(generating).forEach(([qid, active]) => {
        if (active) cancelledQids.current.add(qid);
      });
      setGenerating({});
      setGenAllProgress(null);
      return;
    }
    const toGenerate = ungeneratedSelected;
    if (toGenerate.length === 0) return;
    setGenAllProgress({ total: toGenerate.length, done: 0 });
    toGenerate.forEach((qid) =>
      executeGen(qid, () =>
        setGenAllProgress((prev) => (prev ? { ...prev, done: prev.done + 1 } : null))
      )
    );
  };

  const handleFinalize = () => {
    if (selectedQs.length === 0) {
      toast({ title: 'No questions selected', description: 'Select at least one question to finalize.', variant: 'destructive' });
      return;
    }
    const ungenerated = selectedQs.filter((qid) => !results[qid]);
    if (ungenerated.length > 0) {
      toast({
        title: 'Generation incomplete',
        description: `${ungenerated.join(', ')} ${ungenerated.length === 1 ? 'has' : 'have'} not been generated yet.`,
        variant: 'destructive',
      });
      return;
    }
    onNext();
  };

  if (sessionError) {
    return (
      <div className="flex flex-col items-center justify-center p-16 text-center gap-4">
        <p className="text-destructive font-mono text-sm">{sessionError}</p>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-6 pb-20 animate-in fade-in slide-in-from-right-4">
      <div className="flex justify-between items-center">
        <div>
          <h2 className="text-2xl font-bold font-outfit text-foreground">Generate</h2>
          <p className="text-muted-foreground text-sm">Select questions and generate structured test cases.</p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" onClick={onBack} className="rounded-md">
            <ChevronLeft size={16} className="mr-1" /> Review
          </Button>
          <Button
            variant="outline"
            onClick={generateAll}
            disabled={isAnyRecLoading || (!isAnyGenerating && ungeneratedSelected.length === 0)}
            className={`rounded-md gap-1.5 ${isAnyGenerating ? 'border-destructive/40 text-destructive hover:bg-destructive/10' : ''}`}
          >
            {isAnyGenerating
              ? <><X size={14} /> Cancel</>
              : isAnyRecLoading
                ? <><Loader2 size={14} className="animate-spin" /> Recommending...</>
                : <><FastForward size={14} fill="currentColor" /> Generate All</>
            }
          </Button>
          <Button
            onClick={handleFinalize}
            disabled={isAnyGenerating || ungeneratedSelected.length > 0 || selectedQs.length === 0}
            className="rounded-md px-6 glow-blue"
          >
            Continue <ChevronRight size={16} className="ml-1" />
          </Button>
        </div>
      </div>

      {recProgress && (
        <div className="space-y-1.5 px-4 py-3 rounded-md border border-accent/20 bg-accent/5 animate-in fade-in">
          <div className="flex justify-between items-center text-xs font-mono text-foreground">
            <span className="flex items-center gap-2">
              {recProgress.done < recProgress.total
                ? <Loader2 size={12} className="animate-spin" />
                : <CheckCircle2 size={12} />
              }
              {recProgress.done < recProgress.total
                ? `Fetching recommendations… ${recProgress.done} / ${recProgress.total} done`
                : `All ${recProgress.total} recommendations ready — review before generating`
              }
            </span>
            <span>{Math.round((recProgress.done / recProgress.total) * 100)}%</span>
          </div>
          <Progress value={(recProgress.done / recProgress.total) * 100} className="h-1.5" />
        </div>
      )}

      {genAllProgress && (
        <div className="space-y-1.5 px-4 py-3 rounded-md border border-primary/20 bg-primary/5 animate-in fade-in">
          <div className="flex justify-between items-center text-xs font-mono text-primary">
            <span className="flex items-center gap-2">
              {genAllProgress.done < genAllProgress.total
                ? <Loader2 size={12} className="animate-spin" />
                : <CheckCircle2 size={12} />
              }
              {genAllProgress.done < genAllProgress.total
                ? `Generating… ${genAllProgress.done} / ${genAllProgress.total} done`
                : `All ${genAllProgress.total} generated`
              }
            </span>
            <span>{Math.round((genAllProgress.done / genAllProgress.total) * 100)}%</span>
          </div>
          <Progress value={(genAllProgress.done / genAllProgress.total) * 100} className="h-1.5" />
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Sidebar: Selection */}
        <div className="lg:col-span-1 space-y-3">
          <Card className="border-border bg-card">
            <CardHeader className="pb-2">
              <div className="flex items-center justify-between">
                <CardTitle className="text-sm">Question Selection</CardTitle>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={toggleAll}
                  className="text-[10px] font-mono h-6 px-2 text-muted-foreground hover:text-foreground"
                >
                  {selectedQs.length === allQuestions.length ? 'Deselect All' : 'Select All'}
                </Button>
              </div>
              <CardDescription className="text-xs">Select questions to generate</CardDescription>
            </CardHeader>
            <CardContent className="space-y-2">
              {allQuestions.map((q: any) => (
                <label
                  key={q.questionId}
                  className="flex items-center space-x-3 p-2.5 rounded-md hover:bg-secondary transition-colors cursor-pointer"
                >
                  <Checkbox
                    id={`q-${q.questionId}`}
                    checked={selectedQs.includes(q.questionId)}
                    onCheckedChange={() => toggleQ(q.questionId)}
                  />
                  <span className="flex-1 font-mono font-bold text-sm">{q.questionId}</span>
                  <span className="text-[9px] bg-secondary px-2 py-0.5 rounded font-mono text-muted-foreground">{q.maxScore} pts</span>
                </label>
              ))}
              <Separator />
              <div className="pt-1 flex justify-between text-xs">
                <span className="text-muted-foreground">Selected</span>
                <span className="font-bold text-primary font-mono">{selectedQs.length}</span>
              </div>
            </CardContent>
          </Card>

          <div className="p-4 bg-accent/5 rounded-md border border-accent/10">
            <div className="flex items-center gap-2 text-accent mb-2">
              <BrainCircuit size={14} />
              <h3 className="font-bold font-mono text-xs">AI Recommended</h3>
            </div>
            <p className="text-[10px] text-muted-foreground leading-relaxed">
              Click "Recommend" to have the AI suggest all testing concepts. Delete any you don't need, add custom instructions, then Generate.
            </p>
          </div>
        </div>

        {/* Main Content: Actions */}
        <div className="lg:col-span-2 space-y-4">
          {selectedQs.length === 0 ? (
            <div className="flex flex-col items-center justify-center p-16 border-2 border-dashed border-border rounded-lg bg-secondary/50 text-center">
              <div className="p-3 bg-card rounded-md border border-border mb-3">
                <ListChecks size={28} className="text-muted-foreground" />
              </div>
              <h3 className="text-base font-bold font-outfit text-muted-foreground">No Questions Selected</h3>
              <p className="text-xs text-muted-foreground max-w-xs mx-auto mt-1">Select a question from the sidebar to begin.</p>
            </div>
          ) : (
            selectedQs.map((qid) => {
              const rec = recommendations[qid];
              const customs = customSuggestions[qid] ?? [];
              const isGenerating = generating[qid];
              const result = results[qid];
              const isRecLoading = !!loadingRec[qid];

              return (
                <QuestionCard
                  key={qid}
                  qid={qid}
                  rec={rec}
                  customs={customs}
                  isGenerating={isGenerating}
                  isRecLoading={isRecLoading}
                  result={result}
                  onRecommend={() => getRecommendation(qid)}
                  onGenerate={() => executeGen(qid)}
                  onDeleteConcept={(idx) => deleteRecommendedConcept(qid, idx)}
                  onDeleteCustom={(idx) => deleteCustomSuggestion(qid, idx)}
                  onReset={() => {
                    resetQuestion(qid);
                    setGenerating((prev) => { const next = { ...prev }; delete next[qid]; return next; });
                    setLoadingRec((prev) => { const next = { ...prev }; delete next[qid]; return next; });
                  }}
                />
              );
            })
          )}
        </div>
      </div>
    </div>
  );
};

interface QuestionCardProps {
  qid: string;
  rec: any;
  customs: string[];
  isGenerating: boolean;
  isRecLoading: boolean;
  result: any;
  onRecommend: () => void;
  onGenerate: () => void;
  onDeleteConcept: (idx: number) => void;
  onDeleteCustom: (idx: number) => void;
  onReset: () => void;
}

const QuestionCard: React.FC<QuestionCardProps> = ({
  qid, rec, customs, isGenerating, isRecLoading, result,
  onRecommend, onGenerate, onDeleteConcept, onDeleteCustom, onReset,
}) => {
  const genText = useAnimatedText(isGenerating, GEN_MESSAGES);

  return (
    <Card className="border-border bg-card overflow-hidden animate-in zoom-in-95 duration-300">
      <CardHeader className="bg-secondary/50 border-b border-border py-3 px-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 bg-primary text-primary-foreground rounded flex items-center justify-center font-mono font-bold text-sm">
              {qid}
            </div>
          </div>
          <div className="flex items-center gap-2">
            {/* Reset — only enabled when there's something to reset */}
            <Button
              variant="ghost"
              size="sm"
              className="h-7 px-1.5 text-muted-foreground hover:text-destructive"
              onClick={onReset}
              title="Reset this question"
              disabled={isGenerating || isRecLoading || (!rec && customs.length === 0 && !result)}
            >
              <RotateCcw size={12} />
            </Button>

            {/* Recommend — hidden once generated, locked once recommended */}
            {!result && (
              rec ? (
                <span className="text-[10px] font-mono text-accent flex items-center gap-1 px-2">
                  <BrainCircuit size={12} /> Recommended
                </span>
              ) : (
                <Button
                  variant="ghost"
                  size="sm"
                  className="text-primary font-mono text-xs gap-1.5 hover:bg-primary/10"
                  onClick={onRecommend}
                  disabled={isRecLoading || isGenerating}
                >
                  {isRecLoading
                    ? <><Loader2 size={12} className="animate-spin" /> Recommending...</>
                    : <><BrainCircuit size={14} /> Recommend</>
                  }
                </Button>
              )
            )}

            {/* Generate — muted/locked after first use */}
            {result ? (
              <Button
                size="sm"
                className="h-8 px-3 rounded gap-1.5 text-xs bg-vsc-green/10 text-vsc-green border border-vsc-green/20 cursor-default"
                disabled
              >
                <CheckCircle2 size={12} /> Generated
              </Button>
            ) : (
              <Button
                size="sm"
                className="h-8 px-3 rounded gap-1.5 glow-blue text-xs"
                onClick={onGenerate}
                disabled={isGenerating || isRecLoading}
              >
                <Play size={12} fill="currentColor" /> Generate
              </Button>
            )}
          </div>
        </div>
      </CardHeader>
      <CardContent className="p-4 space-y-3">
        {/* AI suggestions + custom suggestions */}
        {rec ? (
          <div className="p-3 bg-white/5 rounded-md border border-white/10 animate-in slide-in-from-top-2">
            <div className="flex items-center justify-between mb-2">
              <p className="text-[9px] uppercase font-mono text-white/80 tracking-wider">AI Suggestions</p>
              <span className="text-[9px] bg-white/10 px-2 py-0.5 rounded font-mono text-white/80">
                {(rec.conceptsToCover?.length ?? 0) + customs.length} concepts
              </span>
            </div>
            <div className="flex flex-wrap gap-1.5">
              {rec.conceptsToCover.map((c: string, idx: number) => (
                <span
                  key={idx}
                  className="text-[10px] px-2 py-0.5 bg-card rounded border border-accent/10 text-muted-foreground font-mono flex items-center gap-1 group"
                >
                  <span className="w-1 h-1 bg-white/50 rounded-full shrink-0" />
                  {c}
                  <button
                    onClick={() => onDeleteConcept(idx)}
                    className="ml-0.5 opacity-0 group-hover:opacity-100 text-muted-foreground hover:text-destructive transition-opacity"
                  >
                    <X size={9} />
                  </button>
                </span>
              ))}
              {customs.map((c, idx) => (
                <span
                  key={`custom-${idx}`}
                  className="text-[10px] px-2 py-0.5 bg-primary/5 rounded border border-primary/20 text-primary font-mono flex items-center gap-1 group"
                >
                  <span className="w-1 h-1 bg-white/50 rounded-full shrink-0" />
                  Custom #{idx + 1} — {c}
                  <button
                    onClick={() => onDeleteCustom(idx)}
                    className="ml-0.5 opacity-0 group-hover:opacity-100 text-muted-foreground hover:text-destructive transition-opacity"
                  >
                    <X size={9} />
                  </button>
                </span>
              ))}
            </div>
            <CustomSuggestionInput qid={qid} disabled={isGenerating || !!result} />
          </div>
        ) : (
          <div className="p-3 bg-secondary/50 rounded-md border border-border">
            <p className="text-[9px] uppercase font-mono text-muted-foreground tracking-wider mb-1.5">Custom Instructions</p>
            {customs.length > 0 && (
              <div className="flex flex-wrap gap-1.5 mb-2">
                {customs.map((c, idx) => (
                  <span
                    key={idx}
                    className="text-[10px] px-2 py-0.5 bg-card rounded border border-primary/20 text-primary font-mono flex items-center gap-1 group"
                  >
                    <span className="w-1 h-1 bg-white/50 rounded-full shrink-0" />
                    Custom #{idx + 1} — {c}
                    <button
                      onClick={() => onDeleteCustom(idx)}
                      className="ml-0.5 opacity-0 group-hover:opacity-100 text-muted-foreground hover:text-destructive transition-opacity"
                    >
                      <X size={9} />
                    </button>
                  </span>
                ))}
              </div>
            )}
            <CustomSuggestionInput qid={qid} disabled={isGenerating || !!result} />
          </div>
        )}

        {/* Loading progress */}
        {isGenerating && (
          <div className="space-y-3 py-2 animate-pulse">
            <div className="flex justify-between text-xs">
              <span className="text-primary font-mono">{genText}</span>
            </div>
            <Progress value={undefined} className="h-1.5" />
          </div>
        )}

        {/* Result preview */}
        {result && (
          <div className="space-y-3 animate-in fade-in duration-500">
            <div className="flex items-center p-2.5 bg-vsc-green/10 border border-vsc-green/20 rounded-md text-vsc-green">
              <div className="flex items-center gap-2 text-xs font-mono font-bold">
                <CheckCircle2 size={14} /> Generation Complete ({result.cases.length} cases)
              </div>
            </div>
            <div className="grid grid-cols-1 gap-1.5">
              {result.cases.map((tc: any, idx: number) => (
                <div key={idx} className="flex items-center gap-3 p-2.5 bg-secondary rounded-md text-xs border border-border">
                  <div className="w-5 h-5 bg-card rounded flex items-center justify-center font-mono font-bold text-[9px] border border-border">{idx + 1}</div>
                  <p className="flex-1 text-muted-foreground">{tc.description}</p>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Empty state */}
        {!rec && !isGenerating && !isRecLoading && !result && customs.length === 0 && (
          <div className="flex flex-col items-center justify-center py-6 opacity-30">
            <Code size={28} />
            <p className="text-xs mt-2 font-mono">Ready</p>
          </div>
        )}

        {/* Loading recommendation state */}
        {isRecLoading && !rec && !result && (
          <div className="flex flex-col items-center justify-center py-6 opacity-30">
            <Loader2 size={28} className="animate-spin" />
            <p className="text-xs mt-2 font-mono">Recommending...</p>
          </div>
        )}
      </CardContent>
    </Card>
  );
};

export default GenerationHub;

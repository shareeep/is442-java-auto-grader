import React, { useState, useMemo, useEffect } from 'react';
import { useWizardStore } from '../../store/wizardStore';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { AlertCircle, CheckCircle2, ChevronRight, ChevronLeft, ChevronDown, ChevronUp, ListChecks, FileSearch, FolderSearch, Info } from 'lucide-react';

interface InferenceReviewProps {
  onNext: () => void;
  onBack: () => void;
}

interface QuestionGroup {
  parent: any | null;
  children: any[];
}

const InferenceReview: React.FC<InferenceReviewProps> = ({ onNext, onBack }) => {
  const config = useWizardStore((s) => s.inferredConfig);
  const testerId = useWizardStore((s) => s.testerId);
  const hasTestersDir = !!testerId;
  const [expandedParents, setExpandedParents] = useState<Set<string>>(new Set());

  const groups = useMemo(() => {
    if (!config?.questions) return [];

    const questions = config.questions;
    const parentMap = new Map<string, QuestionGroup>();
    const parentIds = new Set<string>();

    // Identify parent questions (ones with sub-questions)
    questions.forEach((q: any) => {
      // Check if this is a sub-question (e.g., Q1a, Q2b)
      const parentMatch = q.questionId.match(/^Q(\d+)$/);
      if (!parentMatch) {
        // It's a sub-question, find its parent
        const parentId = q.questionId.slice(0, q.questionId.length > 2 ? 2 : q.questionId.length);
        if (!parentMap.has(parentId)) {
          parentMap.set(parentId, { parent: null, children: [] });
        }
        parentMap.get(parentId)!.children.push(q);
      } else {
        parentIds.add(q.questionId);
      }
    });

    // Now organize into final groups
    const result: QuestionGroup[] = [];
    const processedParents = new Set<string>();

    questions.forEach((q: any) => {
      if (q.implicitParent || parentIds.has(q.questionId)) {
        // It's a parent question
        const group = parentMap.get(q.questionId);
        if (group) {
          result.push({ parent: q, children: group.children });
        } else {
          result.push({ parent: q, children: [] });
        }
        processedParents.add(q.questionId);
      } else {
        // It's a standalone question (not a parent or child)
        const parentId = q.questionId.slice(0, 2);
        if (!parentIds.has(parentId)) {
          result.push({ parent: null, children: [q] });
        }
        // Skip children - they're already in parent groups
      }
    });

    return result;
  }, [config]);

  // Auto-expand all parent groups so sub-questions are immediately visible
  useEffect(() => {
    const parentIds = groups
      .filter(g => g.children.length > 0 && g.parent)
      .map(g => g.parent!.questionId);
    if (parentIds.length > 0) {
      setExpandedParents(new Set(parentIds));
    }
  }, [groups]);

  const toggleParent = (parentId: string) => {
    setExpandedParents(prev => {
      const next = new Set(prev);
      if (next.has(parentId)) {
        next.delete(parentId);
      } else {
        next.add(parentId);
      }
      return next;
    });
  };

  if (!config) {
    return (
      <div className="flex flex-col items-center justify-center p-12 text-center h-full">
        <AlertCircle className="text-destructive mb-4" size={48} />
        <h2 className="text-2xl font-bold">No Inference Data Found</h2>
        <p className="text-muted-foreground mt-2">Please go back and complete the project setup first.</p>
        <Button onClick={onBack} className="mt-6">Go Back</Button>
      </div>
    );
  }

  const allConflicts = config.conflicts || [];
  const questions = config.questions || [];

  // When no tester dir was provided, MISSING_TESTER is expected — show as warnings not errors
  const hardConflicts = hasTestersDir
    ? allConflicts
    : allConflicts.filter((c: any) => c.type !== 'MISSING_TESTER');
  // Derive from questions directly so sub-questions found via file scanning are included
  const testerWarnings = !hasTestersDir
    ? questions.filter((q: any) => !q.implicitParent && !q.tester).map((q: any) => ({ questionId: q.questionId }))
    : [];

  // Count stats (exclude parent questions from ready count)
  const nonParentQuestions = questions.filter((q: any) => !q.implicitParent);
  const fromPdf = questions.filter((q: any) => q.inferredFromPdf).length;
  const withTester = nonParentQuestions.filter((q: any) => q.tester).length;
  const withFolder = nonParentQuestions.filter((q: any) => q.folder).length;
  const ready = nonParentQuestions.filter((q: any) => hasTestersDir ? (q.folder && q.tester) : q.folder).length;
  const totalMarks = questions.reduce((sum: number, q: any) => sum + (q.maxScore || 0), 0);

  const renderQuestionCard = (q: any, isChild: boolean = false) => {
    return (
      <div
        key={q.questionId}
        className={`flex items-center p-4 gap-4 ${isChild ? 'pl-8 border-l-2 border-primary/20 ml-8' : ''}`}
      >
        <div className={`w-12 h-12 rounded-md flex items-center justify-center font-mono font-bold text-lg ${
          isChild ? 'bg-secondary text-secondary-foreground' : 'bg-primary/10 text-primary'
        }`}>
          {q.questionId}
        </div>
        <div className="flex-1 grid grid-cols-2 md:grid-cols-5 gap-3">
          <div>
            <p className="text-[9px] uppercase font-mono text-muted-foreground">Source</p>
            {q.inferredFromPdf ? (
              <span className="inline-flex items-center gap-1 text-[9px] px-1.5 py-0.5 bg-primary/10 text-primary rounded font-mono font-bold uppercase">
                <FileSearch size={8} /> PDF
              </span>
            ) : (
              <span className="inline-flex items-center gap-1 text-[9px] px-1.5 py-0.5 bg-secondary text-muted-foreground rounded font-mono font-bold uppercase">
                <FolderSearch size={8} /> Filesystem
              </span>
            )}
          </div>
          <div>
            <p className="text-[9px] uppercase font-mono text-muted-foreground">Folder</p>
            <p className="font-mono text-sm text-foreground">{q.folder || 'N/A'}</p>
          </div>
          <div>
            <p className="text-[9px] uppercase font-mono text-muted-foreground">Tester</p>
            <p className={`font-mono text-sm ${q.tester ? 'text-foreground' : 'text-muted-foreground'}`}>
              {q.tester ? `${q.tester}.java` : (hasTestersDir ? 'MISSING' : '—')}
            </p>
          </div>
          <div>
            <p className="text-[9px] uppercase font-mono text-muted-foreground">Max Score</p>
            <p className="font-mono text-sm text-foreground">{q.maxScore || 0}</p>
          </div>
          <div className="flex items-center gap-2">
            {q.folder && (q.tester || !hasTestersDir) ? (
              <div className="flex items-center gap-1.5 px-2.5 py-1 bg-vsc-green/10 text-vsc-green rounded text-[10px] font-mono font-bold">
                <CheckCircle2 size={10} /> Ready
              </div>
            ) : !q.folder ? (
              <div className="flex items-center gap-1.5 px-2.5 py-1 bg-vsc-yellow/10 text-vsc-yellow rounded text-[10px] font-mono font-bold">
                <AlertCircle size={10} /> Action Needed
              </div>
            ) : (
              <div className="flex items-center gap-1.5 px-2.5 py-1 bg-secondary text-muted-foreground rounded text-[10px] font-mono font-bold">
                <Info size={10} /> No Tester
              </div>
            )}
          </div>
        </div>
      </div>
    );
  };

  const renderGroupCard = (group: QuestionGroup, index: number) => {
    // Render as standalone question if:
    // 1. No parent (standalone question)
    // 2. Parent has no children (e.g., Q3 with no sub-questions like Q3a, Q3b)
    if (!group.parent || group.children.length === 0) {
      const question = group.parent || group.children[0];
      return (
        <Card key={index} className="border-border bg-card overflow-hidden">
          {renderQuestionCard(question, false)}
          {question.dependencyFiles?.length > 0 && (
            <div className="px-4 pb-4 pt-0 border-t border-border bg-secondary/50">
              <div className="flex items-center gap-2 mt-3 mb-2">
                <ListChecks size={12} className="text-muted-foreground" />
                <p className="text-[9px] uppercase font-mono text-muted-foreground tracking-wider">Dependencies</p>
              </div>
              <div className="flex flex-wrap gap-1.5">
                {(question.dependencyFiles || []).map((f: string, fi: number) => (
                  <span key={fi} className="text-[10px] px-2 py-0.5 bg-card border border-border rounded font-mono text-muted-foreground">
                    {f}
                  </span>
                ))}
              </div>
            </div>
          )}
        </Card>
      );
    }

    // Parent with children (accordion)
    const isExpanded = expandedParents.has(group.parent.questionId);
    const totalScore = group.children.reduce((sum: number, c: any) => sum + (c.maxScore || 0), 0);
    const childrenReady = group.children.filter((c: any) => hasTestersDir ? (c.folder && c.tester) : c.folder).length;
    const allChildrenReady = childrenReady === group.children.length;

    return (
      <Card key={index} className="border-border bg-card overflow-hidden">
        {/* Parent header - clickable accordion */}
        <div
          className="flex items-center p-4 gap-4 cursor-pointer hover:bg-accent/5 transition-colors"
          onClick={() => toggleParent(group.parent.questionId)}
        >
          <div className="w-12 h-12 bg-primary/10 rounded-md flex items-center justify-center font-mono font-bold text-primary text-lg">
            {group.parent.questionId}
          </div>
          <div className="flex-1 flex items-center justify-between">
            <div className="flex items-center gap-4">
              <div>
                <p className="text-[9px] uppercase font-mono text-muted-foreground">Total Score</p>
                <p className="font-mono text-sm text-foreground font-bold">{totalScore}</p>
              </div>
              <div>
                <p className="text-[9px] uppercase font-mono text-muted-foreground">Sub-questions</p>
                <p className="font-mono text-sm text-foreground">{group.children.length} parts</p>
              </div>
              <div className="flex items-center gap-1.5 px-2.5 py-1 bg-blue-500/10 text-blue-400 rounded text-[10px] font-mono font-bold">
                <FolderSearch size={10} /> Parent
              </div>
            </div>
            <div className="flex items-center gap-2">
              <span className="font-mono text-xs text-muted-foreground">
                {childrenReady}/{group.children.length} ready
              </span>
              {isExpanded ? (
                <ChevronUp size={16} className="text-muted-foreground" />
              ) : (
                <ChevronDown size={16} className="text-muted-foreground" />
              )}
            </div>
          </div>
        </div>

        {/* Children accordion */}
        {isExpanded && (
          <div className="border-t border-border bg-secondary/20">
            {group.children.map((child: any) => renderQuestionCard(child, true))}
            {group.children.some((c: any) => c.dependencyFiles?.length > 0) && (
              <div className="px-4 pb-4 pt-2 border-t border-border">
                <div className="flex items-center gap-2 mt-2 mb-2 ml-8">
                  <ListChecks size={12} className="text-muted-foreground" />
                  <p className="text-[9px] uppercase font-mono text-muted-foreground tracking-wider">Dependencies</p>
                </div>
                <div className="flex flex-wrap gap-1.5 ml-8">
                  {group.children.flatMap((c: any) => c.dependencyFiles || []).map((f: string, fi: number) => (
                    <span key={fi} className="text-[10px] px-2 py-0.5 bg-card border border-border rounded font-mono text-muted-foreground">
                      {f}
                    </span>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </Card>
    );
  };

  return (
    <div className="flex flex-col gap-4 pb-20 animate-in fade-in slide-in-from-bottom-4">
      <div className="flex justify-between items-center">
        <div>
          <h2 className="text-2xl font-bold font-outfit text-foreground">Configuration Review</h2>
          <p className="text-muted-foreground text-sm">Review the inferred structure. Resolve any issues before proceeding.</p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" onClick={onBack} className="rounded-md">
            <ChevronLeft size={16} className="mr-1" /> Back
          </Button>
          <Button onClick={onNext} className="rounded-md px-6 glow-blue">
            Confirm & Continue <ChevronRight size={16} className="ml-1" />
          </Button>
        </div>
      </div>

      {/* Inference summary */}
      <Card className="border-border bg-card">
        <CardContent className="pt-4">
          <div className="flex flex-wrap gap-4">
            <div className="flex items-center gap-2 text-sm">
              <span className="text-muted-foreground">Total Marks:</span>
              <span className="font-mono font-bold text-foreground">{totalMarks}</span>
            </div>
            <div className="h-4 w-px bg-border self-center" />
            <div className="flex items-center gap-2 text-sm">
              <FileSearch size={14} className="text-primary" />
              <span className="text-muted-foreground">PDF scan detected</span>
              <span className="font-mono font-bold text-foreground">{fromPdf}</span>
              <span className="text-muted-foreground">question{fromPdf !== 1 ? 's' : ''}</span>
            </div>
            <div className="h-4 w-px bg-border self-center" />
            <div className="flex items-center gap-2 text-sm">
              <FolderSearch size={14} className="text-primary" />
              <span className="font-mono font-bold text-foreground">{withFolder}/{nonParentQuestions.length}</span>
              <span className="text-muted-foreground">folders matched</span>
            </div>
            <div className="h-4 w-px bg-border self-center" />
            <div className="flex items-center gap-2 text-sm">
              {hasTestersDir ? (
                <>
                  <span className="font-mono font-bold text-foreground">{withTester}/{nonParentQuestions.length}</span>
                  <span className="text-muted-foreground">testers matched</span>
                </>
              ) : (
                <span className="text-muted-foreground italic">no testers (from scratch)</span>
              )}
            </div>
            <div className="h-4 w-px bg-border self-center" />
            <div className={`flex items-center gap-2 text-sm ${ready === nonParentQuestions.length ? 'text-vsc-green' : hasTestersDir ? 'text-vsc-yellow' : 'text-muted-foreground'}`}>
              {ready === nonParentQuestions.length
                ? <CheckCircle2 size={14} />
                : hasTestersDir ? <AlertCircle size={14} /> : <Info size={14} />}
              <span className="font-mono font-bold">{ready}/{nonParentQuestions.length}</span>
              <span className={ready === nonParentQuestions.length ? 'text-vsc-green' : 'text-muted-foreground'}>ready</span>
            </div>
          </div>
        </CardContent>
      </Card>

      {hardConflicts.length > 0 && (
        <Card className="border-destructive/30 bg-destructive/5">
          <CardHeader className="pb-2">
            <div className="flex items-center gap-2 text-destructive">
              <AlertCircle size={18} />
              <CardTitle className="text-base">Detected {hardConflicts.length} {hardConflicts.length === 1 ? 'Issue' : 'Issues'}</CardTitle>
            </div>
          </CardHeader>
          <CardContent>
            <div className="space-y-2">
              {hardConflicts.map((c: any, i: number) => (
                <div key={i} className="flex flex-col gap-1 p-3 bg-card rounded-md border border-destructive/10">
                  <p className="text-sm font-bold text-destructive flex items-center gap-2">
                    <span className="bg-destructive/10 px-2 py-0.5 rounded text-[9px] uppercase font-mono">{c.type}</span>
                    {c.message}
                  </p>
                  <p className="text-xs text-muted-foreground">Recommendation: {c.suggestion}</p>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      {(!hasTestersDir) && (
        <Card className="border-border bg-secondary/30">
          <CardHeader className="pb-2">
            <div className="flex items-center gap-2 text-muted-foreground">
              <Info size={16} />
              <CardTitle className="text-sm font-medium">
                No tester directory provided — generating from scratch
                {testerWarnings.length > 0 && <span className="ml-2 text-[10px] font-mono bg-secondary px-2 py-0.5 rounded">{testerWarnings.length} noted</span>}
              </CardTitle>
            </div>
          </CardHeader>
          {testerWarnings.length > 0 && (
            <CardContent className="pt-0">
              <div className="space-y-1.5">
                {testerWarnings.map((c: any, i: number) => (
                  <div key={i} className="flex items-center gap-2 p-2.5 bg-card rounded-md border border-border text-xs">
                    <span className="bg-secondary px-1.5 py-0.5 rounded text-[9px] uppercase font-mono text-muted-foreground shrink-0">{c.questionId}</span>
                    <span className="text-muted-foreground">no tester — will be generated</span>
                  </div>
                ))}
              </div>
            </CardContent>
          )}
        </Card>
      )}

      <div className="grid grid-cols-1 gap-3">
        {groups
          .filter(group => group.children.length > 0 || group.parent !== null)
          .map((group, index) => renderGroupCard(group, index))}
      </div>
    </div>
  );
};

export default InferenceReview;

import React, { useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';
import { Progress } from '@/components/ui/progress';
import { Separator } from '@/components/ui/separator';
import { Sparkles, Play, ChevronLeft, ChevronRight, BrainCircuit, Code, ListChecks, CheckCircle2 } from 'lucide-react';
import { recommend as apiRecommend, executeQuestion } from '@/api/client';

interface GenerationHubProps {
  data: any;
  onUpdate: (data: any) => void;
  onNext: () => void;
  onBack: () => void;
}

const GenerationHub: React.FC<GenerationHubProps> = ({ data, onUpdate, onNext, onBack }) => {
  const [selectedQs, setSelectedQs] = useState<string[]>([]);
  const [recommendations, setRecommendations] = useState<Record<string, any>>({});
  const [generating, setGenerating] = useState<Record<string, boolean>>({});
  const [results, setResults] = useState<Record<string, any>>({});
  const [loadingRec, setLoadingRec] = useState<string | null>(null);

  const toggleQ = (qid: string) => {
    setSelectedQs(prev =>
      prev.includes(qid) ? prev.filter(q => q !== qid) : [...prev, qid]
    );
  };

  const getRecommendation = async (qid: string) => {
    setLoadingRec(qid);
    try {
      const rec = await apiRecommend({ examId: data.examId, questionId: qid });
      setRecommendations(prev => ({ ...prev, [qid]: rec }));
    } catch (err) {
      console.error(err);
    } finally {
      setLoadingRec(null);
    }
  };

  const executeGen = async (qid: string) => {
    setGenerating(prev => ({ ...prev, [qid]: true }));
    const question = data.inferredConfig.questions.find((q: any) => q.questionId === qid);
    const rec = recommendations[qid];

    try {
      const result = await executeQuestion({
        examId: data.examId,
        testerId: data.testerId,
        templateId: data.templateId,
        numCases: rec?.recommendedCount || 3,
        question: question
      });
      setResults(prev => ({ ...prev, [qid]: result }));
    } catch (err) {
      console.error(err);
    } finally {
      setGenerating(prev => ({ ...prev, [qid]: false }));
    }
  };

  const [progress, setProgress] = useState<Record<string, number>>({});

  const allQuestions = (data.inferredConfig?.questions || []).filter((q: any) => q.maxScore > 0);

  return (
    <div className="flex flex-col gap-6 pb-20 animate-in fade-in slide-in-from-right-4">
      <div className="flex justify-between items-center">
        <div>
          <h2 className="text-2xl font-bold font-outfit text-foreground">Generation Hub</h2>
          <p className="text-muted-foreground text-sm">Select questions and generate structured test cases.</p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" onClick={onBack} className="rounded-md">
            <ChevronLeft size={16} className="mr-1" /> Review
          </Button>
          <Button
            onClick={() => {
              onUpdate({ results });
              onNext();
            }}
            className="rounded-md px-6 glow-blue"
            disabled={Object.keys(results).length === 0}
          >
            Finalize <ChevronRight size={16} className="ml-1" />
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Sidebar: Selection */}
        <div className="lg:col-span-1 space-y-3">
          <Card className="border-border bg-card">
            <CardHeader className="pb-2">
              <CardTitle className="text-sm">Question Selection</CardTitle>
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
              <Sparkles size={14} />
              <h3 className="font-bold font-mono text-xs">AI Recommended</h3>
            </div>
            <p className="text-[10px] text-muted-foreground leading-relaxed">
              Click "Get Recommendations" on any question to have the AI suggest key testing concepts.
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
            selectedQs.map(qid => {
              const rec = recommendations[qid];
              const isGenerating = generating[qid];
              const result = results[qid];

              return (
                <Card key={qid} className="border-border bg-card overflow-hidden animate-in zoom-in-95 duration-300">
                  <CardHeader className="bg-secondary/50 border-b border-border py-3 px-4">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-3">
                        <div className="w-8 h-8 bg-primary text-primary-foreground rounded flex items-center justify-center font-mono font-bold text-sm">
                          {qid}
                        </div>
                        <CardTitle className="text-sm">Structured Generation</CardTitle>
                      </div>
                      <div className="flex items-center gap-2">
                        <Button
                          variant="ghost"
                          size="sm"
                          className="text-primary font-mono text-xs gap-1.5 hover:bg-primary/10"
                          onClick={() => getRecommendation(qid)}
                          disabled={loadingRec === qid || isGenerating}
                        >
                          <BrainCircuit size={14} /> {loadingRec === qid ? 'Analyzing...' : 'Recommend'}
                        </Button>
                        <Button
                          size="sm"
                          className="h-8 px-3 rounded gap-1.5 glow-blue text-xs"
                          onClick={() => executeGen(qid)}
                          disabled={isGenerating}
                        >
                          {isGenerating ? 'Generating...' : <><Play size={12} fill="currentColor" /> Generate</>}
                        </Button>
                      </div>
                    </div>
                  </CardHeader>
                  <CardContent className="p-4">
                    {rec && (
                      <div className="mb-4 p-3 bg-accent/5 rounded-md border border-accent/10 animate-in slide-in-from-top-2">
                        <div className="flex items-center justify-between mb-2">
                          <p className="text-[9px] uppercase font-mono text-accent tracking-wider">AI Suggestions</p>
                          <span className="text-[9px] bg-accent/10 px-2 py-0.5 rounded font-mono text-accent">{rec.recommendedCount} cases</span>
                        </div>
                        <div className="flex flex-wrap gap-1.5">
                          {rec.conceptsToCover.map((c: string, idx: number) => (
                            <span key={idx} className="text-[10px] px-2 py-0.5 bg-card rounded border border-accent/10 text-muted-foreground font-mono flex items-center gap-1">
                              <span className="w-1 h-1 bg-accent rounded-full" /> {c}
                            </span>
                          ))}
                        </div>
                      </div>
                    )}

                    {isGenerating && (
                      <div className="space-y-3 py-3 animate-pulse">
                        <div className="flex justify-between text-xs">
                          <span className="text-primary font-mono">Processing{rec ? ' (using AI recommendations)' : '...'}</span>
                          <span className="text-muted-foreground font-mono">Generating...</span>
                        </div>
                        <Progress value={undefined} className="h-1.5" />
                      </div>
                    )}

                    {result && (
                      <div className="space-y-3 animate-in fade-in duration-500">
                        <div className="flex items-center justify-between p-2.5 bg-vsc-green/10 border border-vsc-green/20 rounded-md text-vsc-green">
                          <div className="flex items-center gap-2 text-xs font-mono font-bold">
                            <CheckCircle2 size={14} /> Gen-Ready ({result.cases.length} cases)
                          </div>
                          <div className="flex items-center gap-3 text-[9px] font-mono">
                            <span>Strict Schema: ON</span>
                          </div>
                        </div>

                        <div className="grid grid-cols-1 gap-1.5">
                          {result.cases.slice(0, 3).map((tc: any, idx: number) => (
                            <div key={idx} className="flex items-center gap-3 p-2.5 bg-secondary rounded-md text-xs border border-border">
                              <div className="w-5 h-5 bg-card rounded flex items-center justify-center font-mono font-bold text-[9px] border border-border">{idx + 1}</div>
                              <p className="flex-1 text-muted-foreground">{tc.description}</p>
                              <span className="font-mono text-[8px] text-muted-foreground uppercase">W:{tc.weight || 1}</span>
                            </div>
                          ))}
                          {result.cases.length > 3 && (
                            <p className="text-[10px] text-center text-muted-foreground font-mono">+{result.cases.length - 3} more</p>
                          )}
                        </div>
                      </div>
                    )}

                    {!rec && !isGenerating && !result && (
                      <div className="flex flex-col items-center justify-center py-8 opacity-30">
                        <Code size={28} />
                        <p className="text-xs mt-2 font-mono">Ready</p>
                      </div>
                    )}
                  </CardContent>
                </Card>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
};

export default GenerationHub;

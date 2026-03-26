import React, { useEffect, useRef } from 'react';
import { ArrowLeft, ShieldAlert, CheckCircle2, LayoutList, Activity } from 'lucide-react';
import gsap from 'gsap';

interface Anomaly {
  severity: string;
  description: string;
}

interface QuestionResult {
  questionId: string;
  score: number;
  maxScore: number;
}

export interface Submission {
  username: string;
  name: string;
  displayName: string;
  totalScore: number;
  maxPossibleScore: number;
  anomalies?: Anomaly[];
  results?: QuestionResult[];
}

interface SubmissionDetailsProps {
  submission: Submission;
  onBack: () => void;
}

const SubmissionDetails: React.FC<SubmissionDetailsProps> = ({ submission, onBack }) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const isPerfect = submission.totalScore === submission.maxPossibleScore;
  const hasAnomalies = submission.anomalies && submission.anomalies.length > 0;

  useEffect(() => {
    if (containerRef.current) {
      gsap.fromTo(
        containerRef.current,
        { opacity: 0, x: 20 },
        { opacity: 1, x: 0, duration: 0.4, ease: "power3.out" }
      );
      
      gsap.fromTo(
        containerRef.current.querySelectorAll('.stagger-item'),
        { opacity: 0, y: 15 },
        { opacity: 1, y: 0, duration: 0.4, stagger: 0.05, ease: "power3.out", delay: 0.1 }
      );
    }
  }, [submission]);

  const handleBack = () => {
    gsap.to(containerRef.current, {
      opacity: 0,
      x: -20,
      duration: 0.3,
      ease: "power2.inOut",
      onComplete: onBack
    });
  };

  return (
    <div ref={containerRef} className="flex flex-col gap-6">
      
      {/* Header / Navigation Controls */}
      <div className="flex items-center justify-between">
        <button 
          onClick={handleBack}
          className="magnetic-button group flex items-center gap-2 px-4 py-2 bg-white/50 border border-primary/20 rounded-lg text-primary hover:bg-white hover:border-primary/40 transition-all font-mono text-xs uppercase tracking-widest font-bold"
        >
          <ArrowLeft size={16} className="group-hover:-translate-x-1 transition-transform" />
          Back to Submissions
        </button>
      </div>

      {/* Identity Profile & Score Header */}
      <div className="glass-card p-8 flex flex-col md:flex-row justify-between items-start md:items-center gap-6 stagger-item border-primary/10">
        <div className="flex flex-col">
          <h2 className="font-outfit text-3xl font-bold text-primary mb-1">
            {submission.name || 'Unknown Student'}
          </h2>
          <span className="font-mono text-xs uppercase tracking-widest text-charcoal/40">
            Student ID: {submission.username || submission.displayName}
          </span>
        </div>
        
        <div className={`px-6 py-4 rounded-xl border flex gap-4 items-center ${isPerfect ? 'bg-green-50 border-green-200' : 'bg-background border-primary/20'}`}>
          <div className="flex flex-col text-right">
            <span className="font-mono text-[10px] uppercase tracking-widest text-charcoal/50 mb-1">Final Score</span>
            <div className="flex items-baseline gap-1">
              <span className={`font-mono text-4xl font-bold ${isPerfect ? 'text-green-700' : 'text-primary'}`}>
                {submission.totalScore}
              </span>
              <span className="font-mono text-sm text-charcoal/30">/{submission.maxPossibleScore}</span>
            </div>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        
        {/* Left Column: Anomalies */}
        <div className="flex flex-col gap-4 stagger-item">
          <div className="flex items-center gap-2 mb-2">
            <Activity size={20} className={hasAnomalies ? 'text-accent' : 'text-green-600'} />
            <h3 className="font-outfit text-xl font-bold text-primary">Errors & Warnings</h3>
          </div>
          
          {hasAnomalies ? (
            <div className="flex flex-col gap-3">
              {submission.anomalies!.map((anomaly, idx) => (
                <div key={idx} className="glass-card p-4 border-[#FAD1CD] bg-[#FDE8E6]/50 flex gap-4 items-start">
                  <div className="pt-1 text-accent"><ShieldAlert size={18} /></div>
                  <div className="flex flex-col">
                    <span className="font-mono text-[10px] uppercase font-bold text-accent tracking-widest mb-1">{anomaly.severity}</span>
                    <p className="text-sm font-sans text-charcoal/80 leading-relaxed font-medium">
                      {anomaly.description}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="glass-card p-8 border-green-200 bg-green-50/50 flex flex-col items-center justify-center text-center gap-3">
              <CheckCircle2 size={32} className="text-green-600" />
              <div className="flex flex-col">
                <span className="font-outfit font-bold text-lg text-green-800">Valid Submission</span>
                <span className="font-mono text-xs text-green-600/70">No anomalies or structural violations detected.</span>
              </div>
            </div>
          )}
        </div>

        {/* Right Column: Question Results */}
        <div className="flex flex-col gap-4 stagger-item">
          <div className="flex items-center gap-2 mb-2">
            <LayoutList size={20} className="text-primary" />
            <h3 className="font-outfit text-xl font-bold text-primary">Evaluation Matrix</h3>
          </div>

          <div className="glass-card overflow-hidden">
            <div className="grid grid-cols-12 gap-4 px-5 py-3 bg-primary/5 border-b border-primary/10">
              <div className="col-span-8 font-mono text-[10px] uppercase tracking-widest text-primary/60 font-semibold">Question</div>
              <div className="col-span-4 font-mono text-[10px] uppercase tracking-widest text-primary/60 font-semibold text-right">Score</div>
            </div>

            <div className="flex flex-col max-h-[400px] overflow-y-auto">
              {submission.results && submission.results.length > 0 ? (
                submission.results.map((req, idx) => {
                  const passed = req.score === req.maxScore;
                  return (
                    <div key={idx} className="grid grid-cols-12 gap-4 px-5 py-3 border-b border-primary/5 last:border-0 items-center">
                      <div className="col-span-8 font-sans text-sm font-medium text-charcoal/80 flex items-center gap-2">
                        <div className={`w-1.5 h-1.5 rounded-full ${passed ? 'bg-green-500' : 'bg-accent'}`} />
                        {req.questionId}
                      </div>
                      <div className="col-span-4 flex justify-end font-mono text-sm">
                        <span className={passed ? 'text-green-700 font-bold' : 'text-accent font-bold'}>{req.score}</span>
                        <span className="text-charcoal/30">/{req.maxScore}</span>
                      </div>
                    </div>
                  );
                })
              ) : (
                <div className="px-5 py-8 text-center text-sm font-mono text-charcoal/40">
                  No discrete questions logged.
                </div>
              )}
            </div>
          </div>
        </div>

      </div>

    </div>
  );
};

export default SubmissionDetails;

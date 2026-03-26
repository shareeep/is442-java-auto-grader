import React, { useRef, useEffect, useState } from 'react';
import { Users, AlertTriangle, Activity, CheckCircle2, ChevronRight } from 'lucide-react';
import gsap from 'gsap';
import SubmissionDetails, { Submission } from './SubmissionDetails';

// interfaces moved to SubmissionDetails.tsx and imported

interface ResultsTableProps {
  data: {
    status?: string;
    submissions?: Submission[];
    message?: string;
  };
}

const ResultsTable: React.FC<ResultsTableProps> = ({ data }) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const [selectedSubmission, setSelectedSubmission] = useState<Submission | null>(null);

  useEffect(() => {
    if (containerRef.current && data?.submissions?.length) {
      // Stagger list items in
      gsap.fromTo(
        containerRef.current.querySelectorAll('.result-row'),
        { y: 15, opacity: 0 },
        { y: 0, opacity: 1, stagger: 0.05, duration: 0.5, ease: 'power3.out' }
      );
    }
  }, [data, selectedSubmission]);

  if (data.status === 'error') {
    return (
      <div className="glass-card p-8 border-red-500/20 bg-red-50 text-red-700 mt-8">
        <div className="flex items-center gap-3 mb-2">
          <AlertTriangle size={24} />
          <h2 className="text-xl font-bold font-outfit text-red-800">Pipeline Error</h2>
        </div>
        <p className="font-mono text-sm">{data.message}</p>
      </div>
    );
  }

  const submissions = [...(data.submissions || [])].sort((a, b) => {
    const nameA = a.name || a.username || a.displayName || '';
    const nameB = b.name || b.username || b.displayName || '';
    return nameA.localeCompare(nameB);
  });
  const totalAnomalies = submissions.reduce((acc, s) => acc + (s.anomalies?.length || 0), 0);
  const perfectScores = submissions.filter(s => s.totalScore === s.maxPossibleScore).length;

  return (
    <div ref={containerRef} className="mt-8 flex flex-col gap-6">
      
      {/* Metrics Row */}
      <div className="flex flex-col md:flex-row gap-4 mb-2">
        <div className="flex-1 glass-card p-5 flex flex-col">
          <span className="font-mono text-[10px] uppercase tracking-widest text-charcoal/50 mb-1">Processed Zip Files</span>
          <span className="font-outfit text-3xl font-bold text-primary">{submissions.length}</span>
        </div>
        <div className="flex-1 glass-card p-5 flex flex-col border-accent/20">
          <span className="font-mono text-[10px] uppercase tracking-widest text-charcoal/50 mb-1 flex items-center gap-1.5">
            Detected Anomalies <AlertTriangle size={12} className="text-accent" />
          </span>
          <span className="font-outfit text-3xl font-bold text-accent">{totalAnomalies}</span>
        </div>
        <div className="flex-1 glass-card p-5 flex flex-col">
          <span className="font-mono text-[10px] uppercase tracking-widest text-charcoal/50 mb-1 flex items-center gap-1.5">
            Valid Submissions <CheckCircle2 size={12} className="text-green-600" />
          </span>
          <span className="font-outfit text-3xl font-bold text-green-700">{perfectScores}</span>
        </div>
      </div>

      {selectedSubmission ? (
        <SubmissionDetails submission={selectedSubmission} onBack={() => setSelectedSubmission(null)} />
      ) : (
        /* Main Data Table */
        <div className="glass-card overflow-hidden">
          {/* Header */}
          <div className="grid grid-cols-12 gap-4 px-6 py-4 bg-primary/5 border-b border-primary/10">
            <div className="col-span-5 font-mono text-[10px] uppercase tracking-widest text-primary/60 font-semibold">Student</div>
            <div className="col-span-4 font-mono text-[10px] uppercase tracking-widest text-primary/60 font-semibold">Submission Status</div>
            <div className="col-span-3 font-mono text-[10px] uppercase tracking-widest text-primary/60 font-semibold text-right">Final Score</div>
          </div>

        {/* Rows */}
        <div className="flex flex-col">
          {submissions.map((s, i) => {
            const hasAnomalies = s.anomalies && s.anomalies.length > 0;
            return (
              <div 
                key={i} 
                onClick={() => setSelectedSubmission(s)}
                className="result-row group grid grid-cols-12 gap-4 px-6 py-4 border-b border-primary/5 last:border-0 items-center transition-all duration-300 hover:bg-white/80 hover:-translate-y-[1px] cursor-pointer"
              >
                {/* Identity Col */}
                <div className="col-span-5 flex items-center gap-3">
                  <div className="w-10 h-10 rounded-full bg-primary/5 group-hover:bg-primary/10 flex items-center justify-center text-primary shrink-0 transition-colors">
                    <Users size={16} />
                  </div>
                  <div className="flex flex-col truncate pr-4">
                    <span className="font-outfit font-bold text-sm text-primary truncate">
                      {s.name || 'Unknown Student'}
                    </span>
                    <span className="font-mono text-[10px] text-charcoal/40 truncate mt-0.5">
                      {s.username || s.displayName}
                    </span>
                  </div>
                </div>

                {/* Badges Col */}
                <div className="col-span-4 flex items-center">
                  {hasAnomalies ? (
                    <span className="flex items-center gap-1.5 px-3 py-1.5 bg-[#FDE8E6] text-accent text-[10px] uppercase tracking-wider rounded border border-[#FAD1CD] font-bold shadow-sm">
                      <AlertTriangle size={12} className="shrink-0" />
                      Anomalies Detected
                    </span>
                  ) : (
                    <span className="flex items-center gap-1.5 px-3 py-1.5 bg-green-50 text-green-700 text-[10px] uppercase tracking-wider rounded border border-green-200 font-bold shadow-sm">
                      <Activity size={12} /> Nominal
                    </span>
                  )}
                </div>

                {/* Score Col */}
                <div className="col-span-3 flex justify-end items-center gap-4">
                  <div className="flex items-baseline gap-1 bg-background group-hover:bg-white px-3 py-1.5 rounded-lg border border-primary/10 shadow-sm transition-colors">
                    <span className={`font-mono text-lg font-semibold ${s.totalScore === s.maxPossibleScore ? 'text-green-700' : 'text-primary'}`}>
                      {s.totalScore}
                    </span>
                    <span className="font-mono text-xs text-charcoal/30">/{s.maxPossibleScore}</span>
                  </div>
                  <ChevronRight size={18} className="text-primary/30 group-hover:text-primary transition-colors transform group-hover:translate-x-1" />
                </div>
              </div>
            );
          })}
          
          {submissions.length === 0 && (
            <div className="py-12 text-center text-charcoal/50 font-sans text-sm">
              Await deployment of assignment data.
            </div>
          )}
        </div>
        </div>
      )}

    </div>
  );
};

export default ResultsTable;

import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  FileText, Download, Eye, Users, AlertCircle, Loader2, ArrowRight, ExternalLink,
} from 'lucide-react';
import { listRunsOptions } from '../generated/@tanstack/react-query.gen';
import { formatRunTimestamp, pdfUrl } from '@/lib/utils';

const PastRuns: React.FC = () => {
  const navigate = useNavigate();
  const { data: runsData, isLoading } = useQuery(listRunsOptions());
  const runs: any[] = ((runsData as any) ?? []) as any[];

  return (
    <div className="mx-auto flex w-full max-w-6xl flex-col gap-6 px-4 py-5 pb-16 sm:px-6 sm:py-6 lg:px-8">
      <header>
        <h1 className="text-3xl font-outfit font-bold text-foreground tracking-tight">Past Runs</h1>
        <p className="text-muted-foreground mt-1">Browse previous grading sessions and download reports.</p>
      </header>

      {isLoading ? (
        <div className="flex items-center justify-center p-20">
          <Loader2 size={28} className="animate-spin text-primary/40" />
        </div>
      ) : runs.length === 0 ? (
        <div className="flex flex-col items-center justify-center p-20 border-2 border-dashed border-border rounded-2xl text-center">
          <AlertCircle size={28} className="text-muted-foreground/30 mb-3" />
          <p className="font-bold text-muted-foreground/60">No past runs found</p>
          <p className="text-sm text-muted-foreground mt-1">Run the auto-grader to generate output.</p>
        </div>
      ) : (
        <div className="flex flex-col gap-2">
          {runs.map(run => (
            <div
              key={run.id}
              className="flex flex-col gap-4 rounded-xl border border-border border-l-2 border-l-primary/20 bg-card px-4 py-4 shadow-sm transition-all duration-200 hover:border-l-primary/60 hover:bg-secondary/40 hover:shadow-md lg:flex-row lg:items-center lg:justify-between"
            >
              {/* Run info */}
              <div className="flex items-start gap-3">
                <div className="w-10 h-10 bg-primary/10 rounded-xl flex items-center justify-center shrink-0">
                  <FileText size={17} className="text-primary" />
                </div>
                <div>
                  <p className="text-base font-semibold text-foreground">{formatRunTimestamp(run.timestamp)}</p>
                  <div className="mt-0.5 flex flex-wrap items-center gap-2 sm:gap-3">
                    <span className="text-[11px] text-muted-foreground flex items-center gap-1">
                      <Users size={11} /> {run.studentCount} students
                    </span>
                    {run.hasPdf && (
                      <span className="text-[10px] px-1.5 py-0.5 bg-primary/10 text-primary rounded font-bold">PDF</span>
                    )}
                    {run.hasCsv && (
                      <span className="text-[10px] px-1.5 py-0.5 bg-vsc-green/10 text-vsc-green rounded font-bold">CSV</span>
                    )}
                  </div>
                </div>
              </div>

              {/* Actions */}
              <div className="flex w-full flex-wrap items-center gap-2 lg:w-auto lg:justify-end">
                {run.hasPlagiarism && (
                  <button
                    onClick={() => window.open(`http://localhost:1996/?file=/api/reports/${run.id}/plagiarism`, '_blank')}
                    className="flex w-full items-center justify-center gap-1.5 rounded-lg border border-border bg-card px-3 py-1.5 text-xs font-medium text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground sm:w-auto"
                  >
                    <ExternalLink size={12} /> JPlag
                  </button>
                )}
                {run.hasPdf && (
                  <button
                    onClick={() => window.open(pdfUrl(run.id, (run as any).pdfFilename), '_blank')}
                    className="flex w-full items-center justify-center gap-1.5 rounded-lg border border-border bg-card px-3 py-1.5 text-xs font-medium text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground sm:w-auto"
                  >
                    <Eye size={12} /> PDF
                  </button>
                )}
                {run.hasCsv && (
                  <button
                    onClick={() => window.open(`/api/reports/${run.id}/csv`, '_blank')}
                    className="flex w-full items-center justify-center gap-1.5 rounded-lg border border-border bg-card px-3 py-1.5 text-xs font-medium text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground sm:w-auto"
                  >
                    <Download size={12} /> CSV
                  </button>
                )}
                <button
                  onClick={() => navigate(`/past-runs/${run.id}`)}
                  className="flex w-full items-center justify-center gap-1.5 rounded-lg bg-primary px-3 py-1.5 text-xs font-semibold text-primary-foreground transition-colors hover:bg-primary/90 sm:w-auto"
                >
                  View Results <ArrowRight size={12} />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default PastRuns;

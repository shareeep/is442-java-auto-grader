import React, { useState, useRef, useEffect } from 'react';
import gsap from 'gsap';
import { FolderArchive, FileCode2, FileSpreadsheet, Play, ChevronRight, ChevronLeft } from 'lucide-react';
import { FileUploadCard } from '../components/FileUploadCard';
import ResultsTable from '../components/ResultsTable';

const GraderWorkspace: React.FC = () => {
  const [step, setStep] = useState(1);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<any>(null);
  const [fileCounts, setFileCounts] = useState({ submissions: 0, testers: 0, scoresheet: 0 });
  const [csvFile, setCsvFile] = useState<File | null>(null);
  const [csvContent, setCsvContent] = useState<string[][] | null>(null);
  const [showCsvModal, setShowCsvModal] = useState(false);
  
  const resultsRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const stepContainerRef = useRef<HTMLDivElement>(null);

  // GSAP Step Transition
  useEffect(() => {
    if (stepContainerRef.current && !result) {
      gsap.fromTo(stepContainerRef.current, 
        { autoAlpha: 0, x: 20 }, 
        { autoAlpha: 1, x: 0, duration: 0.4, ease: "power3.out" }
      );
    }
  }, [step, result]);

  // CSV File Reading
  useEffect(() => {
    if (csvFile) {
      const reader = new FileReader();
      reader.onload = (e) => {
        const text = e.target?.result as string;
        if (text) {
          const rows = text.split(/\r?\n/).filter(r => r.trim() !== '').map(r => r.split(','));
          setCsvContent(rows);
        }
      };
      reader.readAsText(csvFile);
    } else {
      setCsvContent(null);
    }
  }, [csvFile]);

  const canProceed = () => {
    if (step === 1) return fileCounts.submissions > 0;
    if (step === 2) return fileCounts.testers > 0;
    return true; // Step 3 optional
  };

  const handleNext = () => {
    if (canProceed() && step < 3) setStep(s => s + 1);
  };
  
  const handleBack = () => {
    if (step > 1) setStep(s => s - 1);
  };

  const handleGradeSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (step < 2) return; // Prevent enter key early submission on Step 1

    setLoading(true);
    setResult(null);
    setError(null);

    const formElement = e.currentTarget;
    const body = new FormData(formElement);

    try {
      const response = await fetch('http://localhost:8080/api/grade', {
        method: 'POST',
        body: body,
      });

      if (!response.ok) {
        throw new Error(`${response.status}: ${response.statusText}`);
      }

      const data = await response.json();
      setResult(data);
    } catch (err: any) {
      console.error(err);
      if (err.message && err.message.match(/^\d{3}:/)) {
        setError(`Auto-Grader Pipeline Error (${err.message}). The backend might be missing files due to a browser transmission failure.`);
      } else {
        setError("Cannot connect to the backend server. Please verify the Spring Boot Auto-Grader is running on port 8080.");
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (result && resultsRef.current) {
      gsap.fromTo(resultsRef.current, 
        { y: 30, opacity: 0 }, 
        { y: 0, opacity: 1, duration: 0.6, ease: "power3.out" }
      );
    }
  }, [result]);

  const onBtnEnter = () => {
    if(!loading && canProceed()) gsap.to(buttonRef.current, { scale: 1.02, duration: 0.3, ease: 'power3.out' });
  };
  const onBtnLeave = () => {
    if(!loading) gsap.to(buttonRef.current, { scale: 1, duration: 0.3, ease: 'power3.out' });
  };

  return (
    <div className="flex flex-col gap-8 pb-16 max-w-4xl mx-auto w-full">
      <header className="mb-4">
        <h1 className="text-4xl md:text-5xl font-outfit font-bold text-primary mb-3 tracking-tight">
          Auto-Grader
        </h1>
        <p className="text-charcoal/60 font-sans text-base max-w-2xl">
          Upload student submissions and JUnit test cases to compile code, execute tests, and generate automated grading reports.
        </p>
      </header>

      {!result ? (
        <form onSubmit={handleGradeSubmit} className="glass-card p-8 md:p-12 relative overflow-hidden flex flex-col min-h-[520px]">
          <div className="absolute top-0 left-0 w-1.5 h-full bg-primary/20"></div>
          
          {/* Progress Indicators */}
          <div className="flex items-center gap-3 mb-10">
            {[1, 2, 3].map((s) => (
              <div key={s} className="flex-1 flex flex-col gap-2">
                <div className={`h-1.5 rounded-full transition-colors duration-500 ${step >= s ? 'bg-accent' : 'bg-primary/10'}`} />
                <span className={`text-[10px] font-mono tracking-widest uppercase transition-colors duration-500 ${step >= s ? 'text-accent font-bold' : 'text-primary/30'}`}>
                  Step 0{s}
                </span>
              </div>
            ))}
          </div>

          {/* Active Step Content */}
          <div ref={stepContainerRef} className="flex-1 flex flex-col justify-center relative">
            
            <div className={step === 1 ? 'block' : 'hidden'}>
              <FileUploadCard 
                label="Student Submissions" 
                description={<>Upload <span className="font-bold text-accent">student-submission</span> folder.</>}
                icon={<FolderArchive size={40} />}
                isRequired={true}
                isDirectory={true}
                count={fileCounts.submissions}
                name="submissions"
                className="h-64"
                onChange={(e) => {
                  setFileCounts(p => ({...p, submissions: e.target.files?.length || 0}));
                  setError(null);
                }}
              />
            </div>
            
            <div className={step === 2 ? 'block' : 'hidden'}>
              <FileUploadCard 
                label="Test Cases" 
                description={<>Upload <span className="font-bold text-accent">Tester-Files</span> folder.</>}
                icon={<FileCode2 size={40} />}
                isRequired={true}
                isDirectory={true}
                count={fileCounts.testers}
                name="testers"
                className="h-64"
                onChange={(e) => {
                  setFileCounts(p => ({...p, testers: e.target.files?.length || 0}));
                  setError(null);
                }}
              />
            </div>

            <div className={step === 3 ? 'block' : 'hidden'}>
              <FileUploadCard 
                label="Grade Mapping (.csv)" 
                description={<>Upload <span className="font-bold text-accent">IS442-ScoreSheet.csv</span> file (Optional).</>}
                icon={<FileSpreadsheet size={40} />}
                isRequired={false}
                accept=".csv"
                count={fileCounts.scoresheet}
                name="scoresheet"
                className="h-64"
                onChange={(e) => {
                  const files = e.target.files;
                  setFileCounts(p => ({...p, scoresheet: files?.length || 0}));
                  if (files && files.length > 0) {
                    setCsvFile(files[0]);
                  } else {
                    setCsvFile(null);
                  }
                  setError(null);
                }}
              />
              {fileCounts.scoresheet > 0 && csvContent && (
                <div className="mt-4 flex justify-center animate-in fade-in pt-2">
                  <button 
                    type="button" 
                    onClick={() => setShowCsvModal(true)}
                    className="flex items-center gap-2 px-5 py-2.5 rounded-xl border border-primary/20 text-primary font-sans font-bold text-sm hover:border-accent hover:text-accent transition-all bg-white/50 hover:bg-white shadow-sm hover:shadow-md"
                  >
                    View Grade Mapping Data
                  </button>
                </div>
              )}
            </div>

          </div>

          {/* Navigation Controls */}
          <div className="mt-8 pt-6 flex flex-col sm:flex-row items-center justify-between gap-4 border-t border-primary/10 relative">
            
            {/* Error Message Tooltip */}
            {error && (
              <div className="absolute right-0 bottom-full mb-4 w-max max-w-[320px] bg-red-50 text-red-600 font-sans text-sm font-semibold px-4 py-3 rounded-xl border border-red-200/60 shadow-lg animate-in fade-in slide-in-from-bottom-2">
                {error}
              </div>
            )}

            <button
              type="button"
              onClick={handleBack}
              disabled={step === 1 || loading}
              className={`flex items-center gap-2 font-sans font-bold text-sm px-6 py-3 rounded-lg transition-all duration-300 ${step === 1 ? 'opacity-0 cursor-default' : 'text-charcoal/50 hover:bg-black/5 hover:text-charcoal'}`}
            >
              <ChevronLeft size={16} /> Back
            </button>

            <div className="flex items-center gap-4">
              {step < 3 && (
                <button
                  type="button"
                  onClick={handleNext}
                  disabled={!canProceed()}
                  className={`flex items-center gap-2 ${step === 2 ? 'bg-charcoal/10 text-charcoal border hover:bg-charcoal/20' : 'bg-charcoal text-background shadow-md'} px-6 py-3.5 rounded-xl font-sans font-bold transition-all duration-300 ${!canProceed() ? 'opacity-40 cursor-not-allowed shadow-none' : 'hover:scale-105'}`}
                >
                  {step === 2 ? 'Add CSV (Optional)' : 'Continue'} <ChevronRight size={16} />
                </button>
              )}

              {(step === 3 || (step === 2 && canProceed())) && (
                <button 
                  ref={buttonRef}
                  type="submit" 
                  disabled={loading}
                  onMouseEnter={onBtnEnter}
                  onMouseLeave={onBtnLeave}
                  className={`flex items-center gap-3 bg-accent text-background px-8 py-3.5 rounded-xl font-sans font-bold shadow-[0_8px_20px_rgba(204,88,51,0.25)] transition-all duration-300 disabled:opacity-50 disabled:cursor-not-allowed`}
                >
                  {loading ? (
                    <span className="w-5 h-5 border-2 border-background/20 border-t-background rounded-full animate-spin"></span>
                  ) : (
                    <><Play size={18} fill="currentColor" /> Run Auto-Grader</>
                  )}
                </button>
              )}
            </div>
          </div>
        </form>
      ) : (
        /* Results Section */
        <div ref={resultsRef}>
          <div className="flex justify-between items-end mb-6">
            <div className="flex flex-col gap-1">
              <h2 className="font-outfit text-2xl font-bold text-primary">Grading Report</h2>
              {csvContent && (
                <button 
                  type="button" 
                  onClick={() => setShowCsvModal(true)}
                  className="flex items-center gap-1.5 text-xs font-mono font-bold uppercase tracking-widest text-primary/60 hover:text-accent transition-colors w-max"
                >
                  <FileSpreadsheet size={14} /> View Grade Mapping
                </button>
              )}
            </div>
            <button 
              type="button"
              onClick={() => {
                setResult(null);
                setStep(1);
                setFileCounts({ submissions: 0, testers: 0, scoresheet: 0 });
                setCsvFile(null);
              }}
              className="text-accent hover:underline font-sans text-sm font-bold"
            >
              Start New Run
            </button>
          </div>
          <ResultsTable data={result} />
        </div>
      )}

      {/* CSV Modal Overlay */}
      {showCsvModal && csvContent && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-charcoal/60 backdrop-blur-sm animate-in fade-in">
          <div className="bg-background max-w-4xl w-full max-h-[85vh] flex flex-col rounded-2xl overflow-hidden shadow-2xl relative border border-primary/10">
            <div className="p-6 border-b border-primary/10 flex justify-between items-center bg-primary/5">
              <h2 className="font-outfit text-xl font-bold text-primary flex items-center gap-2">
                <FileSpreadsheet size={20} className="text-accent" />
                Grade Mapping Data Preview
              </h2>
              <button 
                type="button" 
                onClick={() => setShowCsvModal(false)}
                className="w-8 h-8 rounded-full flex items-center justify-center bg-primary/10 text-primary hover:bg-accent hover:text-white transition-colors"
                aria-label="Close modal"
              >
                ✕
              </button>
            </div>
            <div className="p-0 overflow-auto flex-1 bg-white">
              <table className="w-full text-left border-collapse">
                <thead className="sticky top-0 bg-primary/5 shadow-sm z-10">
                  <tr>
                    {csvContent[0]?.map((header, i) => (
                      <th key={i} className="py-3 px-6 font-mono text-[10px] uppercase tracking-widest text-primary/70 border-b border-primary/10 whitespace-nowrap">{header}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {csvContent.slice(1).map((row, i) => (
                    <tr key={i} className="border-b border-primary/5 hover:bg-primary/[0.02] transition-colors">
                      {row.map((cell, j) => (
                        <td key={j} className="py-3 px-6 font-sans text-sm text-charcoal/80 whitespace-nowrap leading-tight">{cell}</td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
              {csvContent.length <= 1 && (
                <div className="text-center py-12 text-charcoal/50 font-sans text-sm flex flex-col items-center gap-3 bg-white">
                  <FileSpreadsheet size={32} className="opacity-20" />
                  No data rows found in this file.
                </div>
              )}
            </div>
            <div className="p-4 border-t border-primary/10 bg-primary/5 flex justify-end">
              <button 
                type="button" 
                onClick={() => setShowCsvModal(false)} 
                className="bg-primary text-background px-6 py-2 rounded-lg font-sans font-bold text-sm hover:opacity-90 transition-opacity shadow-sm"
              >
                Close Viewer
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default GraderWorkspace;

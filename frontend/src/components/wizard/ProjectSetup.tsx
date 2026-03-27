import React, { useRef, useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { FileUp, FolderOpen, AlertCircle, CheckCircle2, Loader2, ScanText, Upload } from 'lucide-react';
import { uploadExam, uploadTemplate, uploadTesters, analyzeSetup, preparsePdf } from '@/api/client';

interface ProjectSetupProps {
  data: any;
  onUpdate: (data: any) => void;
  onNext: () => void;
}

interface FolderUploadCardProps {
  title: string;
  hint: string;
  uploadId: string | null;
  onUpload: (files: File[]) => Promise<void>;
}

const FolderUploadCard: React.FC<FolderUploadCardProps> = ({ title, hint, uploadId, onUpload }) => {
  const inputRef = useRef<HTMLInputElement>(null);
  const [fileCount, setFileCount] = useState(0);
  const [folderName, setFolderName] = useState('');
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files || []);
    if (files.length === 0) return;

    const root = files[0].webkitRelativePath?.split('/')[0] ?? '';
    setFolderName(root);
    setFileCount(files.length);
    setUploading(true);
    setError(null);

    try {
      await onUpload(files);
    } catch (err: any) {
      setError(err.message);
    } finally {
      setUploading(false);
    }
  };

  return (
    <Card className="border-border bg-card min-h-[280px]">
      <CardHeader className="pb-3">
        <div className="flex items-center gap-3">
          <div className="p-2 bg-accent/10 rounded text-accent">
            <FolderOpen size={18} />
          </div>
          <CardTitle>{title}</CardTitle>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        {/* Folder picker + uploader */}
        <div
          onClick={() => !uploading && inputRef.current?.click()}
          className={`flex items-center gap-3 px-4 py-3 rounded-lg border cursor-pointer transition-all duration-200 group ${
            uploadId
              ? 'border-vsc-green/40 bg-vsc-green/5 hover:border-vsc-green/60'
              : uploading
                ? 'border-primary/40 bg-primary/5'
                : 'border-border bg-secondary/50 hover:border-primary/30 hover:bg-secondary'
          }`}
        >
          <input
            ref={inputRef}
            type="file"
            // @ts-ignore
            webkitdirectory=""
            directory=""
            multiple
            className="hidden"
            onChange={handleChange}
          />
          <div className={`shrink-0 transition-colors ${
            uploadId ? 'text-vsc-green'
              : uploading ? 'text-primary'
              : 'text-muted-foreground group-hover:text-foreground'
          }`}>
            {uploading ? <Loader2 size={18} className="animate-spin" />
              : uploadId ? <CheckCircle2 size={18} />
              : <Upload size={18} />}
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-sm font-medium text-foreground truncate">
              {uploading ? 'Uploading...'
                : uploadId ? folderName
                : 'Click to select & upload folder'}
            </p>
            {uploadId && (
              <p className="text-[10px] text-vsc-green mt-0.5">{fileCount} files uploaded ✓</p>
            )}
          </div>
          {!uploadId && !uploading && (
            <span className="text-xs text-muted-foreground group-hover:text-foreground transition-colors shrink-0">
              Browse
            </span>
          )}
        </div>

        {/* Error */}
        {error && (
          <div className="flex items-center gap-2 p-2.5 bg-destructive/10 text-destructive rounded-md border border-destructive/20 text-xs">
            <AlertCircle size={14} />
            <p>{error}</p>
          </div>
        )}

        <p className="text-[10px] text-muted-foreground">{hint}</p>
      </CardContent>
    </Card>
  );
};

const ProjectSetup: React.FC<ProjectSetupProps> = ({ data, onUpdate, onNext }) => {
  const [file, setFile] = useState<File | null>(null);

  // PDF upload state 
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);

  // PDF parsing state
  const [parsingPdf, setParsingPdf] = useState(false);

  // Inference state
  const [inferring, setInferring] = useState(false);
  const [inferError, setInferError] = useState<string | null>(null);

  // Progress tracking
  const [progress, setProgress] = useState<{ exam: boolean; template: boolean; testers: boolean }>({
    exam: !!data.examId,
    template: !!data.templateId,
    testers: !!data.testerId,
  });

  const completedCount = [progress.exam, progress.template, progress.testers].filter(Boolean).length;
  const allReady = completedCount === 3 && !parsingPdf;

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      const selectedFile = e.target.files[0];
      setFile(selectedFile);
      setUploadError(null);
      
      // Auto-upload PDF when file is selected
      setUploading(true);
      setProgress(p => ({ ...p, exam: false }));
      setParsingPdf(false);
      try {
        const uploaded = await uploadExam(selectedFile);
        onUpdate({ examId: uploaded.examId });
        
        // Start PDF parsing in background
        setParsingPdf(true);
        preparsePdf(uploaded.examId).then(result => {
          if (result.status === 'error') {
            console.warn('PDF parsing failed, will retry on inference');
          }
          setParsingPdf(false);
        }).catch(err => {
          console.warn('Background PDF parse failed:', err);
          setParsingPdf(false);
        });
        
        setProgress(p => ({ ...p, exam: true }));
      } catch (err: any) {
        setUploadError(err.message);
      } finally {
        setUploading(false);
      }
    }
  };

  const handleUploadTemplate = async (files: File[]) => {
    setProgress(p => ({ ...p, template: false }));
    const result = await uploadTemplate(files);
    onUpdate({ templateId: result.templateId });
    setProgress(p => ({ ...p, template: true }));
  };

  const handleUploadTesters = async (files: File[]) => {
    setProgress(p => ({ ...p, testers: false }));
    const result = await uploadTesters(files);
    onUpdate({ testerId: result.testerId });
    setProgress(p => ({ ...p, testers: true }));
  };

  const handleBeginInference = async () => {
    const examId = data.examId;
    if (!examId) {
      setInferError('Process the PDF first before running inference.');
      return;
    }
    setInferring(true);
    setInferError(null);
    try {
      const inferredConfig = await analyzeSetup({
        examId,
        templateId: data.templateId,
        testerId: data.testerId,
      });
      onUpdate({ inferredConfig });
      onNext();
    } catch (err: any) {
      setInferError(err.message);
    } finally {
      setInferring(false);
    }
  };

  return (
    <div className="grid grid-cols-1 md:grid-cols-3 gap-4 pb-20">
      {/* PDF Upload */}
      <Card className="border-border bg-card min-h-[280px]">
        <CardHeader>
          <div className="flex items-center gap-3">
            <div className="p-2 bg-accent/10 rounded text-accent">
              <FileUp size={20} />
            </div>
            <div>
              <CardTitle>Exam PDF</CardTitle>
              <CardDescription>Upload the exam PDF</CardDescription>
            </div>
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          {/* Dropzone */}
          <div className="flex flex-col items-center justify-center border-2 border-dashed border-border rounded-lg p-10 bg-secondary/50 hover:bg-secondary hover:border-primary/30 transition-colors group cursor-pointer relative">
            <input
              type="file"
              className="absolute inset-0 opacity-0 cursor-pointer"
              onChange={handleFileChange}
              accept=".pdf"
            />
            <div className="w-14 h-14 bg-card rounded-lg flex items-center justify-center border border-border group-hover:border-primary/30 transition-colors mb-4">
              {data.examId
                ? <CheckCircle2 className="text-vsc-green" size={28} />
                : <FileUp className="text-muted-foreground" size={28} />
              }
            </div>
            <p className="font-outfit font-bold text-foreground">
              {file ? file.name : 'Click or drag PDF to upload'}
            </p>
            <p className="text-xs text-muted-foreground mt-1">Maximum size 10MB</p>
          </div>

          {/* Upload error */}
          {uploadError && (
            <div className="flex items-center gap-3 p-3 bg-destructive/10 text-destructive rounded-md border border-destructive/20 animate-in fade-in">
              <AlertCircle size={16} />
              <p className="text-sm">{uploadError}</p>
            </div>
          )}

          {/* Upload success */}
          {data.examId && !uploadError && (
            <div className={`flex items-center gap-3 p-3 rounded-md border animate-in fade-in ${
              parsingPdf 
                ? 'bg-primary/10 text-primary border-primary/20' 
                : 'bg-vsc-green/10 text-vsc-green border-vsc-green/20'
            }`}>
              {parsingPdf ? <Loader2 size={16} className="animate-spin" /> : <CheckCircle2 size={16} />}
              <p className="text-sm font-medium">
                {parsingPdf ? 'Analyzing PDF...' : 'PDF uploaded and ready for inference.'}
              </p>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Template Directory */}
      <FolderUploadCard
        title="Template Directory"
        hint="Select the folder containing student code templates (e.g. RenameToYourUsername with Q1, Q2 subfolders)."
        uploadId={data.templateId}
        onUpload={handleUploadTemplate}
      />

      {/* Testers Directory */}
      <FolderUploadCard
        title="Testers Directory"
        hint="Select the folder containing existing Tester.java files (e.g. Tester-Files with Q1Tester.java, etc.)."
        uploadId={data.testerId}
        onUpload={handleUploadTesters}
      />

      {/* Inference error */}
      {inferError && (
        <div className="md:col-span-3 flex items-center gap-3 p-4 bg-destructive/10 text-destructive rounded-md border border-destructive/20 animate-in fade-in slide-in-from-top-2">
          <AlertCircle size={18} />
          <p className="text-sm font-medium">{inferError}</p>
        </div>
      )}

      <div className="md:col-span-3 flex justify-center">
        <Button
          size="lg"
          onClick={handleBeginInference}
          disabled={inferring || parsingPdf || !allReady}
          className="min-w-[280px] h-12 rounded-md glow-blue"
        >
          {parsingPdf ? (
            <>
              Analyzing PDF...
            </>
          ) : inferring ? (
            <>
              <Loader2 size={16} className="animate-spin mr-2" />
              Inferring...
            </>
          ) : allReady ? (
            <>Begin Inference ({completedCount}/3 ready)</>
          ) : (
            <>Upload {3 - completedCount} more to continue</>
          )}
        </Button>
      </div>
    </div>
  );
};

export default ProjectSetup;

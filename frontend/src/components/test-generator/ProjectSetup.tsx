import React, { useRef, useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { FileUp, FolderOpen, AlertCircle, CheckCircle2, Loader2, Upload } from 'lucide-react';
import { uploadTemplate } from '@/api/uploadTemplate';
import { analyzeSetup, preparsePdf, uploadExam, uploadTesters } from '@/generated/sdk.gen';
import { useWizardStore } from '../../store/wizardStore';
import { useShallow } from 'zustand/react/shallow';

interface ProjectSetupProps {
  onNext: () => void;
}

interface FolderUploadCardProps {
  title: string;
  hint: string;
  uploadId: string | null;
  onUpload: (files: File[]) => Promise<void>;
  footer?: React.ReactNode;
  disabled?: boolean;
}

const FolderUploadCard: React.FC<FolderUploadCardProps> = ({ title, hint, uploadId, onUpload, footer, disabled }) => {
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
    <Card className="border-border bg-card">
      <CardHeader className="pb-3">
        <div className="flex items-center gap-3">
          <div className="p-2 bg-accent/10 rounded text-accent shrink-0">
            <FolderOpen size={18} />
          </div>
          <CardTitle className="text-base">{title}</CardTitle>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        <div
          onClick={() => !uploading && !disabled && inputRef.current?.click()}
          className={`flex items-center gap-3 px-4 py-3 rounded-lg border transition-all duration-200 group ${
            disabled
              ? 'border-border bg-secondary/30 opacity-40 cursor-not-allowed'
              : uploadId
                ? 'border-vsc-green/40 bg-vsc-green/5 hover:border-vsc-green/60 cursor-pointer'
                : uploading
                  ? 'border-primary/40 bg-primary/5 cursor-pointer'
                  : 'border-border bg-secondary/50 hover:border-primary/30 hover:bg-secondary cursor-pointer'
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
            <p className="text-sm font-medium text-foreground">
              {uploading ? 'Uploading...'
                : uploadId ? (folderName || 'Uploaded')
                : 'Click to select & upload folder'}
            </p>
            {uploadId && (
              <p className="text-[10px] text-vsc-green mt-0.5">{fileCount > 0 ? `${fileCount} files uploaded ✓` : 'Uploaded ✓'}</p>
            )}
          </div>
          {!uploadId && !uploading && (
            <span className="text-xs text-muted-foreground group-hover:text-foreground transition-colors shrink-0">
              Browse
            </span>
          )}
        </div>

        {error && (
          <div className="flex items-center gap-2 p-2.5 bg-destructive/10 text-destructive rounded-md border border-destructive/20 text-xs">
            <AlertCircle size={14} />
            <p>{error}</p>
          </div>
        )}

        <p className="text-[10px] text-muted-foreground">{hint}</p>
        {footer}
      </CardContent>
    </Card>
  );
};

const ProjectSetup: React.FC<ProjectSetupProps> = ({ onNext }) => {
  const { examId, templateId, testerId } = useWizardStore(
    useShallow((s) => ({ examId: s.examId, templateId: s.templateId, testerId: s.testerId }))
  );
  const setExamId = useWizardStore((s) => s.setExamId);
  const setTemplateId = useWizardStore((s) => s.setTemplateId);
  const setTesterId = useWizardStore((s) => s.setTesterId);
  const setInferredConfig = useWizardStore((s) => s.setInferredConfig);

  const [file, setFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [parsingPdf, setParsingPdf] = useState(false);
  const [inferring, setInferring] = useState(false);
  const [inferError, setInferError] = useState<string | null>(null);
  const [noTesters, setNoTesters] = useState(false);

  const allReady = !!examId && !!templateId && (!!testerId || noTesters) && !parsingPdf;
  const completedCount = [examId, templateId, testerId || noTesters].filter(Boolean).length;

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      const selectedFile = e.target.files[0];
      setFile(selectedFile);
      setUploadError(null);
      setUploading(true);
      setParsingPdf(false);
      try {
        const { data: uploaded } = await uploadExam({ body: { file: selectedFile }, throwOnError: true });
        setExamId(uploaded!['examId']);

        setParsingPdf(true);
        preparsePdf({ body: { examId: uploaded.examId }, throwOnError: true }).then(({ data: result }) => {
          if ((result as any).status === 'error') {
            console.warn('PDF parsing failed, will retry on inference');
          }
          setParsingPdf(false);
        }).catch(err => {
          console.warn('Background PDF parse failed:', err);
          setParsingPdf(false);
        });
      } catch (err: any) {
        setUploadError(err.message);
      } finally {
        setUploading(false);
      }
    }
  };

  const handleUploadTemplate = async (files: File[]) => {
    const result = await uploadTemplate(files);
    setTemplateId(result.templateId);
  };

  const handleUploadTesters = async (files: File[]) => {
    const { data: result } = await uploadTesters({ body: { files }, throwOnError: true });
    setTesterId((result as any).testerId);
  };

  const handleBeginInference = async () => {
    if (!examId) {
      setInferError('Process the PDF first before running inference.');
      return;
    }
    setInferring(true);
    setInferError(null);
    try {
      const { data: inferredConfig } = await analyzeSetup({ body: { examId, templateId: templateId ?? undefined, testerId: testerId ?? undefined }, throwOnError: true });
      setInferredConfig(inferredConfig);
      onNext();
    } catch (err: any) {
      setInferError(err.message);
    } finally {
      setInferring(false);
    }
  };

  return (
    <div className="flex flex-col gap-4 pb-20">
      {/* Main upload row: PDF square + two dir cards stacked */}
      <div className="grid grid-cols-[1fr_2fr] gap-4 items-stretch">
        {/* PDF Upload — square card */}
        <Card className="border-border bg-card">
          <CardHeader className="pb-3">
            <div className="flex items-center gap-3">
              <div className="p-2 bg-accent/10 rounded text-accent shrink-0">
                <FileUp size={20} />
              </div>
              <CardTitle>Exam PDF</CardTitle>
            </div>
          </CardHeader>
          <CardContent className="space-y-4">
            <label className="flex flex-col items-center justify-center border-2 border-dashed border-border rounded-lg p-8 bg-secondary/50 hover:bg-secondary hover:border-primary/30 transition-colors cursor-pointer">
              <input
                type="file"
                className="hidden"
                onChange={handleFileChange}
                accept=".pdf"
              />
              <div className="w-14 h-14 bg-card rounded-lg flex items-center justify-center border border-border mb-4">
                {uploading
                  ? <Loader2 className="text-primary animate-spin" size={28} />
                  : examId
                    ? <CheckCircle2 className="text-vsc-green" size={28} />
                    : <FileUp className="text-muted-foreground" size={28} />
                }
              </div>
              <p className="font-outfit font-bold text-foreground text-center">
                {uploading ? 'Uploading...' : file ? file.name : examId ? 'PDF uploaded' : 'Click or drag PDF to upload'}
              </p>
              <p className="text-xs text-muted-foreground mt-1">Maximum size 10MB</p>
            </label>

            {uploadError && (
              <div className="flex items-center gap-3 p-3 bg-destructive/10 text-destructive rounded-md border border-destructive/20 animate-in fade-in">
                <AlertCircle size={16} />
                <p className="text-sm">{uploadError}</p>
              </div>
            )}

            {examId && !uploadError && (
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

        {/* Two directory cards stacked */}
        <div className="flex flex-col gap-4">
          <FolderUploadCard
            title="Template Directory"
            hint="Select the folder containing student code templates (e.g. RenameToYourUsername with Q1, Q2 subfolders)."
            uploadId={templateId}
            onUpload={handleUploadTemplate}
          />
          <FolderUploadCard
            key={noTesters ? 'testers-disabled' : 'testers-enabled'}
            title="Testers Directory"
            hint="Select the folder containing existing Tester.java files (e.g. Tester-Files with Q1Tester.java, etc.)."
            uploadId={testerId}
            onUpload={handleUploadTesters}
            disabled={noTesters}
            footer={
              <label className="flex items-center gap-2 cursor-pointer pt-1">
                <Checkbox
                  checked={noTesters}
                  onCheckedChange={(v) => {
                    setNoTesters(!!v);
                    if (v) setTesterId(undefined);
                  }}
                />
                <span className="text-xs text-muted-foreground select-none">No existing testers — generate from scratch</span>
              </label>
            }
          />
        </div>
      </div>

      {inferError && (
        <div className="flex items-center gap-3 p-4 bg-destructive/10 text-destructive rounded-md border border-destructive/20 animate-in fade-in slide-in-from-top-2">
          <AlertCircle size={18} />
          <p className="text-sm font-medium">{inferError}</p>
        </div>
      )}

      <div className="flex justify-center">
        <Button
          size="lg"
          onClick={handleBeginInference}
          disabled={inferring || parsingPdf || !allReady}
          className="min-w-[280px] h-12 rounded-md glow-blue"
        >
          {parsingPdf ? (
            <>Analyzing PDF...</>
          ) : inferring ? (
            <><Loader2 size={16} className="animate-spin mr-2" />Inferring...</>
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

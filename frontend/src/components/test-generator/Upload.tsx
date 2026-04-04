import React, { useRef, useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { FileUp, FolderOpen, AlertCircle, CheckCircle2, Loader2, Upload, ChevronRight } from 'lucide-react';
import { uploadTemplate } from '@/api/uploadTemplate';
import { analyzeSetup, parsePdf, uploadExam, uploadTesters } from '@/generated/sdk.gen';
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
              <p className="text-xs text-vsc-green mt-0.5">{fileCount > 0 ? `${fileCount} files uploaded ✓` : 'Uploaded ✓'}</p>
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

        <p className="text-xs text-muted-foreground">{hint}</p>
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

  const pdfInputRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [parsingPdf, setParsingPdf] = useState(false);
  const [pdfDragging, setPdfDragging] = useState(false);
  const [inferring, setInferring] = useState(false);
  const [inferError, setInferError] = useState<string | null>(null);

  const allReady = !!examId && !!templateId && !parsingPdf;

  const uploadPdfFile = async (selectedFile: File) => {
    setFile(selectedFile);
    setUploadError(null);
    setUploading(true);
    setParsingPdf(false);
    try {
      const { data: uploaded } = await uploadExam({ body: { file: selectedFile }, throwOnError: true });
      const eid = uploaded!['examId'];
      setExamId(eid);
      setParsingPdf(true);
      parsePdf({ path: { examId: eid }, throwOnError: true }).then(({ data: result }: any) => {
        if (result?.status === 'error') console.warn('PDF parsing failed, will retry on inference');
        setParsingPdf(false);
      }).catch((err: any) => {
        console.warn('Background PDF parse failed:', err);
        setParsingPdf(false);
      });
    } catch (err: any) {
      setUploadError(err.message);
    } finally {
      setUploading(false);
    }
  };

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) await uploadPdfFile(e.target.files[0]);
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
      const { data: inferredConfig } = await analyzeSetup({ path: { examId: examId! }, body: { templateId: templateId ?? undefined, testerId: testerId ?? undefined }, throwOnError: true });
      setInferredConfig(inferredConfig);
      onNext();
    } catch (err: any) {
      setInferError(err.message);
    } finally {
      setInferring(false);
    }
  };

  return (
    <div className="flex flex-col gap-4 pb-20 animate-in fade-in slide-in-from-right-4">
      <div className="flex justify-between items-center">
        <div>
          <h2 className="text-2xl font-bold font-outfit text-foreground">Upload</h2>
          <p className="text-muted-foreground text-sm">Upload your exam PDF, template folder, and optional testers.</p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            onClick={handleBeginInference}
            disabled={!allReady || inferring}
            className="rounded-md px-6 glow-blue"
          >
            {inferring
              ? <><Loader2 size={14} className="animate-spin mr-1" /> Running Inference...</>
              : <>Continue <ChevronRight size={16} className="ml-1" /></>
            }
          </Button>
        </div>
      </div>

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
            <div
              onClick={() => !uploading && pdfInputRef.current?.click()}
              onDragOver={e => { e.preventDefault(); if (!uploading) setPdfDragging(true); }}
              onDragEnter={e => { e.preventDefault(); if (!uploading) setPdfDragging(true); }}
              onDragLeave={e => { if (!e.currentTarget.contains(e.relatedTarget as Node)) setPdfDragging(false); }}
              onDrop={async e => {
                e.preventDefault();
                setPdfDragging(false);
                if (uploading) return;
                const dropped = e.dataTransfer.files[0];
                if (dropped) await uploadPdfFile(dropped);
              }}
              className={`flex flex-col items-center justify-center border-2 border-dashed rounded-lg p-8 transition-all duration-200 cursor-pointer ${
                pdfDragging
                  ? 'border-primary bg-primary/10 scale-[1.01]'
                  : 'border-border bg-secondary/50 hover:bg-secondary hover:border-primary/30'
              }`}
            >
              <input
                ref={pdfInputRef}
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
                    : <FileUp className={pdfDragging ? 'text-primary' : 'text-muted-foreground'} size={28} />
                }
              </div>
              <p className="font-outfit font-bold text-foreground text-center">
                {pdfDragging ? 'Drop PDF here' : uploading ? 'Uploading...' : file ? file.name : examId ? 'PDF uploaded' : 'Click or drag PDF to upload'}
              </p>
              <p className="text-xs text-muted-foreground mt-1">Maximum size 10MB</p>
            </div>

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
                  {parsingPdf ? 'Analyzing PDF...' : 'PDF uploaded.'}
                </p>
              </div>
            )}
          </CardContent>
        </Card>

        {/* Two directory cards stacked */}
        <div className="flex flex-col gap-4">
          <FolderUploadCard
            title="Template Folder"
            hint="Select the student code template folder (e.g. RenameToYourUsername)."
            uploadId={templateId}
            onUpload={handleUploadTemplate}
          />
          <FolderUploadCard
            title="Testers Directory"
            hint="Optional — upload your existing Tester-Files folder. Leave empty to generate tests from scratch."
            uploadId={testerId}
            onUpload={handleUploadTesters}
          />
        </div>
      </div>

      {inferError && (
        <div className="flex items-center gap-3 p-4 bg-destructive/10 text-destructive rounded-md border border-destructive/20 animate-in fade-in slide-in-from-top-2">
          <AlertCircle size={18} />
          <p className="text-sm font-medium">{inferError}</p>
        </div>
      )}

    </div>
  );
};

export default ProjectSetup;

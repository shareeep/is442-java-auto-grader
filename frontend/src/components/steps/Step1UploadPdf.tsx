import { useState, useRef } from "react";
import { uploadExam } from "../../api/client";

interface Props {
  onUploaded: (examId: string, fileName: string) => void;
  currentExamId: string | null;
  currentFileName: string | null;
}

export default function Step1UploadPdf({
  onUploaded,
  currentExamId,
  currentFileName,
}: Props) {
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  async function handleFile(file: File) {
    if (!file.name.toLowerCase().endsWith(".pdf")) {
      setError("Please select a PDF file.");
      return;
    }
    setError(null);
    setUploading(true);
    try {
      const res = await uploadExam(file);
      onUploaded(res.examId, res.fileName);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Upload failed");
    } finally {
      setUploading(false);
    }
  }

  return (
    <div>
      <h2 style={{ marginTop: 0 }}>Upload Exam PDF</h2>

      <div
        onDragOver={(e) => {
          e.preventDefault();
          setDragOver(true);
        }}
        onDragLeave={() => setDragOver(false)}
        onDrop={(e) => {
          e.preventDefault();
          setDragOver(false);
          const file = e.dataTransfer.files[0];
          if (file) handleFile(file);
        }}
        onClick={() => inputRef.current?.click()}
        style={{
          border: `2px dashed ${dragOver ? "#2563eb" : "#d1d5db"}`,
          borderRadius: 12,
          padding: "3rem 2rem",
          textAlign: "center",
          cursor: "pointer",
          background: dragOver ? "#eff6ff" : "#f9fafb",
          transition: "all 0.2s",
        }}
      >
        <div style={{ fontSize: "2rem", marginBottom: "0.5rem" }}>
          {uploading ? "..." : "\u{1F4C4}"}
        </div>
        <div>
          {uploading
            ? "Uploading..."
            : "Drop PDF here or click to browse"}
        </div>
        <input
          ref={inputRef}
          type="file"
          accept=".pdf"
          style={{ display: "none" }}
          onChange={(e) => {
            const file = e.target.files?.[0];
            if (file) handleFile(file);
          }}
        />
      </div>

      {error && (
        <div style={{ color: "#dc2626", marginTop: "0.75rem" }}>
          {error}
        </div>
      )}

      {currentExamId && currentFileName && (
        <div
          style={{
            marginTop: "1rem",
            padding: "0.75rem 1rem",
            background: "#ecfdf5",
            borderRadius: 8,
            color: "#065f46",
          }}
        >
          Uploaded: <strong>{currentFileName}</strong>
        </div>
      )}
    </div>
  );
}

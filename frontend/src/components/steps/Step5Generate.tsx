import { useEffect, useRef, useState } from "react";
import { generateQuestion } from "../../api/client";
import type { GenerationResult, QuestionSelection } from "../../types";

type QuestionStatus = "pending" | "generating" | "done" | "error";

interface QuestionProgress {
  questionId: string;
  status: QuestionStatus;
  result?: GenerationResult;
  error?: string;
}

interface Props {
  examId: string;
  testersDir: string | null;
  templateDir: string | null;
  selections: QuestionSelection[];
  onDone: (results: GenerationResult[]) => void;
  onError: (msg: string) => void;
  results: GenerationResult[] | null;
}

export default function Step5Generate({
  examId,
  testersDir,
  templateDir,
  selections,
  onDone,
  onError,
  results,
}: Props) {
  const started = useRef(false);
  const [progress, setProgress] = useState<QuestionProgress[]>(() =>
    selections.map((s) => ({ questionId: s.questionId, status: "pending" }))
  );

  useEffect(() => {
    if (started.current || results) return;
    started.current = true;

    async function runSequential() {
      const allResults: GenerationResult[] = [];
      const updated: QuestionProgress[] = selections.map((s) => ({
        questionId: s.questionId,
        status: "pending" as QuestionStatus,
      }));

      for (let i = 0; i < selections.length; i++) {
        const sel = selections[i];

        // Mark current as generating
        updated[i] = { ...updated[i], status: "generating" };
        setProgress([...updated]);

        try {
          const result = await generateQuestion({
            examId,
            testersDir,
            templateDir,
            questionId: sel.questionId,
            numCases: sel.numCases,
          });
          allResults.push(result);
          updated[i] = {
            questionId: sel.questionId,
            status: "done",
            result,
          };
        } catch (e) {
          const msg = e instanceof Error ? e.message : "Generation failed";
          updated[i] = {
            questionId: sel.questionId,
            status: "error",
            error: msg,
          };
          // Add a failed result so the flow can continue
          allResults.push({
            questionId: sel.questionId,
            cases: [],
            compiledOk: false,
            compileErrors: msg,
            generatedCode: "",
          });
        }

        setProgress([...updated]);
      }

      // Check if any succeeded
      const anySuccess = allResults.some((r) => r.compiledOk || r.cases.length > 0);
      if (anySuccess || allResults.length > 0) {
        onDone(allResults);
      } else {
        onError("All questions failed to generate");
      }
    }

    runSequential();
  }, [examId, testersDir, templateDir, selections, onDone, onError, results]);

  const statusIcon = (status: QuestionStatus) => {
    switch (status) {
      case "pending":
        return "\u23F3"; // hourglass
      case "generating":
        return "\u26A1"; // lightning
      case "done":
        return "\u2713"; // check
      case "error":
        return "\u2717"; // cross
    }
  };

  const statusColor = (status: QuestionStatus) => {
    switch (status) {
      case "pending":
        return "#9ca3af";
      case "generating":
        return "#f59e0b";
      case "done":
        return "#10b981";
      case "error":
        return "#ef4444";
    }
  };

  const statusText = (p: QuestionProgress) => {
    switch (p.status) {
      case "pending":
        return "Waiting...";
      case "generating":
        return "Generating...";
      case "done":
        return `Done - ${p.result?.cases.length ?? 0} case(s)`;
      case "error":
        return `Failed: ${p.error}`;
    }
  };

  const completedCount = progress.filter(
    (p) => p.status === "done" || p.status === "error"
  ).length;
  const totalCount = progress.length;

  return (
    <div>
      <h2 style={{ marginTop: 0 }}>
        Generating Test Cases ({completedCount}/{totalCount})
      </h2>
      <p style={{ color: "#6b7280", marginBottom: "1rem" }}>
        Each question is generated separately to ensure accurate context.
      </p>

      {/* Progress bar */}
      <div
        style={{
          width: "100%",
          height: 6,
          background: "#e5e7eb",
          borderRadius: 3,
          marginBottom: "1.5rem",
          overflow: "hidden",
        }}
      >
        <div
          style={{
            width: `${(completedCount / totalCount) * 100}%`,
            height: "100%",
            background: "#2563eb",
            borderRadius: 3,
            transition: "width 0.3s ease",
          }}
        />
      </div>

      {/* Per-question status */}
      {progress.map((p) => (
        <div
          key={p.questionId}
          style={{
            display: "flex",
            alignItems: "center",
            gap: 10,
            padding: "0.6rem 0.8rem",
            marginBottom: "0.5rem",
            background:
              p.status === "generating" ? "#fffbeb" : p.status === "done" ? "#f0fdf4" : p.status === "error" ? "#fef2f2" : "#f9fafb",
            borderRadius: 8,
            border: `1px solid ${p.status === "generating" ? "#fde68a" : p.status === "done" ? "#bbf7d0" : p.status === "error" ? "#fecaca" : "#e5e7eb"}`,
          }}
        >
          <span
            style={{
              fontSize: "1.1rem",
              color: statusColor(p.status),
              minWidth: 24,
              textAlign: "center",
            }}
          >
            {p.status === "generating" ? (
              <span style={{ display: "inline-block", animation: "spin 1s linear infinite" }}>
                {statusIcon(p.status)}
              </span>
            ) : (
              statusIcon(p.status)
            )}
          </span>
          <strong style={{ minWidth: 50 }}>{p.questionId}</strong>
          <span style={{ color: statusColor(p.status), fontSize: "0.9rem" }}>
            {statusText(p)}
          </span>
        </div>
      ))}

      <style>{`
        @keyframes spin {
          from { transform: rotate(0deg); }
          to { transform: rotate(360deg); }
        }
      `}</style>
    </div>
  );
}

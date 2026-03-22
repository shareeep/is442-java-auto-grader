import { useState } from "react";
import { saveResults } from "../../api/client";
import type {
  GenerationResult,
  QuestionConfig,
  SaveResponse,
} from "../../types";

interface Props {
  examId: string;
  testersDir: string | null;
  results: GenerationResult[];
  questions: QuestionConfig[];
  weights: Record<string, number[]>;
  onSaved: (response: SaveResponse) => void;
}

export default function Step7ConfirmSave({
  examId,
  testersDir,
  results,
  questions,
  weights,
  onSaved,
}: Props) {
  const [updateScores, setUpdateScores] = useState(true);
  const [outputDir, setOutputDir] = useState("generated-testers");
  const [saving, setSaving] = useState(false);
  const [saveResponse, setSaveResponse] = useState<SaveResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function handleSave() {
    setSaving(true);
    setError(null);

    try {
      const entries = results.map((r) => {
        const qc = questions.find((q) => q.questionId === r.questionId);
        const caseWeights = weights[r.questionId] ?? r.cases.map((c) => c.weight);
        return {
          questionId: r.questionId,
          testerClassName: qc?.testerClassName ?? r.questionId + "Tester",
          generatedCode: r.generatedCode,
          cases: r.cases.map((c, i) => ({
            ...c,
            weight: caseWeights[i] ?? c.weight,
          })),
        };
      });

      const resp = await saveResults({
        examId,
        testersDir,
        outputDir,
        results: entries,
        updateMaxScores: updateScores,
      });

      setSaveResponse(resp);
      onSaved(resp);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Save failed");
    } finally {
      setSaving(false);
    }
  }

  if (saveResponse) {
    return (
      <div>
        <h2 style={{ marginTop: 0 }}>Saved</h2>
        {saveResponse.savedPaths.map((p) => (
          <div
            key={p}
            style={{
              padding: "0.4rem 0",
              color: "#065f46",
            }}
          >
            {"\u2713"} {p}
          </div>
        ))}
        {saveResponse.errors.length > 0 && (
          <div style={{ marginTop: "1rem" }}>
            <strong style={{ color: "#dc2626" }}>Errors:</strong>
            {saveResponse.errors.map((e, i) => (
              <div key={i} style={{ color: "#dc2626", padding: "0.3rem 0" }}>
                {e}
              </div>
            ))}
          </div>
        )}
      </div>
    );
  }

  return (
    <div>
      <h2 style={{ marginTop: 0 }}>Confirm &amp; Save</h2>

      <div style={{ marginBottom: "1rem" }}>
        <label
          style={{
            display: "flex",
            alignItems: "center",
            gap: 8,
            cursor: "pointer",
          }}
        >
          <input
            type="checkbox"
            checked={updateScores}
            onChange={(e) => setUpdateScores(e.target.checked)}
          />
          Update config.properties max scores
        </label>
      </div>

      <div style={{ marginBottom: "1.5rem" }}>
        <label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>
          Output folder
        </label>
        <input
          type="text"
          value={outputDir}
          onChange={(e) => setOutputDir(e.target.value)}
          style={{
            width: "100%",
            padding: "0.4rem 0.6rem",
            border: "1px solid #d1d5db",
            borderRadius: 6,
          }}
        />
      </div>

      <button
        onClick={handleSave}
        disabled={saving}
        style={{
          padding: "0.6rem 2rem",
          border: "none",
          borderRadius: 6,
          background: saving ? "#93c5fd" : "#2563eb",
          color: "#fff",
          fontWeight: 600,
          cursor: saving ? "not-allowed" : "pointer",
          fontSize: "1rem",
        }}
      >
        {saving ? "Saving..." : "Save All"}
      </button>

      {error && (
        <div style={{ color: "#dc2626", marginTop: "0.75rem" }}>{error}</div>
      )}
    </div>
  );
}

import { useState } from "react";
import type { GenerationResult, QuestionConfig } from "../../types";

interface Props {
  results: GenerationResult[];
  questions: QuestionConfig[];
  weights: Record<string, number[]>;
  onWeightChanged: (questionId: string, caseIndex: number, w: number) => void;
}

export default function Step6ReviewResults({
  results,
  questions,
  weights,
  onWeightChanged,
}: Props) {
  const [expandedCode, setExpandedCode] = useState<Set<string>>(new Set());

  function toggleCode(qId: string) {
    setExpandedCode((prev) => {
      const next = new Set(prev);
      if (next.has(qId)) next.delete(qId);
      else next.add(qId);
      return next;
    });
  }

  return (
    <div>
      <h2 style={{ marginTop: 0 }}>Review Results</h2>
      <p style={{ color: "#6b7280" }}>
        Review generated test cases and adjust weights as needed.
      </p>

      {results.map((r) => {
        const qc = questions.find((q) => q.questionId === r.questionId);
        const originalMax = qc?.maxScore ?? 0;
        const caseWeights = weights[r.questionId] ?? r.cases.map((c) => c.weight);
        const generatedTotal = caseWeights.reduce((s, w) => s + w, 0);
        const newTotal = originalMax + generatedTotal;

        return (
          <div
            key={r.questionId}
            style={{
              marginBottom: "1.5rem",
              border: "1px solid #e5e7eb",
              borderRadius: 10,
              overflow: "hidden",
            }}
          >
            <div
              style={{
                background: "#f9fafb",
                padding: "0.75rem 1rem",
                borderBottom: "1px solid #e5e7eb",
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
              }}
            >
              <strong>
                {r.questionId} &mdash; {r.cases.length} generated case
                {r.cases.length !== 1 ? "s" : ""}
              </strong>
              <span style={{ color: "#6b7280", fontSize: "0.85rem" }}>
                Original max: {originalMax} | New total: {newTotal.toFixed(1)}
              </span>
            </div>

            <div style={{ padding: "0.5rem 1rem" }}>
              {/* Original tests info */}
              {qc && (
                <div
                  style={{
                    padding: "0.5rem 0",
                    color: "#6b7280",
                    fontSize: "0.85rem",
                    borderBottom: "1px solid #f3f4f6",
                    marginBottom: "0.5rem",
                  }}
                >
                  Original test cases (preserved, max score: {originalMax})
                </div>
              )}

              {/* Generated cases */}
              {r.cases.map((c, i) => (
                <div
                  key={i}
                  style={{
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "space-between",
                    padding: "0.5rem 0",
                    borderBottom:
                      i < r.cases.length - 1 ? "1px solid #f3f4f6" : "none",
                  }}
                >
                  <div>
                    <span
                      style={{
                        color: "#7c3aed",
                        fontSize: "0.8rem",
                        fontWeight: 600,
                      }}
                    >
                      Generated Test {i + 1}
                    </span>
                    <div style={{ fontSize: "0.9rem" }}>{c.description}</div>
                  </div>
                  <div
                    style={{
                      display: "flex",
                      alignItems: "center",
                      gap: 6,
                      flexShrink: 0,
                    }}
                  >
                    <span style={{ fontSize: "0.8rem", color: "#6b7280" }}>
                      Weight:
                    </span>
                    <input
                      type="number"
                      min={0}
                      step={0.5}
                      value={caseWeights[i] ?? 1}
                      onChange={(e) =>
                        onWeightChanged(
                          r.questionId,
                          i,
                          parseFloat(e.target.value) || 0
                        )
                      }
                      style={{
                        width: 60,
                        padding: "0.25rem 0.4rem",
                        border: "1px solid #d1d5db",
                        borderRadius: 4,
                        textAlign: "center",
                      }}
                    />
                  </div>
                </div>
              ))}
            </div>

            {/* Code toggle */}
            <div
              style={{
                borderTop: "1px solid #e5e7eb",
                padding: "0.5rem 1rem",
              }}
            >
              <button
                onClick={() => toggleCode(r.questionId)}
                style={{
                  background: "none",
                  border: "none",
                  color: "#2563eb",
                  cursor: "pointer",
                  fontSize: "0.85rem",
                  padding: 0,
                }}
              >
                {expandedCode.has(r.questionId) ? "Hide Code" : "Show Code"}
              </button>
              {expandedCode.has(r.questionId) && (
                <pre
                  style={{
                    marginTop: "0.5rem",
                    padding: "0.75rem",
                    background: "#f9fafb",
                    borderRadius: 6,
                    overflow: "auto",
                    fontSize: "0.8rem",
                    maxHeight: 400,
                  }}
                >
                  {r.generatedCode}
                </pre>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}

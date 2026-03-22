import type { QuestionConfig } from "../../types";

interface Props {
  questions: QuestionConfig[];
  selectedIds: Set<string>;
  caseCounts: Record<string, number>;
  defaultCount: number;
  onCountChanged: (id: string, count: number) => void;
}

export default function Step4SetCaseCount({
  questions,
  selectedIds,
  caseCounts,
  defaultCount,
  onCountChanged,
}: Props) {
  const selectedQuestions = questions.filter((q) =>
    selectedIds.has(q.questionId)
  );

  return (
    <div>
      <h2 style={{ marginTop: 0 }}>Cases Per Question</h2>
      <p style={{ color: "#6b7280" }}>
        Set the number of new test cases to generate for each question.
      </p>

      <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
        {selectedQuestions.map((q) => (
          <div
            key={q.questionId}
            style={{
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
              padding: "0.6rem 1rem",
              border: "1px solid #e5e7eb",
              borderRadius: 8,
            }}
          >
            <strong>{q.questionId}</strong>
            <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
              <input
                type="number"
                min={1}
                max={20}
                value={caseCounts[q.questionId] ?? defaultCount}
                onChange={(e) =>
                  onCountChanged(
                    q.questionId,
                    Math.max(1, parseInt(e.target.value) || 1)
                  )
                }
                style={{
                  width: 60,
                  padding: "0.3rem 0.5rem",
                  border: "1px solid #d1d5db",
                  borderRadius: 6,
                  textAlign: "center",
                }}
              />
              <span style={{ color: "#6b7280" }}>cases</span>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

import type { QuestionConfig } from "../../types";

interface Props {
  questions: QuestionConfig[];
  selected: Set<string>;
  onToggle: (id: string) => void;
  onSelectAll: () => void;
}

export default function Step3SelectQuestions({
  questions,
  selected,
  onToggle,
  onSelectAll,
}: Props) {
  return (
    <div>
      <h2 style={{ marginTop: 0 }}>Select Questions</h2>
      <p style={{ color: "#6b7280" }}>
        Choose which questions to generate test cases for.
      </p>

      <button
        onClick={onSelectAll}
        style={{
          marginBottom: "1rem",
          padding: "0.35rem 1rem",
          border: "1px solid #d1d5db",
          borderRadius: 6,
          background: "#fff",
          cursor: "pointer",
          fontSize: "0.85rem",
        }}
      >
        {selected.size === questions.length ? "Deselect All" : "Select All"}
      </button>

      <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
        {questions.map((q) => (
          <label
            key={q.questionId}
            style={{
              display: "flex",
              alignItems: "center",
              gap: 10,
              padding: "0.6rem 1rem",
              border: `1px solid ${selected.has(q.questionId) ? "#2563eb" : "#e5e7eb"}`,
              borderRadius: 8,
              background: selected.has(q.questionId) ? "#eff6ff" : "#fff",
              cursor: "pointer",
              transition: "all 0.15s",
            }}
          >
            <input
              type="checkbox"
              checked={selected.has(q.questionId)}
              onChange={() => onToggle(q.questionId)}
            />
            <div>
              <strong>{q.questionId}</strong>{" "}
              <span style={{ color: "#6b7280" }}>
                ({q.testerClassName}, max: {q.maxScore})
              </span>
            </div>
          </label>
        ))}
      </div>
    </div>
  );
}

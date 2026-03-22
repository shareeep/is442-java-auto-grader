import { type ReactNode } from "react";

interface StepWizardProps {
  currentStep: number;
  totalSteps: number;
  onBack: () => void;
  onNext: () => void;
  canGoNext: boolean;
  nextLabel?: string;
  hideNav?: boolean;
  children: ReactNode;
}

const stepLabels = [
  "Upload PDF",
  "Tester Folder",
  "Select Questions",
  "Case Count",
  "Generate",
  "Review",
  "Save",
];

export default function StepWizard({
  currentStep,
  totalSteps,
  onBack,
  onNext,
  canGoNext,
  nextLabel = "Next",
  hideNav = false,
  children,
}: StepWizardProps) {
  return (
    <div>
      {/* Progress bar */}
      <div
        style={{
          display: "flex",
          gap: 4,
          marginBottom: "1.5rem",
        }}
      >
        {Array.from({ length: totalSteps }, (_, i) => (
          <div
            key={i}
            style={{
              flex: 1,
              height: 6,
              borderRadius: 3,
              background: i < currentStep ? "#2563eb" : "#e5e7eb",
              transition: "background 0.2s",
            }}
          />
        ))}
      </div>

      {/* Step label */}
      <div
        style={{
          fontSize: "0.85rem",
          color: "#6b7280",
          marginBottom: "1rem",
        }}
      >
        Step {currentStep} of {totalSteps}:{" "}
        {stepLabels[currentStep - 1] ?? ""}
      </div>

      {/* Content */}
      <div style={{ minHeight: 200 }}>{children}</div>

      {/* Navigation */}
      {!hideNav && (
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            marginTop: "1.5rem",
          }}
        >
          <button
            onClick={onBack}
            disabled={currentStep <= 1}
            style={{
              padding: "0.5rem 1.5rem",
              border: "1px solid #d1d5db",
              borderRadius: 6,
              background: "#fff",
              cursor: currentStep <= 1 ? "not-allowed" : "pointer",
              opacity: currentStep <= 1 ? 0.5 : 1,
            }}
          >
            Back
          </button>
          <button
            onClick={onNext}
            disabled={!canGoNext}
            style={{
              padding: "0.5rem 1.5rem",
              border: "none",
              borderRadius: 6,
              background: canGoNext ? "#2563eb" : "#93c5fd",
              color: "#fff",
              cursor: canGoNext ? "pointer" : "not-allowed",
              fontWeight: 600,
            }}
          >
            {nextLabel}
          </button>
        </div>
      )}
    </div>
  );
}

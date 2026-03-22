import { TestGenerationWizard } from "./components/TestGenerationWizard";

export default function App() {
  return (
    <div
      style={{
        maxWidth: 800,
        margin: "0 auto",
        padding: "2rem 1rem",
        fontFamily:
          '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
      }}
    >
      <h1 style={{ textAlign: "center", marginBottom: "0.5rem" }}>
        IS442 AutoGrader
      </h1>
      <p
        style={{
          textAlign: "center",
          color: "#666",
          marginBottom: "2rem",
        }}
      >
        AI Test Case Generation Wizard
      </p>
      <TestGenerationWizard />
    </div>
  );
}

import { useState } from "react";

interface Props {
  testersDir: string | null;
  templateDir: string | null;
  onTestersChanged: (dir: string | null) => void;
  onTemplateChanged: (dir: string | null) => void;
}

export default function Step2TesterFolder({
  testersDir,
  templateDir,
  onTestersChanged,
  onTemplateChanged,
}: Props) {
  const [mode, setMode] = useState<"existing" | "none">(
    testersDir ? "existing" : "none"
  );
  const [testerPath, setTesterPath] = useState(
    testersDir ?? "is442-project-materials/Tester-Files"
  );
  const [templatePath, setTemplatePath] = useState(
    templateDir ?? "is442-project-materials/RenameToYourUsername"
  );
  const [useTemplate, setUseTemplate] = useState(!!templateDir);

  return (
    <div>
      <h2 style={{ marginTop: 0 }}>Tester Files & Original Code</h2>
      <p style={{ color: "#6b7280" }}>
        Configure the paths to existing tester files and original student code.
      </p>

      {/* Tester files section */}
      <h3 style={{ fontSize: "1rem", marginBottom: "0.5rem" }}>
        Existing Tester Files
      </h3>

      <label
        style={{
          display: "flex",
          alignItems: "flex-start",
          gap: 8,
          marginBottom: "1rem",
          cursor: "pointer",
        }}
      >
        <input
          type="radio"
          checked={mode === "existing"}
          onChange={() => {
            setMode("existing");
            onTestersChanged(testerPath);
          }}
          style={{ marginTop: 4 }}
        />
        <div>
          <div style={{ fontWeight: 500 }}>Use existing tester files</div>
          <input
            type="text"
            value={testerPath}
            onChange={(e) => {
              setTesterPath(e.target.value);
              if (mode === "existing") onTestersChanged(e.target.value);
            }}
            onClick={(e) => e.stopPropagation()}
            style={{
              marginTop: 4,
              width: "100%",
              padding: "0.4rem 0.6rem",
              border: "1px solid #d1d5db",
              borderRadius: 6,
              fontSize: "0.9rem",
              minWidth: 350,
            }}
            placeholder="Path to tester files folder"
          />
        </div>
      </label>

      <label
        style={{
          display: "flex",
          alignItems: "center",
          gap: 8,
          cursor: "pointer",
          marginBottom: "1.5rem",
        }}
      >
        <input
          type="radio"
          checked={mode === "none"}
          onChange={() => {
            setMode("none");
            onTestersChanged(null);
          }}
        />
        <div>
          <div style={{ fontWeight: 500 }}>No existing testers</div>
          <div style={{ color: "#6b7280", fontSize: "0.85rem" }}>
            Generate test cases from scratch
          </div>
        </div>
      </label>

      {/* Template dir section */}
      <div
        style={{
          borderTop: "1px solid #e5e7eb",
          paddingTop: "1rem",
        }}
      >
        <h3 style={{ fontSize: "1rem", marginBottom: "0.5rem" }}>
          Original Student Code (Template)
        </h3>
        <p style={{ color: "#6b7280", fontSize: "0.85rem", margin: "0 0 0.75rem" }}>
          Providing the original student code helps the AI understand the exact
          method signatures, data types, and logic (e.g. pi=3.14 vs Math.PI).
        </p>

        <label
          style={{
            display: "flex",
            alignItems: "flex-start",
            gap: 8,
            cursor: "pointer",
          }}
        >
          <input
            type="checkbox"
            checked={useTemplate}
            onChange={(e) => {
              setUseTemplate(e.target.checked);
              onTemplateChanged(e.target.checked ? templatePath : null);
            }}
            style={{ marginTop: 4 }}
          />
          <div>
            <div style={{ fontWeight: 500 }}>
              Include original student code as context
            </div>
            {useTemplate && (
              <input
                type="text"
                value={templatePath}
                onChange={(e) => {
                  setTemplatePath(e.target.value);
                  onTemplateChanged(e.target.value);
                }}
                onClick={(e) => e.stopPropagation()}
                style={{
                  marginTop: 4,
                  width: "100%",
                  padding: "0.4rem 0.6rem",
                  border: "1px solid #d1d5db",
                  borderRadius: 6,
                  fontSize: "0.9rem",
                  minWidth: 350,
                }}
                placeholder="Path to RenameToYourUsername folder"
              />
            )}
          </div>
        </label>
      </div>
    </div>
  );
}

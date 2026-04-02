package com.is442.autograder.generation;

import com.is442.autograder.model.GeneratedTestCase;
import com.is442.autograder.model.StructuredTestCase;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Properties;

/**
 * Writes the final generated tester Java file, merging original and generated
 * test cases. Backs up the original tester before writing.
 */
public class TesterFileWriter {
	private static final String JAVA_SUFFIX = ".java";
	private static final String TESTER_SUFFIX = "Tester";
	private static final String ARCHIVE_TIMESTAMP_FORMAT = "yyyyMMdd_HHmmss";
	private static final String WEIGHT_PLACEHOLDER_PATTERN = "WEIGHT_\\d+";
	private static final String DEFAULT_WEIGHT = "1.0";
	private static final String GRADER_METHOD_MARKER = "    public static void grade() {\n";

	/**
	 * Write a generated tester file to the output folder.
	 *
	 * @param testerClassName
	 *            e.g. "Q1aTester"
	 * @param originalCode
	 *            content of the original tester (may be null)
	 * @param generatedCode
	 *            raw code blocks from Claude
	 * @param cases
	 *            test cases with professor-assigned weights
	 * @param originalTesterDir
	 *            directory containing original tester file (for backup)
	 * @param outputDir
	 *            where to write the generated file
	 * @return path of the written file
	 */
	public Path write(String testerClassName, String originalCode, String generatedCode, List<GeneratedTestCase> cases,
			Path originalTesterDir, Path outputDir) throws IOException {

		String parentClass = testerClassName.replace(TESTER_SUFFIX, "");

		// Substitute WEIGHT_N placeholders with actual weights
		String codeWithWeights = substituteWeights(generatedCode, cases);

		String fileContent = buildFileContent(testerClassName, parentClass, originalCode, codeWithWeights);

		// Path 1: tester dir exists — archive original, overwrite in place
		// Path 2: no tester dir — write to outputDir
		final Path outputFile;
		if (originalTesterDir != null) {
			Path orig = originalTesterDir.resolve(testerClassName + JAVA_SUFFIX);
			if (Files.exists(orig)) {
				String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern(ARCHIVE_TIMESTAMP_FORMAT));
				Path archiveDir = originalTesterDir.resolve("archive").resolve(timestamp);
				Files.createDirectories(archiveDir);
				Files.copy(orig, archiveDir.resolve(testerClassName + JAVA_SUFFIX),
						StandardCopyOption.REPLACE_EXISTING);
			}
			outputFile = originalTesterDir.resolve(testerClassName + JAVA_SUFFIX);
		} else {
			Files.createDirectories(outputDir);
			outputFile = outputDir.resolve(testerClassName + JAVA_SUFFIX);
		}

		Files.writeString(outputFile, fileContent);
		return outputFile;
	}

	/**
	 * Build Java test case blocks from a list of structured test cases. The
	 * returned string contains the inner content of the grade() method — it does
	 * NOT include the class wrapper or the method signature. Each block is a
	 * standalone {@code { ... }} statement that follows the existing tester
	 * convention (tcNum++, score +=, printed pass/fail).
	 *
	 * @param cases
	 *            list of structured test cases
	 * @return Java source fragment ready to embed inside grade()
	 */
	public String buildCodeFromStructured(List<StructuredTestCase> cases) {
		StringBuilder sb = new StringBuilder();
		for (StructuredTestCase tc : cases) {
			sb.append("        {\n");
			// Comment header
			String concept = tc.conceptCovered() != null ? tc.conceptCovered() : "";
			String desc = tc.description() != null ? tc.description() : "";
			if (!concept.isBlank()) {
				sb.append("            // ").append(concept).append(": ").append(desc).append("\n");
			} else {
				sb.append("            // ").append(desc).append("\n");
			}
			sb.append("            System.out.println(\"Test \" + tcNum + \": ").append(desc.replace("\"", "'"))
					.append("\");\n");

			if (tc.expectsException()) {
				// Exception path
				String exType = tc.exceptionType() != null ? tc.exceptionType() : "Exception";
				sb.append("            try {\n");
				appendSetup(sb, tc.setup());
				sb.append("                ").append(tc.methodCall()).append(";\n");
				sb.append("                System.out.println(\"  => Expected ").append(exType)
						.append(" but none thrown — Failed\");\n");
				sb.append("            } catch (").append(exType).append(" e) {\n");
				sb.append("                System.out.println(\"  => Caught expected ").append(exType)
						.append(" — Passed\");\n");
				sb.append("                score += ").append(tc.weight()).append(";\n");
				sb.append("            } catch (Exception e) {\n");
				sb.append(
						"                System.out.println(\"  => Wrong exception: \" + e.getClass().getSimpleName() + \" — Failed\");\n");
				sb.append("            }\n");
			} else if (tc.assertion() == null || tc.assertion().isBlank()) {
				// Void method path (no assertion)
				sb.append("            try {\n");
				appendSetup(sb, tc.setup());
				sb.append("                ").append(tc.methodCall()).append(";\n");
				sb.append("                System.out.println(\"  => Passed\");\n");
				sb.append("                score += ").append(tc.weight()).append(";\n");
				sb.append("            } catch (Exception e) {\n");
				sb.append(
						"                System.out.println(\"  => Exception: \" + e.getMessage() + \" — Failed\");\n");
				sb.append("            }\n");
			} else {
				// Non-void path — compare result using LLM-provided assertion
				String expected = tc.expected() != null ? tc.expected().replace("\"", "'") : "";
				String assertion = tc.assertion();
				// Safety: if assertion references 'expected' but setup doesn't declare it,
				// rewrite assertion to use result.toString().equals("<expected>") as fallback
				boolean setupDeclaresExpected = tc.setup() != null && tc.setup().contains("expected");
				if (assertion.contains("expected") && !setupDeclaresExpected) {
					String rawExpected = tc.expected() != null ? tc.expected() : "";
					if (assertion.contains("expected ==")) {
						// Primitive comparison: expected == result → result == <value>
						assertion = "result == " + rawExpected;
					} else if (assertion.contains("expected.equals(result.toString())")) {
						assertion = "result.toString().equals(\"" + rawExpected.replace("\"", "\\\"") + "\")";
					} else {
						// Generic: expected.equals(result) → result.equals(<value>)
						assertion = "result.equals(" + rawExpected + ")";
					}
				}
				sb.append("            try {\n");
				appendSetup(sb, tc.setup());
				sb.append("                var result = ").append(tc.methodCall()).append(";\n");
				sb.append("                System.out.println(\"  Expected: ").append(expected).append("\");\n");
				sb.append("                System.out.println(\"  Actual:   \" + result);\n");
				sb.append("                if (").append(assertion).append(") {\n");
				sb.append("                    System.out.println(\"  => Passed\");\n");
				sb.append("                    score += ").append(tc.weight()).append(";\n");
				sb.append("                } else {\n");
				sb.append("                    System.out.println(\"  => Failed\");\n");
				sb.append("                }\n");
				sb.append("            } catch (Exception e) {\n");
				sb.append(
						"                System.out.println(\"  => Exception: \" + e.getMessage() + \" — Failed\");\n");
				sb.append("            }\n");
			}
			sb.append("            tcNum++;\n");
			sb.append("        }\n\n");
		}
		return sb.toString();
	}

	private void appendSetup(StringBuilder sb, String setup) {
		if (setup != null && !setup.isBlank()) {
			// Safety: skip if setup is English prose rather than Java code
			// (heuristic: no semicolons and > 60 chars means it's likely a description)
			if (!setup.contains(";") && setup.length() > 60) {
				sb.append("                // (setup omitted — non-Java content)\n");
				return;
			}
			for (String line : setup.split(";")) {
				String trimmed = line.trim();
				if (!trimmed.isBlank()) {
					sb.append("                ").append(trimmed).append(";\n");
				}
			}
		}
	}

	/**
	 * Update the max score for a question in config.properties.
	 *
	 * @param configPath
	 *            path to config.properties
	 * @param questionId
	 *            e.g. "Q1a"
	 * @param newMaxScore
	 *            new total max score
	 */
	public void updateConfigMaxScore(Path configPath, String questionId, double newMaxScore) throws IOException {
		if (!Files.exists(configPath)) {
			return;
		}

		Properties props = new Properties();
		try (InputStream is = Files.newInputStream(configPath)) {
			props.load(is);
		}

		String[] ids = props.getProperty("questions.list", "").split(",", -1);
		String[] scores = props.getProperty("questions.max.scores", "").split(",", -1);

		for (int i = 0; i < ids.length; i++) {
			if (ids[i].trim().equalsIgnoreCase(questionId)) {
				scores[i] = String.valueOf(newMaxScore);
				break;
			}
		}

		props.setProperty("questions.max.scores", String.join(",", scores));

		String content = Files.readString(configPath);
		String newScoresValue = String.join(",", scores);
		content = content.replaceFirst("(?m)^questions\\.max\\.scores=.*$", "questions.max.scores=" + newScoresValue);
		Files.writeString(configPath, content);
	}

	private String substituteWeights(String code, List<GeneratedTestCase> cases) {
		String result = code;
		for (int i = 0; i < cases.size(); i++) {
			result = result.replace("WEIGHT_" + (i + 1), String.valueOf(cases.get(i).weight()));
		}
		// Replace any remaining WEIGHT_N placeholders with 1.0
		result = result.replaceAll(WEIGHT_PLACEHOLDER_PATTERN, DEFAULT_WEIGHT);
		return result;
	}

	private String buildFileContent(String testerClassName, String parentClass, String originalCode,
			String generatedCode) {
		StringBuilder sb = new StringBuilder();
		sb.append("// Auto-generated by IS442 AutoGrader — review before use\n");
		sb.append("import java.util.*;\n\n");
		sb.append("public class ").append(testerClassName).append(" extends ").append(parentClass).append(" {\n\n");
		sb.append("    private static double score = 0;\n");
		sb.append("    private static String qn = \"").append(parentClass).append("\";\n\n");
		sb.append("    public static void main(String[] args) {\n");
		sb.append("        grade();\n");
		sb.append("        System.out.println(score);\n");
		sb.append("    }\n\n");
		sb.append("    public static void grade() {\n");

		if (originalCode != null && !originalCode.isBlank()) {
			// Extract grade() body from original — it already contains the banner
			// and int tcNum = 1; so we do NOT add them again here.
			String gradeBody = extractGradeBody(originalCode);
			if (!gradeBody.isBlank()) {
				sb.append("\n        // ── Original test cases (preserved) ─────────────────────────\n");
				sb.append(gradeBody);
				sb.append("\n");
			}
		} else {
			// From-scratch: add banner and tcNum declaration
			sb.append("        System.out.println(\"-------------------------------------------------------\");\n");
			sb.append(
					"        System.out.println(\"---------------------- \" + qn + \" ----------------------------\");\n");
			sb.append("        System.out.println(\"-------------------------------------------------------\");\n\n");
			sb.append("        int tcNum = 1;\n");
		}

		sb.append("\n        // ── Generated test cases ─────────────────────────────────────\n");
		// Indent generated blocks
		for (String line : generatedCode.split("\n")) {
			sb.append("        ").append(line).append("\n");
		}

		// Balance any unclosed braces from the generated code so the file always
		// compiles. We track depth starting after the grade() opening brace (depth=0
		// means we are at grade-body level).
		int depth = countBraceDepth(sb, GRADER_METHOD_MARKER);
		for (int i = 0; i < depth; i++) {
			sb.append("        }\n");
		}

		sb.append("    }\n"); // close grade()
		sb.append("}\n"); // close class
		return sb.toString();
	}

	/**
	 * Count net brace depth within a StringBuilder starting after a marker line.
	 * Returns the number of unclosed '{' that need matching '}'.
	 */
	private int countBraceDepth(StringBuilder sb, String startMarker) {
		int startIdx = sb.indexOf(startMarker);
		if (startIdx < 0) {
			return 0;
		}
		startIdx += startMarker.length();

		int depth = 0;
		boolean inString = false;
		boolean inLineComment = false;
		boolean inBlockComment = false;
		char prev = 0;

		for (int i = startIdx; i < sb.length(); i++) {
			char c = sb.charAt(i);

			if (inLineComment) {
				if (c == '\n') {
					inLineComment = false;
				}
			} else if (inBlockComment) {
				if (prev == '*' && c == '/') {
					inBlockComment = false;
				}
			} else if (inString) {
				if (c == '"' && prev != '\\') {
					inString = false;
				}
			} else {
				if (c == '/' && i + 1 < sb.length()) {
					char next = sb.charAt(i + 1);
					if (next == '/') {
						inLineComment = true;
					} else if (next == '*') {
						inBlockComment = true;
					}
				} else if (c == '"' && prev != '\\') {
					inString = true;
				} else if (c == '{') {
					depth++;
				} else if (c == '}') {
					depth--;
				}
			}
			prev = c;
		}
		return Math.max(depth, 0);
	}

	/**
	 * Extract the body of the grade() method from existing tester code (without the
	 * method signature and braces).
	 */
	private String extractGradeBody(String code) {
		int gradeStart = code.indexOf("public static void grade()");
		if (gradeStart < 0) {
			gradeStart = code.indexOf("static void grade()");
		}
		if (gradeStart < 0) {
			return "";
		}

		// Find opening brace
		int braceOpen = code.indexOf('{', gradeStart);
		if (braceOpen < 0) {
			return "";
		}

		// Find matching closing brace
		int depth = 1;
		int i = braceOpen + 1;
		while (i < code.length() && depth > 0) {
			char c = code.charAt(i);
			if (c == '{') {
				depth++;
			} else if (c == '}') {
				depth--;
			}
			i++;
		}

		// Extract content between the outer braces
		return code.substring(braceOpen + 1, i - 1);
	}
}

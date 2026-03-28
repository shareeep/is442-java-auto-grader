package com.is442.autograder.generation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.is442.autograder.model.GeneratedTestCase;
import com.is442.autograder.model.GenerationResult;
import com.is442.autograder.model.InferredQuestionConfig;
import com.is442.autograder.model.QuestionConfig;
import com.is442.autograder.model.StructuredTestCase;
import com.is442.autograder.model.TestCaseRecommendation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UI-agnostic orchestrator for AI-assisted test case generation. Coordinates
 * prompt building, LLM calls via LangChain4j, and deterministic code assembly.
 */
public class TestGenerationService {

	private static final Logger logger = LoggerFactory.getLogger(TestGenerationService.class);

	private final LangChainService langChainService;
	private final TesterFileWriter testerFileWriter;
	private final ObjectMapper objectMapper;

	public TestGenerationService(LangChainService langChainService, TesterFileWriter testerFileWriter) {
		this.langChainService = langChainService;
		this.testerFileWriter = testerFileWriter;
		this.objectMapper = new ObjectMapper();
	}

	/**
	 * Generate test cases for a single question.
	 *
	 * @param question
	 *            question configuration
	 * @param examContext
	 *            pre-loaded question markdown (from DB cache or PDF parser)
	 * @param existingTesterFile
	 *            path to existing tester file (null = from scratch)
	 * @param numCases
	 *            number of new test cases to generate
	 * @param templateDir
	 *            path to template directory with student code, or null
	 * @return generation result containing code and compile status
	 */
	public GenerationResult generateForQuestion(QuestionConfig question, String examContext, Path existingTesterFile,
			int numCases, Path templateDir) throws IOException, InterruptedException {

		logger.info("[GEN] Starting generation  questionId={} numCases={}", question.getQuestionId(), numCases);

		// 1. Read existing tester if provided
		String existingCode = null;
		if (existingTesterFile != null && Files.exists(existingTesterFile)) {
			existingCode = Files.readString(existingTesterFile);
			logger.debug("[GEN] Loaded existing tester  file={} chars={}", existingTesterFile.getFileName(),
					existingCode.length());
		} else {
			logger.debug("[GEN] No existing tester file — generating from scratch  questionId={}",
					question.getQuestionId());
		}

		// 2. Build additional context from template dir and data files
		String additionalContext = buildAdditionalContext(question, existingCode, existingTesterFile, templateDir);
		if (!additionalContext.isBlank()) {
			logger.debug("[GEN] Additional context built  questionId={} chars={}", question.getQuestionId(),
					additionalContext.length());
		}

		// 3. Build user prompt and call LLM
		String prompt = buildGeneratePrompt(question, examContext, existingCode, numCases, additionalContext);
		logger.info("[GEN] Calling AI  questionId={}", question.getQuestionId());

		String rawJson;
		try {
			rawJson = langChainService.generateTestCasesJson(prompt);
		} catch (Exception e) {
			logger.error("[GEN] AI call failed  questionId={}: {}", question.getQuestionId(), e.getMessage());
			return new GenerationResult(question.getQuestionId(), List.of(), false,
					"API call failed: " + e.getMessage(), "");
		}

		// 4. Parse structured test cases from JSON
		List<StructuredTestCase> structured = parseStructuredCases(rawJson, numCases);
		logger.info("[GEN] Parsed {} test cases  questionId={}", structured.size(), question.getQuestionId());

		// 5. Build deterministic Java code from structured cases
		String generatedCode = testerFileWriter.buildCodeFromStructured(structured);

		// 6. Convert to GeneratedTestCase for the API response
		List<GeneratedTestCase> cases = structured.stream()
				.map(tc -> new GeneratedTestCase(tc.description(),
						tc.setup() != null ? tc.setup() + "; " + tc.methodCall() : tc.methodCall(),
						tc.expected() != null ? tc.expected() : "", tc.weight()))
				.collect(Collectors.toList());

		logger.info("[GEN] Generation complete  questionId={} codeChars={}", question.getQuestionId(),
				generatedCode.length());
		return new GenerationResult(question.getQuestionId(), cases, true, "", generatedCode);
	}

	/**
	 * Generate test cases using an InferredQuestionConfig (from the Phase 2
	 * analyze-setup flow). Converts to QuestionConfig and delegates.
	 */
	public GenerationResult generateForInferredQuestion(InferredQuestionConfig iqc, String examContext,
			Path existingTesterFile, int numCases, Path templateDir) throws IOException, InterruptedException {
		QuestionConfig qc = new QuestionConfig(iqc.getQuestionId(),
				iqc.getFolder() != null ? iqc.getFolder() : iqc.getQuestionId(),
				iqc.getTester() != null ? iqc.getTester() : iqc.getQuestionId() + "Tester", iqc.getMaxScore(),
				iqc.getDependencyFolder(),
				iqc.getDependencyFiles() != null ? iqc.getDependencyFiles() : Collections.emptyList());
		return generateForQuestion(qc, examContext, existingTesterFile, numCases, templateDir);
	}

	/**
	 * Delegate to the AI for test case count and concept recommendations.
	 */
	public TestCaseRecommendation recommendTestCases(String questionId, String examContext,
			String existingTesterContent, int existingCaseCount) throws IOException, InterruptedException {

		String prompt = buildRecommendPrompt(questionId, examContext, existingTesterContent, existingCaseCount);
		String rawJson = langChainService.recommendJson(prompt);
		return parseRecommendation(rawJson, questionId, existingCaseCount);
	}

	/**
	 * Delegate to the AI for code refinement.
	 */
	public String refineCode(String questionId, String currentCode, String refinementPrompt, String examContext)
			throws IOException, InterruptedException {

		String prompt = buildRefinePrompt(questionId, currentCode, refinementPrompt, examContext);
		return langChainService.refineCode(prompt);
	}

	// ── Prompt builders ──────────────────────────────────────────────────────

	private String buildGeneratePrompt(QuestionConfig question, String examContext, String existingTesterCode,
			int numCases, String additionalContext) {
		StringBuilder sb = new StringBuilder();
		sb.append("Question ID: ").append(question.getQuestionId()).append("\n");
		sb.append("Tester class: ").append(question.getTesterClassName()).append("\n\n");
		sb.append("Exam question context:\n").append(examContext).append("\n\n");

		if (existingTesterCode != null && !existingTesterCode.isBlank()) {
			sb.append("Existing tester code (use the SAME method names, filenames, and patterns):\n");
			sb.append(existingTesterCode).append("\n\n");
		}

		if (additionalContext != null && !additionalContext.isBlank()) {
			sb.append(additionalContext).append("\n\n");
		}

		sb.append("Generate exactly ").append(numCases).append(" test cases. Return ONLY a valid JSON array with ")
				.append(numCases).append(" elements.");
		return sb.toString();
	}

	private String buildRecommendPrompt(String questionId, String examContext, String existingTesterContent,
			int existingCaseCount) {
		StringBuilder sb = new StringBuilder();
		sb.append("Question ID: ").append(questionId).append("\n\n");

		if (existingCaseCount > 0 && existingTesterContent != null) {
			sb.append("=== EXISTING TEST CASES (").append(existingCaseCount).append(") ===\n");
			sb.append(existingTesterContent).append("\n\n");
			sb.append("Analyze the above: what concepts/inputs does each test case cover?\n\n");
		}

		sb.append("=== EXAM QUESTION ===\n").append(examContext);
		return sb.toString();
	}

	private String buildRefinePrompt(String questionId, String currentCode, String refinementPrompt,
			String examContext) {
		StringBuilder sb = new StringBuilder();
		sb.append("Question ID: ").append(questionId).append("\n\n");

		if (examContext != null && !examContext.isBlank()) {
			sb.append("Exam context:\n").append(examContext).append("\n\n");
		}

		sb.append("Current generated code:\n").append(currentCode).append("\n\n");
		sb.append("Refinement request: ").append(refinementPrompt).append("\n\n");
		sb.append("Return the COMPLETE updated Java code with the refinement applied.");
		return sb.toString();
	}

	// ── JSON parsing ─────────────────────────────────────────────────────────

	@SuppressWarnings("unchecked")
	private List<StructuredTestCase> parseStructuredCases(String json, int numCases) {
		try {
			String cleaned = stripMarkdownFences(json);
			List<Map<String, Object>> raw = objectMapper.readValue(cleaned, new TypeReference<>() {
			});
			List<StructuredTestCase> cases = new ArrayList<>();
			for (Map<String, Object> m : raw) {
				cases.add(new StructuredTestCase(str(m, "description"), str(m, "conceptCovered"), str(m, "setup"),
						str(m, "methodCall"), str(m, "expected"), str(m, "assertion"),
						m.get("weight") instanceof Number n ? n.doubleValue() : 1.0,
						Boolean.TRUE.equals(m.get("expectsException")), str(m, "exceptionType")));
			}
			return cases;
		} catch (Exception e) {
			logger.warn("[AI] JSON parse failed, returning placeholder test cases: {}", e.getMessage());
			List<StructuredTestCase> fallback = new ArrayList<>();
			for (int i = 1; i <= numCases; i++) {
				fallback.add(new StructuredTestCase("Generated test " + i, "Uncategorised", null, "/* TODO: fill in */",
						"/* TODO */", null, 1.0, false, null));
			}
			return fallback;
		}
	}

	@SuppressWarnings("unchecked")
	private TestCaseRecommendation parseRecommendation(String json, String questionId, int existingCaseCount) {
		try {
			String cleaned = stripMarkdownFences(json);
			Map<String, Object> m = objectMapper.readValue(cleaned, new TypeReference<>() {
			});
			int count = m.get("recommendedCount") instanceof Number n ? n.intValue() : 3;
			List<String> concepts = m.get("conceptsToCover") instanceof List<?> l ? (List<String>) l : List.of();
			List<String> existingConcepts = m.get("existingConcepts") instanceof List<?> l
					? (List<String>) l
					: List.of();
			String rationale = m.get("rationale") instanceof String s ? s : "";

			return new TestCaseRecommendation(questionId, count, concepts, existingCaseCount, existingConcepts,
					rationale);
		} catch (Exception e) {
			logger.warn("[AI] Recommendation parse failed, using defaults  questionId={}: {}", questionId,
					e.getMessage());
			return new TestCaseRecommendation(questionId, 3,
					List.of("Normal: valid input", "Boundary: edge case", "Exception: error handling"));
		}
	}

	private static String str(Map<String, Object> m, String key) {
		Object v = m.get(key);
		return v instanceof String s ? s : null;
	}

	private static String stripMarkdownFences(String text) {
		String trimmed = text.strip();
		if (trimmed.startsWith("```")) {
			int firstNewline = trimmed.indexOf('\n');
			if (firstNewline > 0) {
				trimmed = trimmed.substring(firstNewline + 1);
			}
		}
		if (trimmed.endsWith("```")) {
			trimmed = trimmed.substring(0, trimmed.length() - 3).stripTrailing();
		}
		return trimmed;
	}

	// ── Additional context builder ───────────────────────────────────────────

	/**
	 * Build additional context for the AI prompt by reading: 1. Original student
	 * code from the template directory 2. Data files (.txt) referenced in the
	 * tester code 3. Data files from the question's subfolder in the template dir
	 */
	private String buildAdditionalContext(QuestionConfig question, String existingCode, Path existingTesterFile,
			Path templateDir) {
		StringBuilder ctx = new StringBuilder();

		// Read original student code from template dir
		if (templateDir != null) {
			Path questionFolder = templateDir.resolve(question.getFolder());
			if (Files.isDirectory(questionFolder)) {
				ctx.append("Original student code (the method students must implement):\n\n");
				try (var files = Files.list(questionFolder)) {
					for (Path f : files.toList()) {
						String name = f.getFileName().toString();
						if (name.endsWith(".java")) {
							ctx.append("--- ").append(name).append(" ---\n");
							ctx.append(Files.readString(f)).append("\n\n");
						}
					}
				} catch (IOException e) {
					// skip
				}

				// Read .txt data files from the question folder
				try (var files = Files.list(questionFolder)) {
					List<Path> dataFiles = files.filter(f -> f.getFileName().toString().endsWith(".txt")).toList();
					if (!dataFiles.isEmpty()) {
						ctx.append("Data files available in the question folder:\n\n");
						for (Path f : dataFiles) {
							ctx.append("File: \"").append(f.getFileName()).append("\" — contents:\n");
							ctx.append(Files.readString(f)).append("\n\n");
						}
					}
				} catch (IOException e) {
					// skip
				}
			}
		}

		// Extract referenced .txt filenames from the existing tester code
		if (existingCode != null) {
			Set<String> filenames = new LinkedHashSet<>();
			Pattern p = Pattern.compile("\"([^\"]+\\.txt)\"");
			Matcher m = p.matcher(existingCode);
			while (m.find()) {
				filenames.add(m.group(1));
			}

			if (!filenames.isEmpty()) {
				ctx.append("IMPORTANT — Data files referenced by the existing tester:\n");
				ctx.append("You may ONLY use these filenames. Do NOT invent new filenames.\n\n");

				// Try to find data files from multiple locations
				List<Path> searchDirs = new ArrayList<>();
				if (existingTesterFile != null && existingTesterFile.getParent() != null) {
					searchDirs.add(existingTesterFile.getParent());
				}
				if (templateDir != null) {
					searchDirs.add(templateDir.resolve(question.getFolder()));
				}

				for (String filename : filenames) {
					ctx.append("File: \"").append(filename).append("\"");
					boolean found = false;
					for (Path dir : searchDirs) {
						Path dataFile = dir.resolve(filename);
						if (Files.exists(dataFile)) {
							try {
								ctx.append(" — contents:\n").append(Files.readString(dataFile)).append("\n");
								found = true;
								break;
							} catch (IOException e) {
								// continue searching
							}
						}
					}
					if (!found) {
						ctx.append(" — file does NOT exist (used for error/exception testing)\n");
					}
					ctx.append("\n");
				}
			}
		}

		return ctx.toString();
	}
}

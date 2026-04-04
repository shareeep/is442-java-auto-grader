package com.is442.autograder.generation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.is442.autograder.model.GeneratedTestCase;
import com.is442.autograder.model.GenerationResult;
import com.is442.autograder.model.QuestionConfig;
import com.is442.autograder.model.StructuredTestCase;
import com.is442.autograder.model.TestCaseRecommendation;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
 * Retries AI calls up to MAX_AI_ATTEMPTS times on failure/null/parse errors.
 */
public class TestGenerationService {

	private static final Logger logger = LoggerFactory.getLogger(TestGenerationService.class);
	private static final int MAX_AI_ATTEMPTS = 3;
	private static final int DEFAULT_RECOMMENDED_COUNT = 3;
	private static final double DEFAULT_WEIGHT = 1.0;
	private static final Pattern TXT_FILENAME_PATTERN = Pattern.compile("\"([^\"]+\\.txt)\"");

	private final LangChainService langChainServiceVision;
	private final LangChainService langChainServiceText;
	private final TesterFileWriter testerFileWriter;
	private final ObjectMapper objectMapper;

	public TestGenerationService(LangChainService langChainServiceVision, LangChainService langChainServiceText,
			TesterFileWriter testerFileWriter) {
		this.langChainServiceVision = langChainServiceVision;
		this.langChainServiceText = langChainServiceText;
		this.testerFileWriter = testerFileWriter;
		this.objectMapper = new ObjectMapper();
	}

	// ── Public API ───────────────────────────────────────────────────────────

	/**
	 * Generate test cases for a single question with retry.
	 */
	public GenerationResult generateTestCases(QuestionConfig question, String examContext, Path existingTesterFile,
			int numCases, Path templateDir, List<String> conceptsToCover, List<String> customSuggestions)
			throws IOException, InterruptedException {

		String qid = question.getQuestionId();
		logger.info("[GEN] Starting generation  questionId={} numCases={} conceptsToCover={} customSuggestions={}", qid, numCases, conceptsToCover, customSuggestions);

		// Build context + prompt once (not per retry)
		String existingCode = loadExistingTesterCode(question, existingTesterFile);
		String additionalContext = buildAdditionalContext(question, existingCode, existingTesterFile, templateDir);
		String apiDocsContext = templateDir != null
				? buildApiDocsContext(templateDir.resolve(question.getFolder()), existingCode)
				: "";
		logger.info("[GEN] Context sizes  questionId={}  additionalContext={}chars  apiDocsContext={}chars",
				qid, additionalContext.length(), apiDocsContext.length());
		if (!apiDocsContext.isBlank()) {
			logger.info("[GEN] API docs included  questionId={}", qid);
		} else {
			logger.warn("[GEN] No API docs found  questionId={}  templateDir={}  folder={}", qid, templateDir, question.getFolder());
		}

		String prompt = buildGeneratePrompt(question, examContext, existingCode, numCases, additionalContext,
				apiDocsContext, conceptsToCover, customSuggestions);
		logger.info("[GEN] Final prompt size  questionId={}  chars={}", qid, prompt.length());

		List<String> imageUris = PdfParser.extractBase64Images(examContext);
		LangChainService svc = selectService(imageUris);
		UserMessage message = buildVisionMessage(prompt, examContext, imageUris);
		logAiCall("GEN", qid, prompt, imageUris);

		// Retry loop: call AI → null guard → parse → empty guard
		for (int attempt = 1; attempt <= MAX_AI_ATTEMPTS; attempt++) {
			if (attempt > 1) sleepBeforeRetry("GEN", qid, attempt - 1);
			String rawJson;
			try {
				rawJson = svc.generateTestCasesJson(message);
				logger.info("[GEN] AI response  questionId={} attempt={}\n{}", qid, attempt, rawJson);
			} catch (Exception e) {
				logger.error("[GEN] AI call failed  questionId={} attempt={}/{}: {}", qid, attempt, MAX_AI_ATTEMPTS,
						e.getMessage());
				if (attempt == MAX_AI_ATTEMPTS)
					return failResult(qid, "AI call failed after " + MAX_AI_ATTEMPTS + " attempts: " + e.getMessage());
				continue;
			}

			if (rawJson == null || rawJson.isBlank()) {
				logger.warn("[GEN] AI returned null/blank  questionId={} attempt={}/{}", qid, attempt, MAX_AI_ATTEMPTS);
				if (attempt == MAX_AI_ATTEMPTS)
					return failResult(qid, "AI returned empty response after " + MAX_AI_ATTEMPTS + " attempts");
				continue;
			}

			List<StructuredTestCase> structured;
			try {
				structured = parseStructuredCases(rawJson);
			} catch (Exception e) {
				logger.warn("[GEN] Parse failed  questionId={} attempt={}/{}: {}", qid, attempt, MAX_AI_ATTEMPTS,
						e.getMessage());
				if (attempt == MAX_AI_ATTEMPTS)
					return failResult(qid, "AI returned unparseable output after " + MAX_AI_ATTEMPTS + " attempts");
				continue;
			}

			if (structured.isEmpty()) {
				logger.warn("[GEN] AI returned 0 cases  questionId={} attempt={}/{}", qid, attempt, MAX_AI_ATTEMPTS);
				if (attempt == MAX_AI_ATTEMPTS)
					return failResult(qid, "AI returned no test cases after " + MAX_AI_ATTEMPTS + " attempts");
				continue;
			}

			// Success
			String generatedCode = testerFileWriter.buildCodeFromStructured(structured);
			List<GeneratedTestCase> cases = structured.stream().map(this::toGeneratedTestCase)
					.collect(Collectors.toList());
			logger.info("[GEN] Generation complete  questionId={} cases={} codeChars={}", qid, cases.size(),
					generatedCode.length());
			return new GenerationResult(qid, cases, true, "", generatedCode);
		}

		return failResult(qid, "Generation failed"); // unreachable
	}

	/**
	 * Ask the AI to recommend test count and concepts for a question with retry.
	 */
	public TestCaseRecommendation recommendTestCases(String questionId, String examContext,
			String existingTesterContent, int existingCaseCount) throws IOException, InterruptedException {

		String prompt = buildRecommendPrompt(questionId, examContext, existingTesterContent, existingCaseCount);

		List<String> imageUris = PdfParser.extractBase64Images(examContext);
		LangChainService svc = selectService(imageUris);
		UserMessage message = buildVisionMessage(prompt, examContext, imageUris);
		logAiCall("GEN][RECOMMEND", questionId, prompt, imageUris);

		for (int attempt = 1; attempt <= MAX_AI_ATTEMPTS; attempt++) {
			if (attempt > 1) sleepBeforeRetry("GEN][RECOMMEND", questionId, attempt - 1);
			String rawJson;
			try {
				rawJson = svc.recommendJson(message);
				logger.info("[GEN][RECOMMEND] AI response  questionId={} attempt={}\n{}", questionId, attempt, rawJson);
			} catch (Exception e) {
				logger.error("[GEN][RECOMMEND] AI call failed  questionId={} attempt={}/{}: {}", questionId, attempt,
						MAX_AI_ATTEMPTS, e.getMessage());
				if (attempt == MAX_AI_ATTEMPTS)
					throw new RuntimeException(
							"Recommendation failed after " + MAX_AI_ATTEMPTS + " attempts: " + e.getMessage(), e);
				continue;
			}

			if (rawJson == null || rawJson.isBlank()) {
				logger.warn("[GEN][RECOMMEND] AI returned null/blank  questionId={} attempt={}/{}", questionId, attempt,
						MAX_AI_ATTEMPTS);
				if (attempt == MAX_AI_ATTEMPTS)
					throw new RuntimeException(
							"AI returned empty recommendation after " + MAX_AI_ATTEMPTS + " attempts");
				continue;
			}

			try {
				return parseRecommendation(rawJson, questionId, existingCaseCount);
			} catch (Exception e) {
				logger.warn("[GEN][RECOMMEND] Parse failed  questionId={} attempt={}/{}: {}", questionId, attempt,
						MAX_AI_ATTEMPTS, e.getMessage());
				if (attempt == MAX_AI_ATTEMPTS)
					throw new RuntimeException(
							"Recommendation parse failed after " + MAX_AI_ATTEMPTS + " attempts: " + e.getMessage(), e);
			}
		}

		throw new RuntimeException("Recommendation failed for " + questionId); // unreachable
	}

	/**
	 * Delegate to the AI for code refinement (no retry — single-shot).
	 */
	public String refineCode(String questionId, String currentCode, String refinementPrompt, String examContext)
			throws IOException, InterruptedException {

		String prompt = buildRefinePrompt(questionId, currentCode, refinementPrompt, examContext);
		logger.info("[GEN][REFINE] === PROMPT START ===  questionId={}\n{}\n[GEN][REFINE] === PROMPT END ===",
				questionId, PdfParser.stripInlineImages(prompt));

		String result = langChainServiceText.refineCode(prompt);
		logger.info("[GEN][REFINE] === AI RESPONSE START ===  questionId={}\n{}\n[GEN][REFINE] === AI RESPONSE END ===",
				questionId, result);

		return result;
	}

	// ── Helpers ──────────────────────────────────────────────────────────────

	private void sleepBeforeRetry(String tag, String questionId, int attempt) {
		long delayMs = 3000L * attempt;
		logger.info("[{}] Waiting {}ms before retry  questionId={} attempt={}", tag, delayMs, questionId, attempt);
		try {
			Thread.sleep(delayMs);
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
		}
	}

	private LangChainService selectService(List<String> imageUris) {
		return imageUris.isEmpty() ? langChainServiceText : langChainServiceVision;
	}

	private GenerationResult failResult(String questionId, String message) {
		return new GenerationResult(questionId, List.of(), false, message, "");
	}

	private void logAiCall(String tag, String questionId, String prompt, List<String> imageUris) {
		logger.info("[{}] Calling AI  questionId={}", tag, questionId);
		logger.info("[{}] === PROMPT START ===  questionId={}\n{}\n[{}] === PROMPT END ===", tag, questionId,
				PdfParser.stripInlineImages(prompt), tag);
		String imageLog = imageUris.isEmpty()
				? "none"
				: imageUris.stream().map(uri -> uri.length() > 50 ? uri.substring(0, 50) + "..." : uri)
						.collect(Collectors.joining(", "));
		logger.info("[{}] Using {} model  questionId={} images={} ({})", tag, imageUris.isEmpty() ? "text" : "vision",
				questionId, imageUris.size(), imageLog);
	}

	// ── Vision message builder ───────────────────────────────────────────────

	private UserMessage buildVisionMessage(String textPrompt, String examContext, List<String> imageUris) {
		List<Content> contents = new ArrayList<>();
		if (imageUris.isEmpty()) {
			contents.add(TextContent.from(textPrompt));
		} else {
			String strippedContext = PdfParser.stripInlineImages(examContext);
			String textWithStripped = textPrompt.replace(examContext, strippedContext);
			contents.add(TextContent.from(textWithStripped));
			for (String dataUri : imageUris) {
				contents.add(ImageContent.from(dataUri));
			}
		}
		return UserMessage.from(contents);
	}

	// ── Prompt builders ──────────────────────────────────────────────────────

	private String buildGeneratePrompt(QuestionConfig question, String examContext, String existingTesterCode,
			int numCases, String additionalContext, String apiDocsContext, List<String> conceptsToCover,
			List<String> customSuggestions) {
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

		if (apiDocsContext != null && !apiDocsContext.isBlank()) {
			sb.append(apiDocsContext).append("\n\n");
		}

		if (conceptsToCover != null && !conceptsToCover.isEmpty()) {
			sb.append("Key concepts to cover in the new test cases:\n");
			conceptsToCover.forEach(c -> sb.append("  - ").append(c).append("\n"));
			sb.append("\n");
		}

		if (customSuggestions != null && !customSuggestions.isEmpty()) {
			sb.append("Professor's additional test instructions:\n");
			for (int i = 0; i < customSuggestions.size(); i++) {
				sb.append("  Custom #").append(i + 1).append(": ").append(customSuggestions.get(i)).append("\n");
			}
			sb.append("\n");
		}

		sb.append("Generate exactly ").append(numCases).append(" test cases.\n");
		sb.append(
				"IMPORTANT: Every 'description' must be a specific meaningful name — never 'Generated Test Case N' or any numbered placeholder.\n");
		sb.append(
				"REMINDER: 'setup' must be VALID JAVA CODE ONLY (no English prose). 'assertion' must be self-contained using only the variable 'result' — do NOT reference a variable named 'expected' (it does not exist).\n");
		sb.append("Return ONLY a valid JSON array with ").append(numCases).append(" elements.");
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

		sb.append("=== EXAM QUESTION ===\n").append(examContext).append("\n\n");
		sb.append("Identify the most important test concepts and edge cases for THIS SPECIFIC sub-question only. ")
				.append("Return at most 5 concepts — pick the highest-value ones. ")
				.append("Do NOT include concepts from other sub-questions.");
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
	private List<StructuredTestCase> parseStructuredCases(String json) {
		try {
			String cleaned = stripMarkdownFences(json);
			List<Map<String, Object>> raw = objectMapper.readValue(cleaned, new TypeReference<>() {
			});
			List<StructuredTestCase> cases = new ArrayList<>();
			for (Map<String, Object> m : raw) {
				cases.add(new StructuredTestCase(str(m, "description"), str(m, "conceptCovered"), str(m, "setup"),
						str(m, "methodCall"), str(m, "expected"), str(m, "assertion"),
						m.get("weight") instanceof Number n ? n.doubleValue() : DEFAULT_WEIGHT,
						Boolean.TRUE.equals(m.get("expectsException")), str(m, "exceptionType")));
			}
			return cases;
		} catch (Exception e) {
			logger.error("[AI] JSON parse failed for test case generation  raw={}", json, e);
			throw new RuntimeException("AI returned unparseable output: " + e.getMessage(), e);
		}
	}

	@SuppressWarnings("unchecked")
	private TestCaseRecommendation parseRecommendation(String json, String questionId, int existingCaseCount) {
		try {
			String cleaned = stripMarkdownFences(json);
			Map<String, Object> m = objectMapper.readValue(cleaned, new TypeReference<>() {
			});
			List<String> concepts = m.get("conceptsToCover") instanceof List<?> l ? (List<String>) l : List.of();
			concepts = concepts.size() > 5 ? concepts.subList(0, 5) : concepts;
			int count = !concepts.isEmpty()
					? concepts.size()
					: m.get("recommendedCount") instanceof Number n
							? Math.min(5, n.intValue())
							: DEFAULT_RECOMMENDED_COUNT;
			List<String> existingConcepts = m.get("existingConcepts") instanceof List<?> l
					? (List<String>) l
					: List.of();
			String rationale = m.get("rationale") instanceof String s ? s : "";

			return new TestCaseRecommendation(questionId, count, concepts, existingCaseCount, existingConcepts,
					rationale);
		} catch (Exception e) {
			logger.error("[AI] Recommendation parse failed  questionId={}  raw={}", questionId, json, e);
			throw new RuntimeException("AI recommendation failed for " + questionId + ": " + e.getMessage(), e);
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

	private String buildAdditionalContext(QuestionConfig question, String existingCode, Path existingTesterFile,
			Path templateDir) {
		StringBuilder ctx = new StringBuilder();

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

		if (existingCode != null) {
			Set<String> filenames = new LinkedHashSet<>();
			Matcher m = TXT_FILENAME_PATTERN.matcher(existingCode);
			while (m.find()) {
				filenames.add(m.group(1));
			}

			if (!filenames.isEmpty()) {
				ctx.append("IMPORTANT — Data files referenced by the existing tester:\n");
				ctx.append("You may ONLY use these filenames. Do NOT invent new filenames.\n\n");

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

	private String buildApiDocsContext(Path questionFolder, String existingCode) {
		if (questionFolder == null || existingCode == null) {
			return "";
		}

		Path apiFolder = questionFolder.resolve("api");
		if (!Files.isDirectory(apiFolder)) {
			return "";
		}

		Set<String> classes = extractClasses(existingCode);
		if (classes.isEmpty()) {
			return "";
		}

		StringBuilder ctx = new StringBuilder();
		ctx.append(
				"API Documentation (available classes and their methods — DO NOT invent methods not listed here):\n\n");

		try (var files = Files.list(apiFolder)) {
			for (Path f : files.filter(p -> p.toString().endsWith(".html")).toList()) {
				String filename = f.getFileName().toString();
				String className = filename.replace(".html", "");

				if (classes.contains(className)) {
					ctx.append("--- ").append(filename).append(" ---\n");
					ctx.append(Files.readString(f)).append("\n\n");
				}
			}
		} catch (IOException e) {
			logger.debug("[GEN] Could not read api folder: {}", apiFolder);
		}

		return ctx.toString();
	}

	private Set<String> extractClasses(String code) {
		Set<String> classes = new LinkedHashSet<>();
		if (code == null || code.isBlank()) {
			return classes;
		}

		Pattern importP = Pattern.compile("import\\s+[^;]*\\.(\\w+);");
		for (Matcher m = importP.matcher(code); m.find();) {
			String c = m.group(1);
			if (!c.equals("util") && !c.equals("lang") && !c.equals("io")) {
				classes.add(c);
			}
		}

		Pattern newP = Pattern.compile("new\\s+(\\w+)\\s*\\(");
		for (Matcher m = newP.matcher(code); m.find();) {
			classes.add(m.group(1));
		}

		Pattern genP = Pattern.compile("<(\\w+)>");
		for (Matcher m = genP.matcher(code); m.find();) {
			classes.add(m.group(1));
		}

		return classes;
	}

	private String loadExistingTesterCode(QuestionConfig question, Path existingTesterFile) throws IOException {
		if (existingTesterFile != null && Files.exists(existingTesterFile)) {
			String existingCode = Files.readString(existingTesterFile);
			logger.debug("[GEN] Loaded existing tester  file={} chars={}", existingTesterFile.getFileName(),
					existingCode.length());
			return existingCode;
		}
		logger.debug("[GEN] No existing tester file — generating from scratch  questionId={}",
				question.getQuestionId());
		return null;
	}

	private GeneratedTestCase toGeneratedTestCase(StructuredTestCase testCase) {
		String inputArgs = testCase.setup() != null
				? testCase.setup() + "; " + testCase.methodCall()
				: testCase.methodCall();
		String expectedOutput = testCase.expected() != null ? testCase.expected() : "";
		return new GeneratedTestCase(testCase.description(), inputArgs, expectedOutput, testCase.weight());
	}
}

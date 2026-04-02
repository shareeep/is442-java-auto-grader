package com.is442.autograder.web;

import com.is442.autograder.config.AppConfig;
import com.is442.autograder.data.SessionDatabase;
import com.is442.autograder.generation.ConfigInferenceService;
import com.is442.autograder.generation.PdfParser;
import com.is442.autograder.generation.TestGenerationService;
import com.is442.autograder.generation.TesterFileWriter;
import com.is442.autograder.model.GeneratedTestCase;
import com.is442.autograder.model.GenerationResult;
import com.is442.autograder.model.InferredConfig;
import com.is442.autograder.model.InferredQuestionConfig;
import com.is442.autograder.model.QuestionConfig;
import com.is442.autograder.model.TestCaseRecommendation;
import com.is442.autograder.web.dto.AnalyzeSetupRequest;
import com.is442.autograder.web.dto.ExecuteRequest;
import com.is442.autograder.web.dto.GenerateQuestionRequest;
import com.is442.autograder.web.dto.GenerateRequest;
import com.is442.autograder.web.dto.PreparsePdfRequest;
import com.is442.autograder.web.dto.RecommendRequest;
import com.is442.autograder.web.dto.RefineRequest;
import com.is442.autograder.web.dto.QuestionSelection;
import com.is442.autograder.web.dto.SaveRequest;
import com.is442.autograder.web.dto.SaveResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates test case generation and saving via the existing service layer.
 * All directory references use upload IDs resolved from ExamController maps.
 * Uses SQLite cache for parsed PDF content — AI generation never re-parses.
 */
@RestController
@RequestMapping("/api/generation")
public class GenerationController {

	private static final Logger logger = LoggerFactory.getLogger(GenerationController.class);

	private final AppConfig appConfig;
	private final TestGenerationService generationService;
	private final TesterFileWriter testerFileWriter;
	private final ConfigInferenceService configInferenceService;
	private final SessionDatabase sessionDb;
	private final PdfParser pdfParser;

	public GenerationController(AppConfig appConfig, TestGenerationService generationService,
			TesterFileWriter testerFileWriter, ConfigInferenceService configInferenceService, SessionDatabase sessionDb,
			PdfParser pdfParser) {
		this.appConfig = appConfig;
		this.generationService = generationService;
		this.testerFileWriter = testerFileWriter;
		this.configInferenceService = configInferenceService;
		this.sessionDb = sessionDb;
		this.pdfParser = pdfParser;
	}

	// ── Helpers: resolve upload IDs to server-side paths ──────────────────────

	private Path resolveTemplateDir(String templateId) {
		if (templateId == null)
			return null;
		return ExamController.TEMPLATE_DIRS.get(templateId);
	}

	private Path resolveTestersDir(String testerId) {
		if (testerId == null)
			return null;
		return ExamController.TESTER_DIRS.get(testerId);
	}

	/**
	 * Load exam context for a question: try DB cache first, fallback to PDF parser.
	 * Saves to DB on cache miss for future use.
	 */
	private String loadExamContext(String examId, String questionId) {
		SessionDatabase.QuestionEntry qEntry = sessionDb.getQuestion(examId, questionId);
		if (qEntry != null && qEntry.markdown() != null) {
			logger.info("[CONTROLLER] Using cached question markdown for {}/{}", examId, questionId);
			return qEntry.markdown();
		}

		// Cache miss — parse from PDF and save
		Path examPdf = ExamController.EXAM_FILES.get(examId);
		if (examPdf == null || !Files.exists(examPdf)) {
			return null;
		}
		try {
			String markdown = pdfParser.extractQuestionSection(examPdf, questionId);
			sessionDb.saveQuestion(examId, questionId, markdown, questionId, questionId, 0, false);
			logger.info("[CONTROLLER] Parsed and cached question markdown for {}/{}", examId, questionId);
			return markdown;
		} catch (IOException e) {
			logger.error("[CONTROLLER] Failed to parse PDF for {}/{}: {}", examId, questionId, e.getMessage());
			return null;
		}
	}

	// ── Endpoints ────────────────────────────────────────────────────────────

	/**
	 * Analyze the uploaded exam PDF alongside the uploaded template and tester
	 * directories to produce an InferredConfig.
	 */
	@PostMapping("/analyze-setup")
	public ResponseEntity<?> analyzeSetup(@RequestBody AnalyzeSetupRequest request) {
		String examId = request.getExamId();
		String templateId = request.getTemplateId();
		String testerId = request.getTesterId();

		Path examPdf = ExamController.EXAM_FILES.get(examId);
		if (examPdf == null || !Files.exists(examPdf)) {
			return ResponseEntity.badRequest().body(Map.of("error", "Exam not found. Please upload first."));
		}

		Path templateDir = resolveTemplateDir(templateId);
		Path testersDir = resolveTestersDir(testerId);

		try {
			String markdown;
			String existingMarkdown = sessionDb.getParsedMarkdown(examId);
			if (existingMarkdown != null) {
				logger.info("[CONTROLLER] Using cached markdown for exam {}", examId);
				markdown = existingMarkdown;
			} else {
				markdown = pdfParser.extractAllText(examPdf);
				sessionDb.saveParsedExam(examId, examPdf.getFileName().toString(), markdown);
			}

			InferredConfig config = configInferenceService.inferConfig(markdown,
					templateDir != null ? templateDir.toString() : null,
					testersDir != null ? testersDir.toString() : null);

			for (InferredQuestionConfig qc : config.getQuestions()) {
				String qMarkdown = pdfParser.extractQuestionSectionFromMarkdown(markdown, qc.getQuestionId());
				sessionDb.saveQuestion(examId, qc.getQuestionId(), qMarkdown, qc.getFolder(), qc.getTester(),
						qc.getMaxScore(), qc.isInferredFromPdf());
			}

			return ResponseEntity.ok(config);
		} catch (Exception e) {
			return ResponseEntity.internalServerError().body(Map.of("error", "Analysis failed: " + e.getMessage()));
		}
	}

	/**
	 * Pre-parse PDF in background after upload (non-blocking).
	 */
	@PostMapping("/preparse-pdf")
	public ResponseEntity<?> preparsePdf(@RequestBody PreparsePdfRequest request) {
		String examId = request.getExamId();

		Path examPdf = ExamController.EXAM_FILES.get(examId);
		if (examPdf == null || !Files.exists(examPdf)) {
			return ResponseEntity.badRequest().body(Map.of("error", "Exam not found."));
		}

		try {
			String existingMarkdown = sessionDb.getParsedMarkdown(examId);
			if (existingMarkdown != null) {
				logger.info("[CONTROLLER] PDF already parsed for exam {}", examId);
				return ResponseEntity.ok(Map.of("status", "already_cached"));
			}

			logger.info("[CONTROLLER] Starting background PDF parse for exam {}", examId);
			String markdown = pdfParser.extractAllText(examPdf);
			sessionDb.saveParsedExam(examId, examPdf.getFileName().toString(), markdown);
			logger.info("[CONTROLLER] PDF parsed and cached for exam {}", examId);
			return ResponseEntity.ok(Map.of("status", "parsed"));
		} catch (Exception e) {
			logger.error("[CONTROLLER] PDF parse failed: {}", e.getMessage());
			return ResponseEntity.ok(Map.of("status", "error", "message", e.getMessage()));
		}
	}

	/**
	 * Persist the confirmed InferredConfig to config.properties.
	 */
	@PostMapping("/save-setup")
	public ResponseEntity<?> saveSetup(@RequestBody InferredConfig inferredConfig) {
		try {
			appConfig.writeQuestionConfigs(inferredConfig);
			return ResponseEntity.ok(Map.of("message", "Configuration saved successfully."));
		} catch (IOException e) {
			return ResponseEntity.internalServerError()
					.body(Map.of("error", "Failed to save configuration: " + e.getMessage()));
		}
	}

	/**
	 * Execute generation for a single question using upload IDs. Uses DB cache for
	 * exam context — never re-parses PDF.
	 */
	@PostMapping("/execute")
	public ResponseEntity<?> execute(@RequestBody ExecuteRequest request) {
		String examId = request.getExamId();
		String testerId = request.getTesterId();
		String templateId = request.getTemplateId();
		int numCases = request.getNumCases() > 0 ? request.getNumCases() : 3;

		InferredQuestionConfig iqc = request.getQuestion();
		if (iqc == null) {
			return ResponseEntity.badRequest().body(Map.of("error", "Missing 'question' field."));
		}

		// Load exam context from DB cache (no Docling call)
		String examContext = loadExamContext(examId, iqc.getQuestionId());
		if (examContext == null) {
			return ResponseEntity.badRequest()
					.body(Map.of("error", "Exam not found or could not be parsed. Please upload and analyze first."));
		}

		Path testersDir = resolveTestersDir(testerId);
		Path templateDir = resolveTemplateDir(templateId);

		Path existingTester = null;
		if (testersDir != null && iqc.getTester() != null) {
			Path candidate = testersDir.resolve(iqc.getTester() + ".java");
			if (Files.exists(candidate)) {
				existingTester = candidate;
			}
		}

		List<String> conceptsToCover = request.getConceptsToCover() != null ? request.getConceptsToCover() : List.of();
		List<String> customSuggestions = request.getCustomSuggestions() != null ? request.getCustomSuggestions() : List.of();

		try {
			GenerationResult result = generationService.generateForInferredQuestion(iqc, examContext, existingTester,
					numCases, templateDir, conceptsToCover, customSuggestions);
			return ResponseEntity.ok(result);
		} catch (Exception e) {
			String qid = iqc.getQuestionId() != null ? iqc.getQuestionId() : "unknown";
			return ResponseEntity
					.ok(new GenerationResult(qid, List.of(), false, "Generation failed: " + e.getMessage(), ""));
		}
	}

	/**
	 * Ask the AI to recommend test count and concepts for a question. Uses DB cache
	 * for exam context.
	 */
	@PostMapping("/recommend")
	public ResponseEntity<?> recommend(@RequestBody RecommendRequest request) {
		String examId = request.getExamId();
		String questionId = request.getQuestionId();
		String testerId = request.getTesterId();

		try {
			String examContext = loadExamContext(examId, questionId);
			if (examContext == null) {
				return ResponseEntity.badRequest()
						.body(Map.of("error", "Exam not found or could not be parsed. Please upload first."));
			}

			int existingCaseCount = 0;
			String existingTesterContent = null;

			SessionDatabase.QuestionEntry qEntry = sessionDb.getQuestion(examId, questionId);
			Path testersDir = resolveTestersDir(testerId);
			if (testersDir != null && qEntry != null && qEntry.tester() != null) {
				Path testerFile = testersDir.resolve(qEntry.tester() + ".java");
				if (Files.exists(testerFile)) {
					existingTesterContent = Files.readString(testerFile);
					existingCaseCount = existingTesterContent.lines().filter(l -> l.contains("tcNum++"))
							.mapToInt(l -> 1).sum();
					logger.info("[CONTROLLER] Found existing tester with {} test cases", existingCaseCount);
				}
			}

			TestCaseRecommendation rec = generationService.recommendTestCases(questionId, examContext,
					existingTesterContent, existingCaseCount);
			return ResponseEntity.ok(rec);
		} catch (Exception e) {
			return ResponseEntity.internalServerError()
					.body(Map.of("error", "Recommendation failed: " + e.getMessage()));
		}
	}

	/**
	 * Refine generated code based on user feedback. Uses DB cache for exam context
	 * and delegates to the shared LangChainService (no new client per request).
	 */
	@PostMapping("/refine")
	public ResponseEntity<?> refine(@RequestBody RefineRequest request) {
		String examId = request.getExamId();
		String questionId = request.getQuestionId();
		String currentCode = request.getCurrentCode();
		String refinementPrompt = request.getRefinementPrompt();

		if (currentCode == null || refinementPrompt == null) {
			return ResponseEntity.badRequest().body(Map.of("error", "Missing currentCode or refinementPrompt."));
		}

		String examContext = "";
		if (examId != null) {
			String loaded = loadExamContext(examId, questionId);
			if (loaded != null) {
				examContext = loaded;
			}
		}

		try {
			String refinedCode = generationService.refineCode(questionId, currentCode, refinementPrompt, examContext);
			return ResponseEntity.ok(Map.of("questionId", questionId, "refinedCode", refinedCode));
		} catch (Exception e) {
			return ResponseEntity.internalServerError().body(Map.of("error", "Refinement failed: " + e.getMessage()));
		}
	}

	/**
	 * Read student template source files for the split-pane viewer. Uses templateId
	 * (upload ID) instead of a raw filesystem path.
	 */
	@GetMapping("/template-source")
	public ResponseEntity<?> getTemplateSource(@RequestParam String templateId, @RequestParam String folder) {
		Path templateDir = resolveTemplateDir(templateId);
		if (templateDir == null) {
			return ResponseEntity.badRequest().body(Map.of("error", "Template not found. Please upload first."));
		}

		Path questionFolder = templateDir.resolve(folder);
		if (!Files.isDirectory(questionFolder)) {
			return ResponseEntity.notFound().build();
		}

		try {
			Map<String, String> sources = new java.util.LinkedHashMap<>();
			try (var stream = Files.list(questionFolder)) {
				for (Path f : stream.sorted().toList()) {
					String name = f.getFileName().toString();
					if (name.endsWith(".java")) {
						sources.put(name, Files.readString(f));
					}
				}
			}
			return ResponseEntity.ok(sources);
		} catch (IOException e) {
			return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
		}
	}

	/**
	 * Generate test cases for multiple questions. Uses DB cache for exam context.
	 */
	@PostMapping("/generate")
	public ResponseEntity<?> generate(@RequestBody GenerateRequest request) {
		String examId = request.getExamId();
		Path examPdf = ExamController.EXAM_FILES.get(examId);
		if (examPdf == null || !Files.exists(examPdf)) {
			return ResponseEntity.badRequest().body(Map.of("error", "Exam not found. Please upload first."));
		}

		Path testersDir = resolveTestersDir(request.getTesterId());
		Path templateDir = resolveTemplateDir(request.getTemplateId());
		List<QuestionConfig> allQuestions = appConfig.getQuestionConfigs();
		List<GenerationResult> results = new ArrayList<>();

		for (QuestionSelection sel : request.getQuestions()) {
			QuestionConfig qc = allQuestions.stream().filter(q -> q.getQuestionId().equals(sel.getQuestionId()))
					.findFirst().orElse(null);

			if (qc == null) {
				results.add(new GenerationResult(sel.getQuestionId(), List.of(), false,
						"Unknown question: " + sel.getQuestionId(), ""));
				continue;
			}

			// Load from DB cache instead of re-parsing PDF
			String examContext = loadExamContext(examId, sel.getQuestionId());
			if (examContext == null) {
				results.add(new GenerationResult(sel.getQuestionId(), List.of(), false,
						"Could not load exam context. Please run analyze-setup first.", ""));
				continue;
			}

			Path existingTester = null;
			if (testersDir != null) {
				Path candidate = testersDir.resolve(qc.getTesterClassName() + ".java");
				if (Files.exists(candidate)) {
					existingTester = candidate;
				}
			}

			try {
				GenerationResult result = generationService.generateForQuestion(qc, examContext, existingTester,
						sel.getNumCases(), templateDir, List.of(), List.of());
				results.add(result);
			} catch (Exception e) {
				results.add(new GenerationResult(sel.getQuestionId(), List.of(), false,
						"Generation failed: " + e.getMessage(), ""));
			}
		}

		return ResponseEntity.ok(results);
	}

	/**
	 * Generate test cases for a single question using upload IDs. Uses DB cache.
	 */
	@PostMapping("/generate-question")
	public ResponseEntity<?> generateQuestion(@RequestBody GenerateQuestionRequest request) {
		String examId = request.getExamId();
		String testerId = request.getTesterId();
		String templateId = request.getTemplateId();
		String questionId = request.getQuestionId();
		int numCases = request.getNumCases() > 0 ? request.getNumCases() : 3;

		// Load from DB cache instead of re-parsing PDF
		String examContext = loadExamContext(examId, questionId);
		if (examContext == null) {
			return ResponseEntity.badRequest()
					.body(Map.of("error", "Exam not found or could not be parsed. Please upload and analyze first."));
		}

		Path testersDir = resolveTestersDir(testerId);
		Path templateDir = resolveTemplateDir(templateId);

		QuestionConfig qc = appConfig.getQuestionConfigs().stream().filter(q -> q.getQuestionId().equals(questionId))
				.findFirst().orElse(null);

		if (qc == null) {
			return ResponseEntity.badRequest()
					.body(new GenerationResult(questionId, List.of(), false, "Unknown question: " + questionId, ""));
		}

		Path existingTester = null;
		if (testersDir != null) {
			Path candidate = testersDir.resolve(qc.getTesterClassName() + ".java");
			if (Files.exists(candidate)) {
				existingTester = candidate;
			}
		}

		try {
			GenerationResult result = generationService.generateForQuestion(qc, examContext, existingTester, numCases,
					templateDir, List.of(), List.of());
			return ResponseEntity.ok(result);
		} catch (Exception e) {
			return ResponseEntity
					.ok(new GenerationResult(questionId, List.of(), false, "Generation failed: " + e.getMessage(), ""));
		}
	}

	@GetMapping("/tester/{className}")
	public ResponseEntity<?> getTester(@PathVariable String className, @RequestParam String testerId) {
		Path testersDir = resolveTestersDir(testerId);
		if (testersDir == null) {
			return ResponseEntity.badRequest().body(Map.of("error", "Testers not found. Please upload first."));
		}

		Path testerFile = testersDir.resolve(className + ".java");
		if (!Files.exists(testerFile)) {
			return ResponseEntity.notFound().build();
		}

		try {
			String code = Files.readString(testerFile);
			// Count test cases by counting "tcNum++" occurrences
			long testCaseCount = code.lines().filter(l -> l.contains("tcNum++")).count();
			return ResponseEntity.ok(Map.of("code", code, "testCaseCount", testCaseCount));
		} catch (IOException e) {
			return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
		}
	}

	@PostMapping("/save")
	public ResponseEntity<SaveResponse> save(@RequestBody SaveRequest request) {
		Path testersDir = resolveTestersDir(request.getTesterId());
		Path outputDir = Paths.get(request.getOutputDir() != null ? request.getOutputDir() : "generated-testers");
		Path configPath = Paths.get("src/main/resources/config.properties");

		List<String> savedPaths = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		Map<String, String> fileContents = new java.util.LinkedHashMap<>();

		for (SaveRequest.ResultEntry entry : request.getResults()) {
			try {
				String existingCode = null;
				if (testersDir != null) {
					Path existing = testersDir.resolve(entry.getTesterClassName() + ".java");
					if (Files.exists(existing)) {
						existingCode = Files.readString(existing);
					}
				}

				List<GeneratedTestCase> cases = entry.getCases().stream()
						.map(c -> new GeneratedTestCase(c.getDescription(), c.getInputArgs(), c.getExpectedOutput(),
								c.getWeight()))
						.toList();

				Path saved = testerFileWriter.write(entry.getTesterClassName(), existingCode, entry.getGeneratedCode(),
						cases, testersDir, outputDir);
				savedPaths.add(saved.toString());
				fileContents.put(entry.getTesterClassName(), Files.readString(saved));

				if (request.isUpdateMaxScores()) {
					double totalWeight = cases.stream().mapToDouble(GeneratedTestCase::weight).sum();
					QuestionConfig qc = appConfig.getQuestionConfigs().stream()
							.filter(q -> q.getQuestionId().equals(entry.getQuestionId())).findFirst().orElse(null);
					double originalMax = qc != null ? qc.getMaxScore() : 0;
					double newMax = originalMax + totalWeight;
					testerFileWriter.updateConfigMaxScore(configPath, entry.getQuestionId(), newMax);
				}
			} catch (Exception e) {
				errors.add(entry.getQuestionId() + ": " + e.getMessage());
			}
		}

		return ResponseEntity.ok(new SaveResponse(savedPaths, errors, fileContents));
	}
}

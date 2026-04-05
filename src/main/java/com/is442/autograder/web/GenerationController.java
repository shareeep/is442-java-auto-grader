package com.is442.autograder.web;

import com.is442.autograder.config.AppConfig;
import com.is442.autograder.data.SessionDatabase;
import com.is442.autograder.generation.PdfParser;
import com.is442.autograder.generation.TestGenerationService;
import com.is442.autograder.model.GenerationResult;
import com.is442.autograder.model.InferredQuestionConfig;
import com.is442.autograder.model.QuestionConfig;
import com.is442.autograder.model.TestCaseRecommendation;
import com.is442.autograder.web.dto.ExecuteRequest;
import com.is442.autograder.web.dto.RecommendRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * AI-powered test case generation endpoints. Uses SQLite cache for parsed PDF
 * content — AI generation never re-parses the exam PDF.
 */
@RestController
@RequestMapping("/api/generation")
public class GenerationController {

	private static final Logger logger = LoggerFactory.getLogger(GenerationController.class);

	private final AppConfig appConfig;
	private final TestGenerationService generationService;
	private final SessionDatabase sessionDb;
	private final PdfParser pdfParser;
	private final UploadRegistry uploadRegistry;

	public GenerationController(AppConfig appConfig, TestGenerationService generationService, SessionDatabase sessionDb,
			PdfParser pdfParser, UploadRegistry uploadRegistry) {
		this.appConfig = appConfig;
		this.generationService = generationService;
		this.sessionDb = sessionDb;
		this.pdfParser = pdfParser;
		this.uploadRegistry = uploadRegistry;
	}

	// ── Helpers ──────────────────────────────────────────────────────────────

	private Path resolveTemplateDir(String templateId) {
		if (templateId == null)
			return null;
		return uploadRegistry.getTemplateDirs().get(templateId);
	}

	private Path resolveTestersDir(String testerId) {
		if (testerId == null)
			return null;
		return uploadRegistry.getTesterDirs().get(testerId);
	}

	/**
	 * Load exam context for a question: try DB cache first, fallback to PDF parser.
	 */
	private String loadExamContext(String examId, String questionId) {
		SessionDatabase.QuestionEntry qEntry = sessionDb.getQuestion(examId, questionId);
		if (qEntry != null && qEntry.markdown() != null) {
			logger.info("[CONTROLLER] Using cached question markdown for {}/{}", examId, questionId);
			return qEntry.markdown();
		}

		Path examPdf = uploadRegistry.getExamFiles().get(examId);
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
	 * Generate test cases for a single question.
	 */
	@PostMapping("/tests")
	public ResponseEntity<?> generateTests(@RequestBody ExecuteRequest request) {
		String examId = request.getExamId();
		String testerId = request.getTesterId();
		String templateId = request.getTemplateId();
		int numCases = request.getNumCases() > 0 ? request.getNumCases() : 3;

		InferredQuestionConfig iqc = request.getQuestion();
		if (iqc == null) {
			return ResponseEntity.badRequest().body(Map.of("error", "Missing 'question' field."));
		}

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
		List<String> customSuggestions = request.getCustomSuggestions() != null
				? request.getCustomSuggestions()
				: List.of();

		QuestionConfig qc = new QuestionConfig(iqc.getQuestionId(),
				iqc.getFolder() != null ? iqc.getFolder() : iqc.getQuestionId(),
				iqc.getTester() != null ? iqc.getTester() : iqc.getQuestionId() + "Tester", iqc.getMaxScore(),
				iqc.getDependencyFolder(),
				iqc.getDependencyFiles() != null ? iqc.getDependencyFiles() : Collections.emptyList());

		try {
			GenerationResult result = generationService.generateTestCases(qc, examContext, existingTester, numCases,
					templateDir, conceptsToCover, customSuggestions);
			if (!result.isCompiledOk()) {
				return ResponseEntity.status(500).body(result);
			}
			return ResponseEntity.ok(result);
		} catch (Exception e) {
			String qid = iqc.getQuestionId() != null ? iqc.getQuestionId() : "unknown";
			return ResponseEntity.status(500)
					.body(new GenerationResult(qid, List.of(), false, "Generation failed: " + e.getMessage(), ""));
		}
	}

	/**
	 * Ask the AI to recommend test count and concepts for a question.
	 */
	@PostMapping("/recommendations")
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

}

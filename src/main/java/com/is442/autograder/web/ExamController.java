package com.is442.autograder.web;

import com.is442.autograder.config.AppConfig;
import com.is442.autograder.data.SessionDatabase;
import com.is442.autograder.generation.ConfigInferenceService;
import com.is442.autograder.generation.PdfParser;
import com.is442.autograder.model.InferredConfig;
import com.is442.autograder.model.InferredQuestionConfig;
import com.is442.autograder.web.dto.AnalyzeSetupRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * Handles exam PDF uploads and exam-level operations (parse, analyze).
 */
@RestController
@RequestMapping("/api/exams")
public class ExamController {

	private static final Logger logger = LoggerFactory.getLogger(ExamController.class);

	private final UploadRegistry uploadRegistry;
	private final AppConfig appConfig;
	private final SessionDatabase sessionDb;
	private final PdfParser pdfParser;
	private final ConfigInferenceService configInferenceService;

	public ExamController(UploadRegistry uploadRegistry, AppConfig appConfig, SessionDatabase sessionDb,
			PdfParser pdfParser, ConfigInferenceService configInferenceService) {
		this.uploadRegistry = uploadRegistry;
		this.appConfig = appConfig;
		this.sessionDb = sessionDb;
		this.pdfParser = pdfParser;
		this.configInferenceService = configInferenceService;
	}

	@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<Map<String, String>> uploadExam(@RequestPart("file") MultipartFile file) throws IOException {
		if (file.isEmpty()) {
			return ResponseEntity.badRequest().body(Map.of("error", "No file provided"));
		}

		String originalName = file.getOriginalFilename();
		if (originalName == null || !originalName.toLowerCase().endsWith(".pdf")) {
			return ResponseEntity.badRequest().body(Map.of("error", "Only PDF files are accepted"));
		}

		String examId = UUID.randomUUID().toString();
		Path tempDir = Files.createTempDirectory("autograder-exam-");
		Path tempFile = tempDir.resolve(originalName);
		file.transferTo(tempFile.toFile());

		uploadRegistry.getExamFiles().put(examId, tempFile);
		tempFile.toFile().deleteOnExit();
		tempDir.toFile().deleteOnExit();

		logger.info("[UPLOAD] Exam PDF stored  examId={} file={} bytes={}", examId, originalName,
				tempFile.toFile().length());
		return ResponseEntity.ok(Map.of("examId", examId, "fileName", originalName));
	}

	/**
	 * Pre-parse PDF after upload (non-blocking warm-up for the Docling cache).
	 */
	@PostMapping("/{examId}/parse")
	public ResponseEntity<?> parsePdf(@PathVariable String examId) {
		Path examPdf = uploadRegistry.getExamFiles().get(examId);
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
	 * Analyze the uploaded exam PDF alongside the uploaded template and tester
	 * directories to produce an InferredConfig.
	 */
	@PostMapping("/{examId}/analyze")
	public ResponseEntity<?> analyzeSetup(@PathVariable String examId, @RequestBody AnalyzeSetupRequest request) {
		Path examPdf = uploadRegistry.getExamFiles().get(examId);
		if (examPdf == null || !Files.exists(examPdf)) {
			return ResponseEntity.badRequest().body(Map.of("error", "Exam not found. Please upload first."));
		}

		String templateId = request.getTemplateId();
		String testerId = request.getTesterId();
		Path templateDir = templateId != null ? uploadRegistry.getTemplateDirs().get(templateId) : null;
		Path testersDir = testerId != null ? uploadRegistry.getTesterDirs().get(testerId) : null;

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
}

package com.is442.autograder.web;

import com.is442.autograder.config.AppConfig;
import com.is442.autograder.generation.TestGenerationService;
import com.is442.autograder.generation.TesterFileWriter;
import com.is442.autograder.model.GeneratedTestCase;
import com.is442.autograder.model.GenerationResult;
import com.is442.autograder.model.QuestionConfig;
import com.is442.autograder.web.dto.GenerateRequest;
import com.is442.autograder.web.dto.QuestionSelection;
import com.is442.autograder.web.dto.SaveRequest;
import com.is442.autograder.web.dto.SaveResponse;

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
 */
@RestController
@RequestMapping("/api/generation")
public class GenerationController {

	private final AppConfig appConfig;
	private final TestGenerationService generationService;
	private final TesterFileWriter testerFileWriter;

	public GenerationController(AppConfig appConfig, TestGenerationService generationService,
			TesterFileWriter testerFileWriter) {
		this.appConfig = appConfig;
		this.generationService = generationService;
		this.testerFileWriter = testerFileWriter;
	}

	@PostMapping("/generate")
	public ResponseEntity<?> generate(@RequestBody GenerateRequest request) {
		Path examPdf = ExamController.EXAM_FILES.get(request.getExamId());
		if (examPdf == null || !Files.exists(examPdf)) {
			return ResponseEntity.badRequest().body(Map.of("error", "Exam not found. Please upload first."));
		}

		Path testersDir = request.getTestersDir() != null ? Paths.get(request.getTestersDir()) : null;
		Path templateDir = request.getTemplateDir() != null ? Paths.get(request.getTemplateDir()) : null;
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

			Path existingTester = null;
			if (testersDir != null) {
				Path candidate = testersDir.resolve(qc.getTesterClassName() + ".java");
				if (Files.exists(candidate)) {
					existingTester = candidate;
				}
			}

			try {
				GenerationResult result = generationService.generateForQuestion(qc, examPdf, existingTester,
						sel.getNumCases(), templateDir);
				results.add(result);
			} catch (Exception e) {
				results.add(new GenerationResult(sel.getQuestionId(), List.of(), false,
						"Generation failed: " + e.getMessage(), ""));
			}
		}

		return ResponseEntity.ok(results);
	}

	/**
	 * Generate test cases for a single question. Used by the frontend for
	 * per-question progress tracking.
	 */
	@PostMapping("/generate-question")
	public ResponseEntity<?> generateQuestion(@RequestBody Map<String, Object> request) {
		String examId = (String) request.get("examId");
		String testersDirStr = (String) request.get("testersDir");
		String templateDirStr = (String) request.get("templateDir");
		String questionId = (String) request.get("questionId");
		int numCases = request.get("numCases") != null ? ((Number) request.get("numCases")).intValue() : 3;

		Path examPdf = ExamController.EXAM_FILES.get(examId);
		if (examPdf == null || !Files.exists(examPdf)) {
			return ResponseEntity.badRequest().body(Map.of("error", "Exam not found. Please upload first."));
		}

		Path testersDir = testersDirStr != null ? Paths.get(testersDirStr) : null;
		Path templateDir = templateDirStr != null ? Paths.get(templateDirStr) : null;

		QuestionConfig qc = appConfig.getQuestionConfigs().stream()
				.filter(q -> q.getQuestionId().equals(questionId)).findFirst().orElse(null);

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
			GenerationResult result = generationService.generateForQuestion(qc, examPdf, existingTester, numCases,
					templateDir);
			return ResponseEntity.ok(result);
		} catch (Exception e) {
			return ResponseEntity.ok(new GenerationResult(questionId, List.of(), false,
					"Generation failed: " + e.getMessage(), ""));
		}
	}

	@GetMapping("/tester/{className}")
	public ResponseEntity<?> getTester(@PathVariable String className, @RequestParam String dir) {
		Path testerFile = Paths.get(dir).resolve(className + ".java");
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
		Path testersDir = request.getTestersDir() != null ? Paths.get(request.getTestersDir()) : null;
		Path outputDir = Paths.get(request.getOutputDir() != null ? request.getOutputDir() : "generated-testers");
		Path configPath = Paths.get("src/main/resources/config.properties");

		List<String> savedPaths = new ArrayList<>();
		List<String> errors = new ArrayList<>();

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

				if (request.isUpdateMaxScores()) {
					double totalWeight = cases.stream().mapToDouble(GeneratedTestCase::weight).sum();
					// Add original max score
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

		return ResponseEntity.ok(new SaveResponse(savedPaths, errors));
	}
}

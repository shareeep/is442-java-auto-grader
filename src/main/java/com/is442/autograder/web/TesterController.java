package com.is442.autograder.web;

import com.is442.autograder.config.AppConfig;
import com.is442.autograder.generation.TesterFileWriter;
import com.is442.autograder.model.GeneratedTestCase;
import com.is442.autograder.model.QuestionConfig;
import com.is442.autograder.web.dto.SaveRequest;
import com.is442.autograder.web.dto.SaveResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Handles tester file uploads, reads, and saves.
 */
@RestController
@RequestMapping("/api/testers")
public class TesterController {

	private static final Logger logger = LoggerFactory.getLogger(TesterController.class);

	private final UploadRegistry uploadRegistry;
	private final AppConfig appConfig;
	private final TesterFileWriter testerFileWriter;

	public TesterController(UploadRegistry uploadRegistry, AppConfig appConfig, TesterFileWriter testerFileWriter) {
		this.uploadRegistry = uploadRegistry;
		this.appConfig = appConfig;
		this.testerFileWriter = testerFileWriter;
	}

	/**
	 * Accepts tester .java files (flat directory).
	 */
	@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<Map<String, Object>> uploadTesters(@RequestPart("files") MultipartFile[] files)
			throws IOException {

		String testerId = UUID.randomUUID().toString();
		Path tempDir = Files.createTempDirectory("autograder-testers-");

		int saved = 0;
		for (MultipartFile file : files) {
			if (file.isEmpty() || file.getOriginalFilename() == null)
				continue;
			if (file.getOriginalFilename().contains(".DS_Store"))
				continue;

			Path dest = tempDir.resolve(Paths.get(file.getOriginalFilename()).getFileName());
			file.transferTo(dest.toFile());
			dest.toFile().deleteOnExit();
			saved++;
		}

		uploadRegistry.getTesterDirs().put(testerId, tempDir);
		tempDir.toFile().deleteOnExit();

		logger.info("[UPLOAD] Testers dir stored  testerId={} files={} path={}", testerId, saved, tempDir);
		return ResponseEntity.ok(Map.of("testerId", testerId, "fileCount", saved));
	}

	@GetMapping("/{className}")
	public ResponseEntity<?> getTester(@PathVariable String className, @RequestParam String testerId) {
		Path testersDir = uploadRegistry.getTesterDirs().get(testerId);
		if (testersDir == null) {
			return ResponseEntity.badRequest().body(Map.of("error", "Testers not found. Please upload first."));
		}

		Path testerFile = testersDir.resolve(className + ".java");
		if (!Files.exists(testerFile)) {
			return ResponseEntity.notFound().build();
		}

		try {
			String code = Files.readString(testerFile);
			long testCaseCount = code.lines().filter(l -> l.contains("tcNum++")).count();
			return ResponseEntity.ok(Map.of("code", code, "testCaseCount", testCaseCount));
		} catch (IOException e) {
			return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
		}
	}

	@PostMapping("/save")
	public ResponseEntity<SaveResponse> save(@RequestBody SaveRequest request) {
		Path testersDir = request.getTesterId() != null ? uploadRegistry.getTesterDirs().get(request.getTesterId())
				: null;
		Path outputDir = Paths.get(request.getOutputDir() != null ? request.getOutputDir() : "generated-testers");
		Path configPath = Paths.get("src/main/resources/config.properties");

		List<String> savedPaths = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		Map<String, String> fileContents = new LinkedHashMap<>();

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

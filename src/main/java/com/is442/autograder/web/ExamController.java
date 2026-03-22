package com.is442.autograder.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles exam PDF upload. Files are stored in a temp directory keyed by UUID.
 * Temp files are cleaned up on JVM shutdown.
 */
@RestController
@RequestMapping("/api/generation/exam")
public class ExamController {

	/** Maps examId → temp file path. Shared with GenerationController. */
	static final ConcurrentHashMap<String, Path> EXAM_FILES = new ConcurrentHashMap<>();

	@PostMapping("/upload")
	public ResponseEntity<Map<String, String>> uploadExam(@RequestParam("file") MultipartFile file) throws IOException {
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

		EXAM_FILES.put(examId, tempFile);

		// Clean up on shutdown
		tempFile.toFile().deleteOnExit();
		tempDir.toFile().deleteOnExit();

		return ResponseEntity.ok(Map.of("examId", examId, "fileName", originalName));
	}
}

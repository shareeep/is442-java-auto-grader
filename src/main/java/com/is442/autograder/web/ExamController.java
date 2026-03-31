package com.is442.autograder.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles file uploads for the test generator wizard. All uploads are stored in
 * temp directories keyed by UUID, cleaned up on JVM shutdown.
 */
@RestController
@RequestMapping("/api/generation")
public class ExamController {

	private static final Logger logger = LoggerFactory.getLogger(ExamController.class);

	/** Maps examId → exam PDF temp path. */
	static final ConcurrentHashMap<String, Path> EXAM_FILES = new ConcurrentHashMap<>();

	/** Maps templateId → extracted template directory. */
	static final ConcurrentHashMap<String, Path> TEMPLATE_DIRS = new ConcurrentHashMap<>();

	/** Maps testerId → directory containing uploaded Tester.java files. */
	static final ConcurrentHashMap<String, Path> TESTER_DIRS = new ConcurrentHashMap<>();

	@PostMapping(value = "/exam/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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

		EXAM_FILES.put(examId, tempFile);
		tempFile.toFile().deleteOnExit();
		tempDir.toFile().deleteOnExit();

		logger.info("[UPLOAD] Exam PDF stored  examId={} file={} bytes={}", examId, originalName,
				tempFile.toFile().length());
		return ResponseEntity.ok(Map.of("examId", examId, "fileName", originalName));
	}

	/**
	 * Accepts the contents of a template directory (uploaded via webkitdirectory).
	 * Each file's relative path is encoded in its filename using "__SEP__" as the
	 * directory separator so the server can reconstruct the folder structure.
	 */
	@PostMapping(value = "/template/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<Map<String, Object>> uploadTemplate(@RequestPart("files") MultipartFile[] files)
			throws IOException {

		String templateId = UUID.randomUUID().toString();
		Path tempDir = Files.createTempDirectory("autograder-template-");

		int saved = 0;
		for (MultipartFile file : files) {
			if (file.isEmpty() || file.getOriginalFilename() == null)
				continue;
			if (file.getOriginalFilename().contains(".DS_Store"))
				continue;

			// Decode the path separator we encoded on the client side
			String relativePath = file.getOriginalFilename().replace("__SEP__", File.separator);
			Path dest = tempDir.resolve(relativePath);
			Files.createDirectories(dest.getParent());
			file.transferTo(dest.toFile());
			dest.toFile().deleteOnExit();
			saved++;
		}

		TEMPLATE_DIRS.put(templateId, tempDir);
		tempDir.toFile().deleteOnExit();

		logger.info("[UPLOAD] Template dir stored  templateId={} files={} path={}", templateId, saved, tempDir);
		return ResponseEntity.ok(Map.of("templateId", templateId, "fileCount", saved));
	}

	/**
	 * Accepts tester .java files (flat directory — no subdirectories needed).
	 */
	@PostMapping(value = "/testers/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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

		TESTER_DIRS.put(testerId, tempDir);
		tempDir.toFile().deleteOnExit();

		logger.info("[UPLOAD] Testers dir stored  testerId={} files={} path={}", testerId, saved, tempDir);
		return ResponseEntity.ok(Map.of("testerId", testerId, "fileCount", saved));
	}
}

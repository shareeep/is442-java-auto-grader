package com.is442.autograder.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles template directory uploads for the test generator wizard.
 */
@RestController
@RequestMapping("/api/templates")
public class TemplateController {

	private static final Logger logger = LoggerFactory.getLogger(TemplateController.class);

	private final UploadRegistry uploadRegistry;

	public TemplateController(UploadRegistry uploadRegistry) {
		this.uploadRegistry = uploadRegistry;
	}

	/**
	 * Accepts the contents of a template directory (uploaded via webkitdirectory).
	 * Each file's relative path is encoded in its filename using "__SEP__" as the
	 * directory separator so the server can reconstruct the folder structure.
	 */
	@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<Map<String, Object>> uploadTemplate(@RequestPart("files") MultipartFile[] files)
			throws IOException {

		String templateId = UUID.randomUUID().toString();
		Path tempDir = Files.createTempDirectory("autograder-template-");

		int saved = 0;
		List<String> savedPaths = new ArrayList<>();
		for (MultipartFile file : files) {
			if (file.isEmpty() || file.getOriginalFilename() == null)
				continue;
			if (file.getOriginalFilename().contains(".DS_Store"))
				continue;

			String originalName = file.getOriginalFilename();
			String relativePath = originalName.replace("__SEP__", File.separator);
			Path dest = tempDir.resolve(relativePath);
			Files.createDirectories(dest.getParent());
			file.transferTo(dest.toFile());
			dest.toFile().deleteOnExit();
			saved++;
			savedPaths.add(relativePath);
		}

		uploadRegistry.getTemplateDirs().put(templateId, tempDir);
		tempDir.toFile().deleteOnExit();

		logger.info("[UPLOAD] Template dir stored  templateId={} files={} path={}", templateId, saved, tempDir);
		logger.info("[UPLOAD] Template files: {}", savedPaths);
		return ResponseEntity.ok(Map.of("templateId", templateId, "fileCount", saved));
	}
}

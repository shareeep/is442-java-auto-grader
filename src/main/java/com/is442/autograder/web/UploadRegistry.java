package com.is442.autograder.web;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Shared registry of uploaded file paths, keyed by UUID. Injected into
 * controllers that need to resolve upload IDs to server-side paths. Replaces
 * the static maps previously held on ExamController.
 */
@Component
public class UploadRegistry {

	private final ConcurrentHashMap<String, Path> examFiles = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Path> templateDirs = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Path> testerDirs = new ConcurrentHashMap<>();

	public ConcurrentHashMap<String, Path> getExamFiles() {
		return examFiles;
	}

	public ConcurrentHashMap<String, Path> getTemplateDirs() {
		return templateDirs;
	}

	public ConcurrentHashMap<String, Path> getTesterDirs() {
		return testerDirs;
	}
}

package com.is442.autograder.web.dto;

import java.util.List;
import java.util.Map;

public class SaveResponse {

	private final List<String> savedPaths;
	private final List<String> errors;
	private final Map<String, String> fileContents;

	public SaveResponse(List<String> savedPaths, List<String> errors, Map<String, String> fileContents) {
		this.savedPaths = savedPaths;
		this.errors = errors;
		this.fileContents = fileContents;
	}

	public List<String> getSavedPaths() {
		return savedPaths;
	}

	public List<String> getErrors() {
		return errors;
	}

	public Map<String, String> getFileContents() {
		return fileContents;
	}
}

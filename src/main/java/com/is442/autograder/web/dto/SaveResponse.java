package com.is442.autograder.web.dto;

import java.util.List;

public class SaveResponse {

	private final List<String> savedPaths;
	private final List<String> errors;

	public SaveResponse(List<String> savedPaths, List<String> errors) {
		this.savedPaths = savedPaths;
		this.errors = errors;
	}

	public List<String> getSavedPaths() {
		return savedPaths;
	}

	public List<String> getErrors() {
		return errors;
	}
}

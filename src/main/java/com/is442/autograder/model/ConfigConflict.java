package com.is442.autograder.model;

/**
 * Represents a conflict or warning detected during auto-configuration
 * inference. For example, a tester file is present but its corresponding source
 * directory is missing.
 */
public class ConfigConflict {

	public enum ConflictType {
		MISSING_FOLDER, MISSING_TESTER, ORPHAN_TESTER, INCOMPLETE_DEPENDENCIES, MISSING_TEMPLATE
	}

	private String questionId;
	private ConflictType type;
	private String message;
	private String suggestion;

	public ConfigConflict() {
	}

	public ConfigConflict(String questionId, ConflictType type, String message, String suggestion) {
		this.questionId = questionId;
		this.type = type;
		this.message = message;
		this.suggestion = suggestion;
	}

	public String getQuestionId() {
		return questionId;
	}

	public void setQuestionId(String questionId) {
		this.questionId = questionId;
	}

	public ConflictType getType() {
		return type;
	}

	public void setType(ConflictType type) {
		this.type = type;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getSuggestion() {
		return suggestion;
	}

	public void setSuggestion(String suggestion) {
		this.suggestion = suggestion;
	}
}

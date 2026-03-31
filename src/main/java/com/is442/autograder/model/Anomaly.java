package com.is442.autograder.model;

/**
 * Represents an anomaly detected during the grading process.
 */
public class Anomaly {
	private static final String ERROR_SYMBOL = "✖";
	private static final String NON_ERROR_SYMBOL = "⚠";

	public enum Type {
		FOLDER_NOT_RENAMED, // Folder still "RenameToYourStudentID" or "RenameToYourUsername"
		STUDENT_ID_AS_FOLDER, // Folder uses OrgDefinedId instead of email (e.g. "01400003")
		NO_PARENT_FOLDER, // Q1/Q2/Q3 archived directly without parent folder
		EXTRA_NESTING, // Extra levels of nesting before Q1/Q2/Q3
		MISSING_QUESTION_FOLDER, // Expected Qx folder not found
		MISSING_JAVA_FILE, // Expected .java file not found in question folder
		MISSING_HEADER, // Java file missing Name/Email header
		INCOMPLETE_HEADER, // Header found but Name or Email is empty
		COMPILATION_ERROR, // javac failed
		RUNTIME_ERROR, // java exited with non-zero
		EXECUTION_TIMEOUT, // Process exceeded timeout
		IDENTITY_MISMATCH // Folder name doesn't match resolved identity from header
	}

	public enum Severity {
		INFO, WARNING, ERROR
	}

	private final Type type;
	private final String description;
	private final Severity severity;
	private final String questionId; // may be null for submission-level anomalies

	public Anomaly(Type type, String description, Severity severity) {
		this(type, description, severity, null);
	}

	public Anomaly(Type type, String description, Severity severity, String questionId) {
		this.type = type;
		this.description = description;
		this.severity = severity;
		this.questionId = questionId;
	}

	public Type getType() {
		return type;
	}

	public String getDescription() {
		return description;
	}

	public Severity getSeverity() {
		return severity;
	}

	public String getQuestionId() {
		return questionId;
	}

	@Override
	public String toString() {
		String prefix = severity == Severity.ERROR ? ERROR_SYMBOL : NON_ERROR_SYMBOL;
		String qInfo = questionId != null ? " [" + questionId + "]" : "";
		return prefix + qInfo + " " + description;
	}
}

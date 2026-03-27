package com.is442.autograder.reporting;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.is442.autograder.model.StudentSubmission;

/**
 * Shared helpers for reporting log files and timestamps.
 */
final class ReportLogSupport {

	private static final DateTimeFormatter LOG_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
			.withZone(ZoneId.systemDefault());

	private ReportLogSupport() {
	}

	static String timestampNow() {
		return LOG_TIMESTAMP.format(Instant.now());
	}

	static String sanitizeStudentDirectory(StudentSubmission submission) {
		return sanitizePathSegment(submission.getDisplayName());
	}

	static String sanitizePathSegment(String value) {
		if (value == null || value.isBlank()) {
			return "unknown";
		}
		return value.replaceAll("[^A-Za-z0-9._-]", "_");
	}

	static String buildQuestionLogContent(StudentSubmission submission, String questionId, String phase, String stdout,
			String stderr) {
		StringBuilder content = new StringBuilder();
		content.append("Student: ").append(submission.getDisplayName()).append("\n");
		content.append("Question: ").append(questionId).append("\n");
		content.append("Phase: ").append(phase).append("\n");
		content.append("Timestamp: ").append(timestampNow()).append("\n");
		content.append("\n");
		content.append("--- STDOUT ---\n");
		appendLogBody(content, stdout);
		content.append("\n--- STDERR ---\n");
		appendLogBody(content, stderr);
		return content.toString();
	}

	private static void appendLogBody(StringBuilder content, String body) {
		if (body == null || body.isBlank()) {
			return;
		}
		content.append(body);
		if (!body.endsWith("\n")) {
			content.append("\n");
		}
	}
}

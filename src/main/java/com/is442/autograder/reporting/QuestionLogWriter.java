package com.is442.autograder.reporting;

import com.is442.autograder.model.StudentSubmission;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Writes full stdout/stderr logs per student and question.
 */
public class QuestionLogWriter {

	private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
			.withZone(ZoneId.systemDefault());
	private final Path logsDir;

	public QuestionLogWriter(Path outputDir) {
		this.logsDir = outputDir.resolve("logs");
	}

	public void write(StudentSubmission submission, String questionId, String phase, String stdout, String stderr)
			throws IOException {
		String studentDirName = sanitize(submission.getDisplayName());
		Path studentDir = logsDir.resolve(studentDirName);
		Files.createDirectories(studentDir);
		Path logFile = studentDir.resolve(questionId + "-" + phase + ".log");

		StringBuilder content = new StringBuilder();
		content.append("Student: ").append(submission.getDisplayName()).append("\n");
		content.append("Question: ").append(questionId).append("\n");
		content.append("Phase: ").append(phase).append("\n");
		content.append("Timestamp: ").append(TS_FORMATTER.format(Instant.now())).append("\n");
		content.append("\n");
		content.append("--- STDOUT ---\n");
		if (stdout != null && !stdout.isBlank()) {
			content.append(stdout);
			if (!stdout.endsWith("\n")) {
				content.append("\n");
			}
		}
		content.append("\n--- STDERR ---\n");
		if (stderr != null && !stderr.isBlank()) {
			content.append(stderr);
			if (!stderr.endsWith("\n")) {
				content.append("\n");
			}
		}

		Files.writeString(logFile, content.toString());
	}

	public void appendSummaryLine(String line) throws IOException {
		Files.createDirectories(logsDir);
		Path summary = logsDir.resolve("errors-summary.log");
		String entry = line.endsWith("\n") ? line : line + "\n";
		Files.writeString(summary, entry, java.nio.file.StandardOpenOption.CREATE,
				java.nio.file.StandardOpenOption.APPEND);
	}

	private String sanitize(String value) {
		if (value == null || value.isBlank()) {
			return "unknown";
		}
		return value.replaceAll("[^A-Za-z0-9._-]", "_");
	}
}

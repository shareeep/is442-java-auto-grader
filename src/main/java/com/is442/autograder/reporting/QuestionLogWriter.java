package com.is442.autograder.reporting;

import com.is442.autograder.model.StudentSubmission;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes full stdout/stderr logs per student and question.
 */
public class QuestionLogWriter {

	private final Path logsDir;

	public QuestionLogWriter(Path outputDir) {
		this.logsDir = outputDir.resolve("logs");
	}

	public void write(StudentSubmission submission, String questionId, String phase, String stdout, String stderr)
			throws IOException {
		String studentDirName = ReportLogSupport.sanitizeStudentDirectory(submission);
		Path studentDir = logsDir.resolve(studentDirName);
		Files.createDirectories(studentDir);
		Path logFile = studentDir.resolve(questionId + "-" + phase + ".log");

		Files.writeString(logFile,
				ReportLogSupport.buildQuestionLogContent(submission, questionId, phase, stdout, stderr));
	}

	public void appendSummaryLine(String line) throws IOException {
		Files.createDirectories(logsDir);
		Path summary = logsDir.resolve("errors-summary.log");
		String entry = line.endsWith("\n") ? line : line + "\n";
		Files.writeString(summary, entry, java.nio.file.StandardOpenOption.CREATE,
				java.nio.file.StandardOpenOption.APPEND);
	}
}

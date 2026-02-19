package com.is442.autograder.execution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.logging.Logger;

import com.is442.autograder.model.Anomaly;
import com.is442.autograder.model.ProcessResult;
import com.is442.autograder.model.QuestionConfig;
import com.is442.autograder.model.QuestionResult;
import com.is442.autograder.model.StudentSubmission;
import com.is442.autograder.reporting.ConsoleReporter;
import com.is442.autograder.reporting.QuestionLogWriter;

/**
 * Orchestrates the grading flow for a single submission: For each question:
 * copy tester → compile → run → parse score.
 */
public class GradingEngine {

	private static final Logger LOGGER = Logger.getLogger(GradingEngine.class.getName());

	private final ProcessRunner processRunner;
	private final QuestionLogWriter questionLogWriter;
	private final ConsoleReporter consoleReporter;

	public GradingEngine(ProcessRunner processRunner, QuestionLogWriter questionLogWriter,
			ConsoleReporter consoleReporter) {
		this.processRunner = processRunner;
		this.questionLogWriter = questionLogWriter;
		this.consoleReporter = consoleReporter;
	}

	/**
	 * Grade all questions for a single student submission.
	 *
	 * @param submissionRoot
	 *            path containing Q1/, Q2/, Q3/
	 * @param testerFilesDir
	 *            path to the directory containing tester .java files
	 * @param questionConfigs
	 *            list of question configurations
	 * @param submission
	 *            submission to add results and anomalies to
	 */
	public void grade(Path submissionRoot, Path testerFilesDir, List<QuestionConfig> questionConfigs,
			StudentSubmission submission) {

		for (QuestionConfig qc : questionConfigs) {
			QuestionResult result = gradeQuestion(submissionRoot, testerFilesDir, qc, submission);
			submission.addResult(result);
		}
	}

	/**
	 * Grade a single question: copy tester, compile, run, parse score.
	 */
	private QuestionResult gradeQuestion(Path submissionRoot, Path testerFilesDir, QuestionConfig qc,
			StudentSubmission submission) {

		String questionId = qc.getQuestionId();
		Path questionFolder = submissionRoot.resolve(qc.getFolder());

		// 1. Check if question folder exists
		if (!Files.isDirectory(questionFolder)) {
			LOGGER.warning(submission.getDisplayName() + " - " + questionId + ": question folder not found");
			logRaw(submission.getDisplayName() + " - " + questionId + ": question folder not found");
			return QuestionResult.missing(questionId, qc.getMaxScore());
		}

		// 2. Copy tester file into question folder
		Path testerSource = testerFilesDir.resolve(qc.getTesterClassName() + ".java");
		Path testerDest = questionFolder.resolve(qc.getTesterClassName() + ".java");

		try {
			if (!Files.isRegularFile(testerSource)) {
				LOGGER.warning("Tester file not found: " + testerSource);
				logRaw("Tester file not found: " + testerSource.getFileName());
				return QuestionResult.compilationFailure(questionId, qc.getMaxScore(),
						"Tester file not found: " + testerSource.getFileName());
			}
			Files.copy(testerSource, testerDest, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			return QuestionResult.compilationFailure(questionId, qc.getMaxScore(),
					"Failed to copy tester: " + e.getMessage());
		}

		// 2.5. For Q3, auto-provide Shape/Circle/Rectangle class files
		if ("Q3".equals(questionId)) {
			copyQ3Dependencies(questionFolder, testerFilesDir);
		}

		// 2.6. For Q2, auto-provide DataException class file and data .txt files
		if ("Q2a".equals(questionId) || "Q2b".equals(questionId)) {
			copyQ2Dependencies(questionFolder, testerFilesDir);
		}

		try {
			// 3. Compile all .java files
			ProcessResult compileResult = processRunner.compile(questionFolder);
			writeLogIfAvailable(submission, questionId, "compile", compileResult.getStdout(),
					compileResult.getStderr());

			if (!compileResult.isSuccess()) {
				String error = compileResult.getStderr().isEmpty()
						? compileResult.getStdout()
						: compileResult.getStderr();
				LOGGER.warning(submission.getDisplayName() + " - " + questionId + ": compilation failed");
				String singleLineError = firstRelevantLine(error);
				String summaryLine = submission.getDisplayName() + " - " + questionId + ": " + singleLineError;
				logRaw(summaryLine);
				appendErrorSummary(summaryLine);
				String filteredError = filterNoteLines(error);
				writeLogIfAvailable(submission, questionId, "compile-error", filteredError, "");
				submission.addAnomaly(new Anomaly(Anomaly.Type.COMPILATION_ERROR,
						"Compilation error in " + questionId + ": " + truncate(error, 200), Anomaly.Severity.ERROR,
						questionId));
				return QuestionResult.compilationFailure(questionId, qc.getMaxScore(), truncate(error, 500));
			}

			// 4. Run tester
			ProcessResult runResult = processRunner.run(questionFolder, qc.getTesterClassName());
			writeLogIfAvailable(submission, questionId, "run", runResult.getStdout(), runResult.getStderr());

			if (runResult.isTimedOut()) {
				LOGGER.warning(submission.getDisplayName() + " - " + questionId + ": execution timed out");
				String summaryLine = submission.getDisplayName() + " - " + questionId + ": execution timed out";
				logRaw(summaryLine);
				appendErrorSummary(summaryLine);
				// Parse partial score from stdout before the hang
				// Each tester prints "Passed" per successful test (score += 1 each)
				double partialScore = parsePartialScore(runResult.getStdout());
				writeLogIfAvailable(submission, questionId, "timeout", runResult.getStdout(), runResult.getStderr());
				submission.addAnomaly(new Anomaly(Anomaly.Type.EXECUTION_TIMEOUT,
						"Execution timed out for " + questionId
								+ (partialScore > 0 ? " (partial score: " + partialScore + ")" : ""),
						Anomaly.Severity.ERROR, questionId));
				return new QuestionResult(questionId, partialScore, qc.getMaxScore(), true, false,
						runResult.getStdout(), "Execution timed out");
			}

			if (!runResult.isSuccess()) {
				String error = runResult.getStderr().isEmpty() ? runResult.getStdout() : runResult.getStderr();
				String singleLineError = firstRelevantLine(error);
				String summaryLine = submission.getDisplayName() + " - " + questionId + ": " + singleLineError;
				logRaw(summaryLine);
				appendErrorSummary(summaryLine);
				String filteredError = filterNoteLines(error);
				writeLogIfAvailable(submission, questionId, "runtime-error", filteredError, "");
				submission.addAnomaly(new Anomaly(Anomaly.Type.RUNTIME_ERROR,
						"Runtime error in " + questionId + ": " + truncate(error, 200), Anomaly.Severity.ERROR,
						questionId));
			}

			// 5. Parse score from last line of stdout
			double score = parseScore(runResult.getStdout());

			return new QuestionResult(questionId, score, qc.getMaxScore(), true, true, runResult.getStdout(),
					runResult.getStderr());

		} finally {
			// 6. Clean up: remove tester file and compiled classes
			cleanupTester(questionFolder, qc.getTesterClassName());
		}
	}

	private void writeLogIfAvailable(StudentSubmission submission, String questionId, String phase, String stdout,
			String stderr) {
		if (questionLogWriter == null) {
			return;
		}
		try {
			questionLogWriter.write(submission, questionId, phase, stdout, stderr);
		} catch (IOException e) {
			LOGGER.warning("Failed to write log for " + submission.getDisplayName() + " " + questionId + ": "
					+ e.getMessage());
		}
	}

	private void logWarning(String message) {
		if (consoleReporter == null) {
			return;
		}
		consoleReporter.logWarning(message);
	}

	private void logRaw(String message) {
		if (consoleReporter == null) {
			return;
		}
		consoleReporter.logRaw(message);
	}

	private String firstRelevantLine(String text) {
		if (text == null || text.isBlank()) {
			return "Compilation failed";
		}
		String[] lines = text.split("\n");
		for (String line : lines) {
			String trimmed = line.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			if (trimmed.contains("com.is442.autograder.")) {
				continue;
			}
			if (trimmed.startsWith("Note:")) {
				continue;
			}
			return trimmed;
		}
		return "Compilation failed";
	}

	private String filterNoteLines(String text) {
		if (text == null || text.isBlank()) {
			return text;
		}
		StringBuilder filtered = new StringBuilder();
		String[] lines = text.split("\n");
		for (String line : lines) {
			String trimmed = line.trim();
			if (trimmed.startsWith("Note:")) {
				continue;
			}
			filtered.append(line).append("\n");
		}
		return filtered.toString().trim();
	}

	private void appendErrorSummary(String summaryLine) {
		if (questionLogWriter == null) {
			return;
		}
		try {
			questionLogWriter.appendSummaryLine(summaryLine);
		} catch (IOException e) {
			LOGGER.warning("Failed to write error summary: " + e.getMessage());
		}
	}

	/**
	 * Parse the score from the last non-empty line of stdout. The tester files
	 * print the numeric score as the very last line.
	 */
	private double parseScore(String stdout) {
		if (stdout == null || stdout.isBlank()) {
			return 0.0;
		}

		String[] lines = stdout.trim().split("\n");
		for (int i = lines.length - 1; i >= 0; i--) {
			String line = lines[i].trim();
			if (!line.isEmpty()) {
				try {
					return Double.parseDouble(line);
				} catch (NumberFormatException e) {
					// Not a number — try the next line up
				}
			}
		}
		return 0.0;
	}

	/**
	 * Parse a partial score from stdout when execution timed out. Since the
	 * tester's final score line may never be printed, we count the number of
	 * "Passed" lines as a fallback. Each "Passed" corresponds to score += 1 in the
	 * tester.
	 */
	private double parsePartialScore(String stdout) {
		if (stdout == null || stdout.isBlank()) {
			return 0.0;
		}

		// First, try to find a numeric score line (in case tester flushed it)
		double parsedScore = parseScore(stdout);
		if (parsedScore > 0) {
			return parsedScore;
		}

		// Fallback: count "Passed" occurrences
		long passedCount = stdout.lines().map(String::trim).filter(line -> line.equals("Passed")).count();

		return (double) passedCount;
	}

	/**
	 * Copy required class files for Q2 (DataException) from the template folder.
	 */
	private void copyQ2Dependencies(Path questionFolder, Path testerFilesDir) {
		try {
			// Template folder is at: is442-project-materials/RenameToYourUsername/Q2/
			Path templateFolder = testerFilesDir.getParent().resolve("RenameToYourUsername").resolve("Q2");

			if (!Files.isDirectory(templateFolder)) {
				LOGGER.fine("Template Q2 folder not found at: " + templateFolder);
				return;
			}

			// Copy DataException.class
			Path source = templateFolder.resolve("DataException.class");
			Path dest = questionFolder.resolve("DataException.class");
			if (Files.isRegularFile(source)) {
				Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
				LOGGER.fine("Copied DataException.class to Q2 folder");
			}

			// Copy data files (persons.txt, students.txt)
			String[] dataFiles = {"persons.txt", "students.txt"};
			for (String fileName : dataFiles) {
				Path dataSource = templateFolder.resolve(fileName);
				Path dataDest = questionFolder.resolve(fileName);
				if (Files.isRegularFile(dataSource)) {
					Files.copy(dataSource, dataDest, StandardCopyOption.REPLACE_EXISTING);
					LOGGER.fine("Copied " + fileName + " to Q2 folder");
				}
			}
		} catch (IOException e) {
			LOGGER.warning("Failed to copy Q2 dependencies: " + e.getMessage());
		}
	}

	/**
	 * Copy required class files for Q3 (Shape, Circle, Rectangle) from the template
	 * folder. Since the exam question states "Only Q3.java and ShapeComparator.java
	 * will be marked", students don't need to submit these dependency classes. The
	 * autograder provides them automatically.
	 */
	private void copyQ3Dependencies(Path questionFolder, Path testerFilesDir) {
		try {
			// Template folder is at: is442-project-materials/RenameToYourUsername/Q3/
			Path templateFolder = testerFilesDir.getParent().resolve("RenameToYourUsername").resolve("Q3");

			if (!Files.isDirectory(templateFolder)) {
				LOGGER.fine("Template Q3 folder not found at: " + templateFolder);
				return;
			}

			// Copy Shape.class, Circle.class, Rectangle.class
			String[] requiredClasses = {"Shape.class", "Circle.class", "Rectangle.class"};
			for (String className : requiredClasses) {
				Path source = templateFolder.resolve(className);
				Path dest = questionFolder.resolve(className);
				if (Files.isRegularFile(source)) {
					Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
					LOGGER.fine("Copied " + className + " to Q3 folder");
				}
			}
		} catch (IOException e) {
			LOGGER.warning("Failed to copy Q3 dependencies: " + e.getMessage());
		}
	}

	/**
	 * Remove tester source and compiled class files to leave the student folder
	 * clean.
	 */
	private void cleanupTester(Path questionFolder, String testerClassName) {
		try {
			Path testerJava = questionFolder.resolve(testerClassName + ".java");
			Path testerClass = questionFolder.resolve(testerClassName + ".class");
			Files.deleteIfExists(testerJava);
			Files.deleteIfExists(testerClass);
		} catch (IOException e) {
			// Cleanup failure is not critical
			LOGGER.fine("Cleanup warning: " + e.getMessage());
		}
	}

	private String truncate(String text, int maxLen) {
		if (text == null) {
			return "";
		}
		text = text.trim();
		return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
	}
}

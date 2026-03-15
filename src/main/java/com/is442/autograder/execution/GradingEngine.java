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
import com.is442.autograder.util.StringUtils;

/**
 * Orchestrates the grading flow for a single submission: For each question:
 * copy tester → compile → run → parse score.
 */
public class GradingEngine {

	private static final Logger LOGGER = Logger.getLogger(GradingEngine.class.getName());

	private final ProcessRunner processRunner;
	private final QuestionLogWriter questionLogWriter;
	private final ConsoleReporter consoleReporter;
	private final String templateFolderName;

	/**
	 * @param processRunner
	 *            runner used to invoke javac and java
	 * @param questionLogWriter
	 *            log writer for per-question output (may be {@code null})
	 * @param consoleReporter
	 *            reporter for console progress messages (may be {@code null})
	 * @param templateFolderName
	 *            name of the template folder inside the project-materials directory
	 *            that holds pre-compiled dependency class files
	 */
	public GradingEngine(ProcessRunner processRunner, QuestionLogWriter questionLogWriter,
			ConsoleReporter consoleReporter, String templateFolderName) {
		this.processRunner = processRunner;
		this.questionLogWriter = questionLogWriter;
		this.consoleReporter = consoleReporter;
		this.templateFolderName = templateFolderName;
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
			logWarning(questionId + "  │  Question folder not found");
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

		// 2.5. Copy any pre-compiled dependency files declared in the question config
		if (qc.getDependencyFolder() != null && !qc.getDependencyFiles().isEmpty()) {
			copyDependencies(questionFolder, testerFilesDir, qc);
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
				String singleLineError = cleanCompilerMessage(firstRelevantLine(error));
				String summaryLine = submission.getDisplayName() + "  │  " + questionId + "  │  Compile error: "
						+ singleLineError;
				logError(questionId + "  │  Compile error: " + singleLineError);
				appendErrorSummary(summaryLine);
				String filteredError = filterNoteLines(error);
				writeLogIfAvailable(submission, questionId, "compile-error", filteredError, "");
				submission.addAnomaly(new Anomaly(Anomaly.Type.COMPILATION_ERROR,
						"Compilation error in " + questionId + ": " + StringUtils.truncate(error, 200),
						Anomaly.Severity.ERROR, questionId));
				return QuestionResult.compilationFailure(questionId, qc.getMaxScore(),
						StringUtils.truncate(error, 500));
			}

			// 4. Run tester
			ProcessResult runResult = processRunner.run(questionFolder, qc.getTesterClassName());
			writeLogIfAvailable(submission, questionId, "run", runResult.getStdout(), runResult.getStderr());

			if (runResult.isTimedOut()) {
				LOGGER.warning(submission.getDisplayName() + " - " + questionId + ": execution timed out");
				String summaryLine = submission.getDisplayName() + "  │  " + questionId + "  │  Timed out";
				logWarning(questionId + "  │  Timed out");
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
				String singleLineError = cleanCompilerMessage(firstRelevantLine(error));
				String summaryLine = submission.getDisplayName() + "  │  " + questionId + "  │  Runtime error: "
						+ singleLineError;
				logError(questionId + "  │  Runtime error: " + singleLineError);
				appendErrorSummary(summaryLine);
				String filteredError = filterNoteLines(error);
				writeLogIfAvailable(submission, questionId, "runtime-error", filteredError, "");
				submission.addAnomaly(new Anomaly(Anomaly.Type.RUNTIME_ERROR,
						"Runtime error in " + questionId + ": " + StringUtils.truncate(error, 200),
						Anomaly.Severity.ERROR, questionId));
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

	private void logError(String message) {
		if (consoleReporter == null) {
			return;
		}
		consoleReporter.logError(message);
	}

	private void logRaw(String message) {
		if (consoleReporter == null) {
			return;
		}
		consoleReporter.logRaw(message);
	}

	/** Strips leading "Filename.java:NN: " prefix from javac/java output lines. */
	private String cleanCompilerMessage(String line) {
		if (line == null)
			return "";
		// e.g. "Q1a.java:23: error: reached end of file" → "error: reached end of file"
		return line.replaceFirst("^[^:]+\\.java:\\d+:\\s*", "");
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
	 * Copy pre-compiled dependency files declared in {@code qc} from the template
	 * folder into the student question folder so they are on the classpath during
	 * compilation and execution.
	 *
	 * <p>
	 * The source directory is resolved as:
	 * {@code <testerFilesDir>/../<templateFolderName>/<qc.dependencyFolder>/}
	 *
	 * @param questionFolder
	 *            destination directory (student's question folder)
	 * @param testerFilesDir
	 *            directory containing the tester .java files
	 * @param qc
	 *            question configuration supplying the dependency folder and file
	 *            list
	 */
	private void copyDependencies(Path questionFolder, Path testerFilesDir, QuestionConfig qc) {
		try {
			Path templateFolder = testerFilesDir.getParent().resolve(templateFolderName)
					.resolve(qc.getDependencyFolder());

			if (!Files.isDirectory(templateFolder)) {
				LOGGER.fine("Dependency folder not found at: " + templateFolder);
				return;
			}

			for (String fileName : qc.getDependencyFiles()) {
				Path source = templateFolder.resolve(fileName.trim());
				Path dest = questionFolder.resolve(fileName.trim());
				if (Files.isRegularFile(source)) {
					Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
					LOGGER.fine("Copied " + fileName + " to " + questionFolder.getFileName());
				} else {
					LOGGER.fine("Dependency file not found: " + source);
				}
			}
		} catch (IOException e) {
			LOGGER.warning("Failed to copy dependencies for " + qc.getQuestionId() + ": " + e.getMessage());
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

}

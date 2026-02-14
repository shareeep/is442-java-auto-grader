package com.is442.autograder.execution;

import com.is442.autograder.model.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.logging.Logger;

/**
 * Orchestrates the grading flow for a single submission:
 * For each question: copy tester → compile → run → parse score.
 */
public class GradingEngine {

    private static final Logger LOGGER = Logger.getLogger(GradingEngine.class.getName());

    private final ProcessRunner processRunner;

    public GradingEngine(ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    /**
     * Grade all questions for a single student submission.
     *
     * @param submissionRoot  path containing Q1/, Q2/, Q3/
     * @param testerFilesDir  path to the directory containing tester .java files
     * @param questionConfigs list of question configurations
     * @param submission      submission to add results and anomalies to
     */
    public void grade(Path submissionRoot, Path testerFilesDir,
            List<QuestionConfig> questionConfigs,
            StudentSubmission submission) {

        for (QuestionConfig qc : questionConfigs) {
            QuestionResult result = gradeQuestion(submissionRoot, testerFilesDir, qc, submission);
            submission.addResult(result);
        }
    }

    /**
     * Grade a single question: copy tester, compile, run, parse score.
     */
    private QuestionResult gradeQuestion(Path submissionRoot, Path testerFilesDir,
            QuestionConfig qc, StudentSubmission submission) {

        String questionId = qc.getQuestionId();
        Path questionFolder = submissionRoot.resolve(qc.getFolder());

        // 1. Check if question folder exists
        if (!Files.isDirectory(questionFolder)) {
            LOGGER.warning(submission.getDisplayName() + " - " + questionId +
                    ": question folder not found");
            return QuestionResult.missing(questionId, qc.getMaxScore());
        }

        // 2. Copy tester file into question folder
        Path testerSource = testerFilesDir.resolve(qc.getTesterClassName() + ".java");
        Path testerDest = questionFolder.resolve(qc.getTesterClassName() + ".java");

        try {
            if (!Files.isRegularFile(testerSource)) {
                LOGGER.warning("Tester file not found: " + testerSource);
                return QuestionResult.compilationFailure(questionId, qc.getMaxScore(),
                        "Tester file not found: " + testerSource.getFileName());
            }
            Files.copy(testerSource, testerDest, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            return QuestionResult.compilationFailure(questionId, qc.getMaxScore(),
                    "Failed to copy tester: " + e.getMessage());
        }

        try {
            // 3. Compile all .java files
            ProcessResult compileResult = processRunner.compile(questionFolder);

            if (!compileResult.isSuccess()) {
                String error = compileResult.getStderr().isEmpty()
                        ? compileResult.getStdout()
                        : compileResult.getStderr();
                LOGGER.warning(submission.getDisplayName() + " - " + questionId +
                        ": compilation failed");
                submission.addAnomaly(new Anomaly(
                        Anomaly.Type.COMPILATION_ERROR,
                        "Compilation error in " + questionId + ": " +
                                truncate(error, 200),
                        Anomaly.Severity.ERROR,
                        questionId));
                return QuestionResult.compilationFailure(questionId, qc.getMaxScore(),
                        truncate(error, 500));
            }

            // 4. Run tester
            ProcessResult runResult = processRunner.run(questionFolder, qc.getTesterClassName());

            if (runResult.isTimedOut()) {
                LOGGER.warning(submission.getDisplayName() + " - " + questionId +
                        ": execution timed out");
                // Parse partial score from stdout before the hang
                // Each tester prints "Passed" per successful test (score += 1 each)
                double partialScore = parsePartialScore(runResult.getStdout());
                submission.addAnomaly(new Anomaly(
                        Anomaly.Type.EXECUTION_TIMEOUT,
                        "Execution timed out for " + questionId +
                                (partialScore > 0 ? " (partial score: " + partialScore + ")" : ""),
                        Anomaly.Severity.ERROR,
                        questionId));
                return new QuestionResult(
                        questionId, partialScore, qc.getMaxScore(),
                        true, false,
                        runResult.getStdout(), "Execution timed out");
            }

            if (!runResult.isSuccess()) {
                String error = runResult.getStderr().isEmpty()
                        ? runResult.getStdout()
                        : runResult.getStderr();
                submission.addAnomaly(new Anomaly(
                        Anomaly.Type.RUNTIME_ERROR,
                        "Runtime error in " + questionId + ": " +
                                truncate(error, 200),
                        Anomaly.Severity.ERROR,
                        questionId));
            }

            // 5. Parse score from last line of stdout
            double score = parseScore(runResult.getStdout());

            return new QuestionResult(
                    questionId, score, qc.getMaxScore(),
                    true, true,
                    runResult.getStdout(), runResult.getStderr());

        } finally {
            // 6. Clean up: remove tester file and compiled classes
            cleanupTester(questionFolder, qc.getTesterClassName());
        }
    }

    /**
     * Parse the score from the last non-empty line of stdout.
     * The tester files print the numeric score as the very last line.
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
     * Parse a partial score from stdout when execution timed out.
     * Since the tester's final score line may never be printed,
     * we count the number of "Passed" lines as a fallback.
     * Each "Passed" corresponds to score += 1 in the tester.
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
        long passedCount = stdout.lines()
                .map(String::trim)
                .filter(line -> line.equals("Passed"))
                .count();

        return (double) passedCount;
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
        if (text == null)
            return "";
        text = text.trim();
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}

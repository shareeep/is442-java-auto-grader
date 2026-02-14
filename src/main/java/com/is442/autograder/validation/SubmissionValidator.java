package com.is442.autograder.validation;

import com.is442.autograder.model.Anomaly;
import com.is442.autograder.model.QuestionConfig;
import com.is442.autograder.model.StudentSubmission;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Validates submission structure and reports anomalies. Never terminates the
 * pipeline — all issues are recorded and grading continues.
 */
public class SubmissionValidator {

	private static final Pattern NAME_PATTERN = Pattern.compile("\\*\\s*Name\\s*:\\s*([^*\\n]+)",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("\\*\\s*Email\\s*ID\\s*:\\s*([\\w.]+)",
			Pattern.CASE_INSENSITIVE);

	/**
	 * Validate a submission's structure and file contents.
	 *
	 * @param submissionRoot
	 *            root containing Q1/, Q2/, Q3/
	 * @param questionConfigs
	 *            configured questions to check
	 * @param submission
	 *            submission object to add anomalies to
	 */
	public void validate(Path submissionRoot, List<QuestionConfig> questionConfigs, StudentSubmission submission) {

		// 1. Check for expected question folders
		Set<String> expectedFolders = questionConfigs.stream().map(QuestionConfig::getFolder)
				.collect(Collectors.toSet());

		for (String folder : expectedFolders) {
			Path folderPath = submissionRoot.resolve(folder);
			if (!Files.isDirectory(folderPath)) {
				submission.addAnomaly(new Anomaly(Anomaly.Type.MISSING_QUESTION_FOLDER,
						"Missing question folder: " + folder, Anomaly.Severity.ERROR));
			}
		}

		// 2. Check for required Java files in each question folder
		for (QuestionConfig qc : questionConfigs) {
			Path folderPath = submissionRoot.resolve(qc.getFolder());
			if (!Files.isDirectory(folderPath)) {
				continue; // already flagged above
			}

			// Derive expected filename from questionId (e.g. "Q1a" -> "Q1a.java")
			String expectedFile = qc.getQuestionId() + ".java";
			Path javaFilePath = folderPath.resolve(expectedFile);

			if (!Files.isRegularFile(javaFilePath)) {
				submission.addAnomaly(new Anomaly(Anomaly.Type.MISSING_JAVA_FILE,
						"Missing file: " + qc.getFolder() + "/" + expectedFile, Anomaly.Severity.ERROR,
						qc.getQuestionId()));
			} else {
				// 3. Check Java file headers
				validateJavaHeader(javaFilePath, qc.getQuestionId(), submission);
			}
		}
	}

	/**
	 * Check that a Java file has valid Name and Email ID in its header comment.
	 */
	private void validateJavaHeader(Path javaFile, String questionId, StudentSubmission submission) {
		try {
			String content = Files.readString(javaFile);
			String header = content.substring(0, Math.min(content.length(), 500));

			Matcher nameMatcher = NAME_PATTERN.matcher(header);
			Matcher emailMatcher = EMAIL_PATTERN.matcher(header);
			boolean hasName = nameMatcher.find();
			boolean hasEmail = emailMatcher.find();
			String nameVal = hasName ? nameMatcher.group(1).trim() : "";
			String emailVal = hasEmail ? emailMatcher.group(1).trim() : "";

			if (!hasName && !hasEmail) {
				submission.addAnomaly(new Anomaly(Anomaly.Type.MISSING_HEADER,
						"No Name/Email header in " + javaFile.getFileName(), Anomaly.Severity.WARNING, questionId));
			} else if (nameVal.isEmpty() || emailVal.isEmpty()) {
				submission.addAnomaly(new Anomaly(Anomaly.Type.INCOMPLETE_HEADER,
						"Incomplete header in " + javaFile.getFileName() + " (Name="
								+ (nameVal.isEmpty() ? "(missing)" : nameVal) + ", Email="
								+ (emailVal.isEmpty() ? "(missing)" : emailVal) + ")",
						Anomaly.Severity.WARNING, questionId));
			}
		} catch (IOException e) {
			submission.addAnomaly(new Anomaly(Anomaly.Type.MISSING_JAVA_FILE,
					"Could not read " + javaFile.getFileName() + ": " + e.getMessage(), Anomaly.Severity.ERROR,
					questionId));
		}
	}
}

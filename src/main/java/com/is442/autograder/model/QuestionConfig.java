package com.is442.autograder.model;

import java.util.Collections;
import java.util.List;

/**
 * Configuration for a single question (e.g. Q1a). Maps a question ID to its
 * folder, tester class, max score, and any dependency files that must be
 * supplied to the student folder before compilation.
 */
public class QuestionConfig {

	private final String questionId; // e.g. "Q1a"
	private final String folder; // e.g. "Q1"
	private final String testerClassName; // e.g. "Q1aTester"
	private final double maxScore; // e.g. 3.0
	private final String dependencyFolder; // sub-folder inside template dir, null if none
	private final List<String> dependencyFiles; // files to copy in, empty if none

	/**
	 * Full constructor.
	 *
	 * @param questionId
	 *            question identifier, e.g. {@code "Q1a"}
	 * @param folder
	 *            submission sub-folder, e.g. {@code "Q1"}
	 * @param testerClassName
	 *            tester class name without {@code .java}, e.g. {@code "Q1aTester"}
	 * @param maxScore
	 *            maximum achievable score
	 * @param dependencyFolder
	 *            sub-folder inside the template directory that holds pre-compiled
	 *            dependency files, or {@code null} if this question has no external
	 *            dependencies
	 * @param dependencyFiles
	 *            list of file names to copy from {@code dependencyFolder} into the
	 *            student question folder before compilation; may be empty
	 */
	public QuestionConfig(String questionId, String folder, String testerClassName, double maxScore,
			String dependencyFolder, List<String> dependencyFiles) {
		this.questionId = questionId;
		this.folder = folder;
		this.testerClassName = testerClassName;
		this.maxScore = maxScore;
		this.dependencyFolder = dependencyFolder;
		this.dependencyFiles = dependencyFiles == null
				? Collections.emptyList()
				: Collections.unmodifiableList(dependencyFiles);
	}

	/**
	 * Convenience constructor for questions with no external dependencies.
	 */
	public QuestionConfig(String questionId, String folder, String testerClassName, double maxScore) {
		this(questionId, folder, testerClassName, maxScore, null, Collections.emptyList());
	}

	public String getQuestionId() {
		return questionId;
	}

	public String getFolder() {
		return folder;
	}

	public String getTesterClassName() {
		return testerClassName;
	}

	public double getMaxScore() {
		return maxScore;
	}

	/**
	 * Sub-folder inside the template directory that contains pre-compiled
	 * dependency files for this question, or {@code null} if none are needed.
	 */
	public String getDependencyFolder() {
		return dependencyFolder;
	}

	/**
	 * Immutable list of file names to copy from the dependency folder into the
	 * student question folder before compilation. Empty when there are no
	 * dependencies.
	 */
	public List<String> getDependencyFiles() {
		return dependencyFiles;
	}

	@Override
	public String toString() {
		return questionId + " -> " + folder + "/" + testerClassName + " (max=" + maxScore + ")";
	}
}

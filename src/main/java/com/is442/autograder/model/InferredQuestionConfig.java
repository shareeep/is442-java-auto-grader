package com.is442.autograder.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single automatically inferred question configuration.
 */
public class InferredQuestionConfig {

	private String questionId;
	private String folder;
	private String tester;
	private double maxScore;
	private String dependencyFolder;
	private List<String> dependencyFiles = new ArrayList<>();

	// Optional flag to indicate it was generated as an empty placeholder
	private boolean inferredFromPdf;

	// Flag to indicate this is an implicit parent (e.g., Q1 created from Q1a/Q1b)
	private boolean isImplicitParent;

	public InferredQuestionConfig() {
	}

	public String getQuestionId() {
		return questionId;
	}

	public void setQuestionId(String questionId) {
		this.questionId = questionId;
	}

	public String getFolder() {
		return folder;
	}

	public void setFolder(String folder) {
		this.folder = folder;
	}

	public String getTester() {
		return tester;
	}

	public void setTester(String tester) {
		this.tester = tester;
	}

	public double getMaxScore() {
		return maxScore;
	}

	public void setMaxScore(double maxScore) {
		this.maxScore = maxScore;
	}

	public String getDependencyFolder() {
		return dependencyFolder;
	}

	public void setDependencyFolder(String dependencyFolder) {
		this.dependencyFolder = dependencyFolder;
	}

	public List<String> getDependencyFiles() {
		return dependencyFiles;
	}

	public void setDependencyFiles(List<String> dependencyFiles) {
		this.dependencyFiles = dependencyFiles;
	}

	public boolean isInferredFromPdf() {
		return inferredFromPdf;
	}

	public void setInferredFromPdf(boolean inferredFromPdf) {
		this.inferredFromPdf = inferredFromPdf;
	}

	public boolean isImplicitParent() {
		return isImplicitParent;
	}

	public void setImplicitParent(boolean implicitParent) {
		this.isImplicitParent = implicitParent;
	}
}

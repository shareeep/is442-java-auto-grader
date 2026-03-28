package com.is442.autograder.web.dto;

import java.util.List;

public class SaveRequest {

	private String examId;
	private String testerId;
	private String outputDir;
	private List<ResultEntry> results;
	private boolean updateMaxScores;

	public String getExamId() {
		return examId;
	}

	public void setExamId(String examId) {
		this.examId = examId;
	}

	public String getTesterId() {
		return testerId;
	}

	public void setTesterId(String testerId) {
		this.testerId = testerId;
	}

	public String getOutputDir() {
		return outputDir;
	}

	public void setOutputDir(String outputDir) {
		this.outputDir = outputDir;
	}

	public List<ResultEntry> getResults() {
		return results;
	}

	public void setResults(List<ResultEntry> results) {
		this.results = results;
	}

	public boolean isUpdateMaxScores() {
		return updateMaxScores;
	}

	public void setUpdateMaxScores(boolean updateMaxScores) {
		this.updateMaxScores = updateMaxScores;
	}

	public static class ResultEntry {

		private String questionId;
		private String testerClassName;
		private String generatedCode;
		private List<TestCaseEntry> cases;

		public String getQuestionId() {
			return questionId;
		}

		public void setQuestionId(String questionId) {
			this.questionId = questionId;
		}

		public String getTesterClassName() {
			return testerClassName;
		}

		public void setTesterClassName(String testerClassName) {
			this.testerClassName = testerClassName;
		}

		public String getGeneratedCode() {
			return generatedCode;
		}

		public void setGeneratedCode(String generatedCode) {
			this.generatedCode = generatedCode;
		}

		public List<TestCaseEntry> getCases() {
			return cases;
		}

		public void setCases(List<TestCaseEntry> cases) {
			this.cases = cases;
		}
	}

	public static class TestCaseEntry {

		private String description;
		private String inputArgs;
		private String expectedOutput;
		private double weight;

		public String getDescription() {
			return description;
		}

		public void setDescription(String description) {
			this.description = description;
		}

		public String getInputArgs() {
			return inputArgs;
		}

		public void setInputArgs(String inputArgs) {
			this.inputArgs = inputArgs;
		}

		public String getExpectedOutput() {
			return expectedOutput;
		}

		public void setExpectedOutput(String expectedOutput) {
			this.expectedOutput = expectedOutput;
		}

		public double getWeight() {
			return weight;
		}

		public void setWeight(double weight) {
			this.weight = weight;
		}
	}
}

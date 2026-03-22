package com.is442.autograder.web.dto;

public class QuestionSelection {

	private String questionId;
	private int numCases;

	public String getQuestionId() {
		return questionId;
	}

	public void setQuestionId(String questionId) {
		this.questionId = questionId;
	}

	public int getNumCases() {
		return numCases;
	}

	public void setNumCases(int numCases) {
		this.numCases = numCases;
	}
}

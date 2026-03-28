package com.is442.autograder.web.dto;

public class RefineRequest {

	private String examId;
	private String questionId;
	private String currentCode;
	private String refinementPrompt;

	public String getExamId() { return examId; }
	public void setExamId(String examId) { this.examId = examId; }

	public String getQuestionId() { return questionId; }
	public void setQuestionId(String questionId) { this.questionId = questionId; }

	public String getCurrentCode() { return currentCode; }
	public void setCurrentCode(String currentCode) { this.currentCode = currentCode; }

	public String getRefinementPrompt() { return refinementPrompt; }
	public void setRefinementPrompt(String refinementPrompt) { this.refinementPrompt = refinementPrompt; }
}

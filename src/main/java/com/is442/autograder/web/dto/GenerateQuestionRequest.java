package com.is442.autograder.web.dto;

public class GenerateQuestionRequest {

	private String examId;
	private String testerId;
	private String templateId;
	private String questionId;
	private int numCases;

	public String getExamId() { return examId; }
	public void setExamId(String examId) { this.examId = examId; }

	public String getTesterId() { return testerId; }
	public void setTesterId(String testerId) { this.testerId = testerId; }

	public String getTemplateId() { return templateId; }
	public void setTemplateId(String templateId) { this.templateId = templateId; }

	public String getQuestionId() { return questionId; }
	public void setQuestionId(String questionId) { this.questionId = questionId; }

	public int getNumCases() { return numCases; }
	public void setNumCases(int numCases) { this.numCases = numCases; }
}

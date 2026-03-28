package com.is442.autograder.web.dto;

public class RecommendRequest {

	private String examId;
	private String questionId;
	private String testerId;

	public String getExamId() { return examId; }
	public void setExamId(String examId) { this.examId = examId; }

	public String getQuestionId() { return questionId; }
	public void setQuestionId(String questionId) { this.questionId = questionId; }

	public String getTesterId() { return testerId; }
	public void setTesterId(String testerId) { this.testerId = testerId; }
}

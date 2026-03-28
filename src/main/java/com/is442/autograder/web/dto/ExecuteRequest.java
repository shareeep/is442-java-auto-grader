package com.is442.autograder.web.dto;

import com.is442.autograder.model.InferredQuestionConfig;

public class ExecuteRequest {

	private String examId;
	private String testerId;
	private String templateId;
	private int numCases;
	private InferredQuestionConfig question;

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

	public String getTemplateId() {
		return templateId;
	}
	public void setTemplateId(String templateId) {
		this.templateId = templateId;
	}

	public int getNumCases() {
		return numCases;
	}
	public void setNumCases(int numCases) {
		this.numCases = numCases;
	}

	public InferredQuestionConfig getQuestion() {
		return question;
	}
	public void setQuestion(InferredQuestionConfig question) {
		this.question = question;
	}
}

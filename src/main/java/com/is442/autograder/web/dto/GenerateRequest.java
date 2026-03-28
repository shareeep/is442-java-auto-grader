package com.is442.autograder.web.dto;

import java.util.List;

public class GenerateRequest {

	private String examId;
	private String testerId;
	private String templateId;
	private List<QuestionSelection> questions;

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

	public List<QuestionSelection> getQuestions() {
		return questions;
	}

	public void setQuestions(List<QuestionSelection> questions) {
		this.questions = questions;
	}
}

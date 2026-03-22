package com.is442.autograder.web.dto;

import java.util.List;

public class GenerateRequest {

	private String examId;
	private String testersDir;
	private String templateDir;
	private List<QuestionSelection> questions;

	public String getExamId() {
		return examId;
	}

	public void setExamId(String examId) {
		this.examId = examId;
	}

	public String getTestersDir() {
		return testersDir;
	}

	public void setTestersDir(String testersDir) {
		this.testersDir = testersDir;
	}

	public String getTemplateDir() {
		return templateDir;
	}

	public void setTemplateDir(String templateDir) {
		this.templateDir = templateDir;
	}

	public List<QuestionSelection> getQuestions() {
		return questions;
	}

	public void setQuestions(List<QuestionSelection> questions) {
		this.questions = questions;
	}
}

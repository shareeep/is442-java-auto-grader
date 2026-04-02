package com.is442.autograder.web.dto;

import com.is442.autograder.model.InferredQuestionConfig;

import java.util.List;

public class ExecuteRequest {

	private String examId;
	private String testerId;
	private String templateId;
	private int numCases;
	private InferredQuestionConfig question;
	private List<String> conceptsToCover;
	private List<String> customSuggestions;

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

	public List<String> getConceptsToCover() {
		return conceptsToCover;
	}
	public void setConceptsToCover(List<String> conceptsToCover) {
		this.conceptsToCover = conceptsToCover;
	}

	public List<String> getCustomSuggestions() {
		return customSuggestions;
	}
	public void setCustomSuggestions(List<String> customSuggestions) {
		this.customSuggestions = customSuggestions;
	}
}

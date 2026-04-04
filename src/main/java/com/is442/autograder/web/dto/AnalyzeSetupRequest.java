package com.is442.autograder.web.dto;

public class AnalyzeSetupRequest {

	private String templateId;
	private String testerId;

	public String getTemplateId() {
		return templateId;
	}

	public void setTemplateId(String templateId) {
		this.templateId = templateId;
	}

	public String getTesterId() {
		return testerId;
	}

	public void setTesterId(String testerId) {
		this.testerId = testerId;
	}
}

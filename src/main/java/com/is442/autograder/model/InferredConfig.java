package com.is442.autograder.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The top-level payload representing the dynamically inferred configuration for
 * an assessment. Includes questions and any detected conflicts.
 */
public class InferredConfig {

	private String assessmentName;
	private String templateFolder;
	private List<InferredQuestionConfig> questions = new ArrayList<>();
	private List<ConfigConflict> conflicts = new ArrayList<>();

	public InferredConfig() {
	}

	public String getAssessmentName() {
		return assessmentName;
	}

	public void setAssessmentName(String assessmentName) {
		this.assessmentName = assessmentName;
	}

	public String getTemplateFolder() {
		return templateFolder;
	}

	public void setTemplateFolder(String templateFolder) {
		this.templateFolder = templateFolder;
	}

	public List<InferredQuestionConfig> getQuestions() {
		return questions;
	}

	public void setQuestions(List<InferredQuestionConfig> questions) {
		this.questions = questions;
	}

	public List<ConfigConflict> getConflicts() {
		return conflicts;
	}

	public void setConflicts(List<ConfigConflict> conflicts) {
		this.conflicts = conflicts;
	}

	public void addConflict(ConfigConflict conflict) {
		this.conflicts.add(conflict);
	}
}

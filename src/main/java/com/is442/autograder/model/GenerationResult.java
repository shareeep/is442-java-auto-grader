package com.is442.autograder.model;

import java.util.List;

/**
 * Result of test case generation for a single question.
 */
public class GenerationResult {

	private final String questionId;
	private final List<GeneratedTestCase> cases;
	private final boolean compiledOk;
	private final String compileErrors;
	private final String generatedCode;

	public GenerationResult(String questionId, List<GeneratedTestCase> cases, boolean compiledOk, String compileErrors,
			String generatedCode) {
		this.questionId = questionId;
		this.cases = cases;
		this.compiledOk = compiledOk;
		this.compileErrors = compileErrors;
		this.generatedCode = generatedCode;
	}

	public String getQuestionId() {
		return questionId;
	}

	public List<GeneratedTestCase> getCases() {
		return cases;
	}

	public boolean isCompiledOk() {
		return compiledOk;
	}

	public String getCompileErrors() {
		return compileErrors;
	}

	public String getGeneratedCode() {
		return generatedCode;
	}
}

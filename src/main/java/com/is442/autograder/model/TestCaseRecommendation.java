package com.is442.autograder.model;

import java.util.List;

/**
 * AI-produced recommendation for how many test cases to generate and which
 * concepts to cover for a given exam question.
 *
 * @param questionId
 *            question identifier, e.g. "Q1a"
 * @param recommendedCount
 *            suggested number of NEW test cases
 * @param conceptsToCover
 *            list of concepts/scenarios to exercise
 * @param existingCount
 *            number of existing test cases
 * @param existingConcepts
 *            concepts already covered by existing tests
 * @param rationale
 *            brief explanation of the recommendation
 */
public record TestCaseRecommendation(String questionId, int recommendedCount, List<String> conceptsToCover,
		int existingCount, List<String> existingConcepts, String rationale) {
	public TestCaseRecommendation(String questionId, int recommendedCount, List<String> conceptsToCover) {
		this(questionId, recommendedCount, conceptsToCover, 0, List.of(), "");
	}
}

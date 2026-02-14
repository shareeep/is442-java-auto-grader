package com.is442.autograder.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StudentSubmissionTest {

	@Test
	void newSubmissionHasZeroScore() {
		StudentSubmission sub = new StudentSubmission("test.zip");
		assertEquals(0.0, sub.getTotalScore());
	}

	@Test
	void newSubmissionHasNoAnomalies() {
		StudentSubmission sub = new StudentSubmission("test.zip");
		assertFalse(sub.hasAnomalies());
		assertTrue(sub.getAnomalies().isEmpty());
	}

	@Test
	void displayNameFallsBackToZipName() {
		StudentSubmission sub = new StudentSubmission("ping.lee.2023.zip");
		// No username set, should fall back to zip name
		assertEquals("ping.lee.2023", sub.getDisplayName());
	}

	@Test
	void displayNamePrefersUsername() {
		StudentSubmission sub = new StudentSubmission("test.zip");
		sub.setUsername("ping.lee.2023");
		assertEquals("ping.lee.2023", sub.getDisplayName());
	}

	@Test
	void orgDefinedIdDefaultsToNull() {
		StudentSubmission sub = new StudentSubmission("test.zip");
		assertNull(sub.getOrgDefinedId());
	}

	@Test
	void scoreAccumulates() {
		StudentSubmission sub = new StudentSubmission("test.zip");
		sub.addResult(new QuestionResult("Q1a", 3.0, 3.0, true, true, "", ""));
		sub.addResult(new QuestionResult("Q1b", 2.0, 3.0, true, true, "", ""));

		assertEquals(5.0, sub.getTotalScore());
		assertEquals(2, sub.getResults().size());
	}

	@Test
	void anomalyTracking() {
		StudentSubmission sub = new StudentSubmission("test.zip");
		sub.addAnomaly(new Anomaly(Anomaly.Type.MISSING_HEADER, "No header", Anomaly.Severity.WARNING));

		assertTrue(sub.hasAnomalies());
		assertEquals(1, sub.getAnomalies().size());
		assertEquals("No header", sub.getAnomalies().get(0).getDescription());
	}
}

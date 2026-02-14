package com.is442.autograder.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.is442.autograder.model.QuestionConfig;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class AppConfigTest {

	@Test
	void loadsConfigProperties() throws IOException {
		AppConfig config = new AppConfig();
		assertNotNull(config);
	}

	@Test
	void timeoutIsPositive() throws IOException {
		AppConfig config = new AppConfig();
		assertTrue(config.getTimeoutSeconds() > 0, "Timeout should be > 0");
	}

	@Test
	void questionConfigsAreLoaded() throws IOException {
		AppConfig config = new AppConfig();
		List<QuestionConfig> configs = config.getQuestionConfigs();

		assertFalse(configs.isEmpty(), "Should have at least one question config");
		assertEquals(5, configs.size(), "Should have 5 questions (Q1a, Q1b, Q2a, Q2b, Q3)");
	}

	@Test
	void questionConfigHasCorrectFields() throws IOException {
		AppConfig config = new AppConfig();
		QuestionConfig first = config.getQuestionConfigs().get(0);

		assertEquals("Q1a", first.getQuestionId());
		assertEquals("Q1", first.getFolder());
		assertEquals("Q1aTester", first.getTesterClassName());
		assertEquals(3.0, first.getMaxScore());
	}
}

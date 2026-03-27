package com.is442.autograder.generation;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.is442.autograder.model.InferredConfig;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;

class ConfigInferenceServiceTest {

	private static final Logger logger = LoggerFactory.getLogger(ConfigInferenceServiceTest.class);

	private ConfigInferenceService service;

	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() {
		service = new ConfigInferenceService();
	}

	@Test
	void testIS442SampleInference() throws IOException {
		// Read the actual PDF markdown
		Path pdfMd = Path.of("pdf-diagnostic/output.md");
		if (!Files.exists(pdfMd)) {
			logger.warn("Skipping test - pdf-diagnostic/output.md not found");
			return;
		}
		String markdown = Files.readString(pdfMd);

		// Create mock template directory with Q1, Q2, Q3 folders
		Path templateDir = tempDir.resolve("RenameToYourUsername");
		Files.createDirectories(templateDir.resolve("Q1"));
		Files.createDirectories(templateDir.resolve("Q2"));
		Files.createDirectories(templateDir.resolve("Q3"));

		// Create mock testers directory
		Path testersDir = tempDir.resolve("testers");
		Files.createDirectories(testersDir);

		// Create tester files
		Files.writeString(testersDir.resolve("Q1aTester.java"),
				"public class Q1aTester { int tcNum = 0; @Test void test1() { tcNum++; } }");
		Files.writeString(testersDir.resolve("Q1bTester.java"),
				"public class Q1bTester { int tcNum = 0; @Test void test1() { tcNum++; } }");
		Files.writeString(testersDir.resolve("Q2aTester.java"),
				"public class Q2aTester { int tcNum = 0; @Test void test1() { tcNum++; } }");
		Files.writeString(testersDir.resolve("Q2bTester.java"),
				"public class Q2bTester { int tcNum = 0; @Test void test1() { tcNum++; } }");
		Files.writeString(testersDir.resolve("Q3Tester.java"),
				"public class Q3Tester { int tcNum = 0; @Test void test1() { tcNum++; } @Test void test2() { tcNum++; } }");

		// Run inference
		InferredConfig config = service.inferConfig(markdown, templateDir.toString(), testersDir.toString());

		// Assertions
		logger.info("Questions found: {}", config.getQuestions().stream().map(q -> q.getQuestionId()).toList());
		logger.info("Conflicts: {}", config.getConflicts());

		// Should find Q1, Q2, Q3 from PDF
		assertTrue(config.getQuestions().stream().anyMatch(q -> q.getQuestionId().equals("Q1")), "Should find Q1");
		assertTrue(config.getQuestions().stream().anyMatch(q -> q.getQuestionId().equals("Q2")), "Should find Q2");
		assertTrue(config.getQuestions().stream().anyMatch(q -> q.getQuestionId().equals("Q3")), "Should find Q3");

		// Should find Q1a, Q1b, Q2a, Q2b from testers
		assertTrue(config.getQuestions().stream().anyMatch(q -> q.getQuestionId().equals("Q1a")), "Should find Q1a");
		assertTrue(config.getQuestions().stream().anyMatch(q -> q.getQuestionId().equals("Q1b")), "Should find Q1b");
		assertTrue(config.getQuestions().stream().anyMatch(q -> q.getQuestionId().equals("Q2a")), "Should find Q2a");
		assertTrue(config.getQuestions().stream().anyMatch(q -> q.getQuestionId().equals("Q2b")), "Should find Q2b");

		// Q1 and Q2 should NOT have MISSING_TESTER (they have sub-question testers)
		var q1 = config.getQuestions().stream().filter(q -> q.getQuestionId().equals("Q1")).findFirst();
		var q2 = config.getQuestions().stream().filter(q -> q.getQuestionId().equals("Q2")).findFirst();

		assertTrue(q1.isPresent(), "Q1 should exist");
		assertTrue(q2.isPresent(), "Q2 should exist");
		assertNotNull(q1.get().getFolder(), "Q1 should have folder");
		assertNotNull(q2.get().getFolder(), "Q2 should have folder");

		// Should have no conflicts (Q1/Q2 skip MISSING_TESTER because they have
		// sub-testers)
		assertEquals(0, config.getConflicts().size(), "Should have no conflicts");

		logger.info("Test PASSED!");
	}

	@Test
	void testQuestionExtraction() {
		String markdown = """
				## Question 1

				Some content...

				## Question 2

				More content...

				## Question 1a

				Sub question...
				""";

		InferredConfig config = service.inferConfig(markdown, null, null);

		assertEquals(3, config.getQuestions().size());
		assertTrue(config.getQuestions().stream().anyMatch(q -> q.getQuestionId().equals("Q1")));
		assertTrue(config.getQuestions().stream().anyMatch(q -> q.getQuestionId().equals("Q2")));
		assertTrue(config.getQuestions().stream().anyMatch(q -> q.getQuestionId().equals("Q1a")));
	}

	@Test
	void testDoesNotDetectRandomNumbers() {
		String markdown = """
				## Question 1

				[10 marks]

				## Question 2

				Section 3
				""";

		InferredConfig config = service.inferConfig(markdown, null, null);

		// Should only find Q1 and Q2, NOT Q3, Q10 etc
		assertEquals(2, config.getQuestions().size());
		assertTrue(config.getQuestions().stream().anyMatch(q -> q.getQuestionId().equals("Q1")));
		assertTrue(config.getQuestions().stream().anyMatch(q -> q.getQuestionId().equals("Q2")));
	}
}

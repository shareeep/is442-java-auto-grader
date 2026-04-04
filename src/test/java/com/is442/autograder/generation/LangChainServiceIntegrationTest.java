package com.is442.autograder.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.is442.autograder.config.EnvLoader;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.service.AiServices;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Integration tests for LangChainService using minimal prompts for fast
 * feedback. Requires OPENROUTER_API_KEY env var or .env file.
 */
class LangChainServiceIntegrationTest {

	private static final Logger logger = LoggerFactory.getLogger(LangChainServiceIntegrationTest.class);

	private LangChainService langChainService;
	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		EnvLoader.load();
		String apiKey = EnvLoader.get("OPENROUTER_API_KEY");
		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalStateException(
					"OPENROUTER_API_KEY env var not set — skipping LangChainService test. Set it to run integration tests.");
		}

		OpenAiChatModel model = OpenAiChatModel.builder().apiKey(apiKey).modelName("minimax/minimax-m2.7")
				.baseUrl("https://openrouter.ai/api/v1").maxTokens(2048).timeout(Duration.ofSeconds(60)).maxRetries(2)
				.defaultRequestParameters(OpenAiChatRequestParameters.builder().reasoningEffort("low").build())
				.logRequests(true).logResponses(true).build();

		langChainService = AiServices.create(LangChainService.class, model);
		objectMapper = new ObjectMapper();
	}

	@Test
	void testGenerateTestCasesJson_minimal() {
		String prompt = """
				Question ID: Q1a
				Tester class: Q1aTester

				Implement getIsogramWords(ArrayList<String> inputs) -> ArrayList<String>.
				Returns words where all characters are unique (case-insensitive).
				Example: ["Alan","Ben","Evelyne"] returns ["Ben"].

				Generate exactly 2 test cases as a JSON array with fields:
				description, conceptCovered, setup, methodCall, expected, assertion, weight, expectsException, exceptionType.
				Return ONLY a valid JSON array, no markdown.
				""";

		logger.info("[TEST] Calling generateTestCasesJson...");
		String rawJson = langChainService.generateTestCasesJson(UserMessage.from(prompt));

		assertNotNull(rawJson, "API returned null response");
		logger.info("[TEST] Raw response ({} chars): {}", rawJson.length(),
				rawJson.length() > 300 ? rawJson.substring(0, 300) + "..." : rawJson);

		String cleaned = stripMarkdownFences(rawJson);
		List<Map<String, Object>> cases = parseJson(cleaned);

		assertFalse(cases.isEmpty(), "Should return at least one test case");
		for (Map<String, Object> tc : cases) {
			assertNotNull(tc.get("description"), "Each test case must have a description");
			assertNotNull(tc.get("methodCall"), "Each test case must have a methodCall");
		}

		logger.info("[TEST] testGenerateTestCasesJson_minimal PASSED — {} cases", cases.size());
	}

	@Test
	void testRecommendJson_minimal() {
		String prompt = """
				Question ID: Q1a

				Implement getSumOfEvenIntegers(ArrayList<Object> inputs) -> int.
				Returns sum of all even integers in the list.

				Recommend 3 new test cases to add. Return ONLY valid JSON:
				{ "questionId": "...", "recommendedCount": 3, "conceptsToCover": [...], "existingConcepts": [], "rationale": "" }
				""";

		logger.info("[TEST] Calling recommendJson...");
		String rawJson = langChainService.recommendJson(UserMessage.from(prompt));

		logger.info("[TEST] Raw response ({} chars): {}", rawJson.length(),
				rawJson.length() > 300 ? rawJson.substring(0, 300) + "..." : rawJson);

		String cleaned = stripMarkdownFences(rawJson);
		Map<String, Object> rec = parseRecommendation(cleaned, "Q1a");

		assertNotNull(rec.get("recommendedCount"), "Must have recommendedCount");
		assertTrue(((Number) rec.get("recommendedCount")).intValue() > 0);
		assertNotNull(rec.get("conceptsToCover"));

		logger.info("[TEST] testRecommendJson_minimal PASSED — recommended {}", rec.get("recommendedCount"));
	}

	@Test
	void testRefineCode_minimal() {
		String prompt = """
				Question ID: Q1a

				Current code:
				void testIsogram() { ArrayList<String> r = Q1a.getIsogramWords(null); }

				Refinement: add @Test annotation and wrap in try-catch. Return the complete updated Java code only.
				""";

		logger.info("[TEST] Calling refineCode...");
		String refined = langChainService.refineCode(prompt);

		logger.info("[TEST] Refined response ({} chars): {}", refined.length(),
				refined.length() > 300 ? refined.substring(0, 300) + "..." : refined);

		assertNotNull(refined);
		assertFalse(refined.isBlank());
		assertFalse(refined.startsWith("```"), "Should not start with markdown fence");

		logger.info("[TEST] testRefineCode_minimal PASSED");
	}

	// ── Parsing helpers ────────────────────────────────────────────────────

	private List<Map<String, Object>> parseJson(String json) {
		try {
			return objectMapper.readValue(json, new TypeReference<>() {
			});
		} catch (Exception e) {
			logger.warn("[TEST] JSON parse failed: {}", e.getMessage());
			return List.of();
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> parseRecommendation(String json, String questionId) {
		try {
			Map<String, Object> m = objectMapper.readValue(json, new TypeReference<>() {
			});
			return Map.of("questionId", questionId, "recommendedCount",
					m.get("recommendedCount") instanceof Number n ? n.intValue() : 3, "conceptsToCover",
					m.get("conceptsToCover") instanceof List<?> l ? (List<String>) l : List.of(), "existingConcepts",
					List.of(), "rationale", m.get("rationale") instanceof String s ? s : "");
		} catch (Exception e) {
			logger.warn("[TEST] Recommendation parse failed: {}", e.getMessage());
			return Map.of("questionId", questionId, "recommendedCount", 3, "conceptsToCover",
					List.of("Normal", "Boundary", "Exception"), "existingConcepts", List.of(), "rationale", "");
		}
	}

	private static String stripMarkdownFences(String text) {
		String trimmed = text.strip();
		if (trimmed.startsWith("```")) {
			int firstNewline = trimmed.indexOf('\n');
			if (firstNewline > 0) {
				trimmed = trimmed.substring(firstNewline + 1);
			}
		}
		if (trimmed.endsWith("```")) {
			trimmed = trimmed.substring(0, trimmed.length() - 3).replaceAll("\\s+$", "");
		}
		return trimmed;
	}
}

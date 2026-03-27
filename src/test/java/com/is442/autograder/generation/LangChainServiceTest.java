package com.is442.autograder.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Fast unit tests for the JSON parsing logic used by LangChainService. No API
 * calls — uses hardcoded strings to verify parsing and stripping logic.
 */
class LangChainServiceTest {

	private static final Logger logger = LoggerFactory.getLogger(LangChainServiceTest.class);

	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
	}

	// ── generateTestCasesJson parsing ──────────────────────────────────────

	@Test
	void testParseStructuredCases_cleanJson() throws Exception {
		String json = """
				[
				  {
				    "description": "Normal: single isogram word",
				    "conceptCovered": "Normal path",
				    "setup": "ArrayList<String> inputs = new ArrayList<>(List.of(\\"hello\\"));",
				    "methodCall": "Q1a.getIsogramWords(inputs)",
				    "expected": "[hello]",
				    "assertion": "result.size() == 1 && result.get(0).equals(\\"hello\\")",
				    "weight": 1.0,
				    "expectsException": false,
				    "exceptionType": null
				  },
				  {
				    "description": "Boundary: word with repeated character",
				    "conceptCovered": "Boundary: repeated chars",
				    "setup": null,
				    "methodCall": "Q1a.getIsogramWords(new ArrayList<>(List.of(\\"apple\\")))",
				    "expected": "[]",
				    "assertion": "result.isEmpty()",
				    "weight": 1.0,
				    "expectsException": false,
				    "exceptionType": null
				  }
				]
				""";

		List<Map<String, Object>> cases = parseStructuredCases(json);
		assertEquals(2, cases.size());
		assertEquals("Normal: single isogram word", cases.get(0).get("description"));
		assertEquals("Q1a.getIsogramWords(inputs)", cases.get(0).get("methodCall"));
		assertEquals(false, cases.get(1).get("expectsException"));
		assertEquals(1.0, ((Number) cases.get(1).get("weight")).doubleValue());
		logger.info("[TEST] testParseStructuredCases_cleanJson PASSED");
	}

	@Test
	void testParseStructuredCases_withMarkdownFences() throws Exception {
		String json = """
				```json
				[
				  {
				    "description": "Test with exception",
				    "conceptCovered": "Exception handling",
				    "setup": null,
				    "methodCall": "Q1b.getSumOfEvenIntegers(null)",
				    "expected": "0",
				    "assertion": null,
				    "weight": 1.0,
				    "expectsException": true,
				    "exceptionType": "NullPointerException"
				  }
				]
				```
				""";

		List<Map<String, Object>> cases = parseStructuredCases(json);
		assertEquals(1, cases.size());
		assertEquals("Test with exception", cases.get(0).get("description"));
		assertEquals(true, cases.get(0).get("expectsException"));
		assertEquals("NullPointerException", cases.get(0).get("exceptionType"));
		logger.info("[TEST] testParseStructuredCases_withMarkdownFences PASSED");
	}

	@Test
	void testParseStructuredCases_tripleBacktickNoLang() throws Exception {
		String json = """
				```
				[
				  {
				    "description": "No-lang fence test",
				    "conceptCovered": "Normal",
				    "setup": null,
				    "methodCall": "Q1a.getIsogramWords(inputs)",
				    "expected": "[]",
				    "assertion": "result.isEmpty()",
				    "weight": 1.0,
				    "expectsException": false,
				    "exceptionType": null
				  }
				]
				```
				""";

		List<Map<String, Object>> cases = parseStructuredCases(json);
		assertEquals(1, cases.size());
		assertEquals("No-lang fence test", cases.get(0).get("description"));
		logger.info("[TEST] testParseStructuredCases_tripleBacktickNoLang PASSED");
	}

	@Test
	void testParseStructuredCases_minimalFields() throws Exception {
		String json = """
				[
				  {
				    "description": "Minimal test case",
				    "conceptCovered": "Basic",
				    "setup": "",
				    "methodCall": "Test.method()",
				    "expected": "null",
				    "assertion": "",
				    "weight": 2.0,
				    "expectsException": false,
				    "exceptionType": null
				  }
				]
				""";

		List<Map<String, Object>> cases = parseStructuredCases(json);
		assertEquals(1, cases.size());
		assertEquals(2.0, ((Number) cases.get(0).get("weight")).doubleValue());
		logger.info("[TEST] testParseStructuredCases_minimalFields PASSED");
	}

	@Test
	void testParseStructuredCases_invalidJson_returnsEmpty() {
		String json = "not valid json at all";
		List<Map<String, Object>> cases = parseStructuredCases(json);
		assertNotNull(cases);
		logger.info("[TEST] testParseStructuredCases_invalidJson_returnsEmpty PASSED — returned {} cases",
				cases.size());
	}

	// ── recommendJson parsing ──────────────────────────────────────────────

	@Test
	void testParseRecommendation_cleanJson() throws Exception {
		String json = """
				{
				  "questionId": "Q1a",
				  "existingConcepts": ["Normal: valid input", "Boundary: single element"],
				  "recommendedCount": 3,
				  "conceptsToCover": ["Boundary: empty list", "Exception: null input"],
				  "rationale": "Need to cover null and empty edge cases"
				}
				""";

		Map<String, Object> rec = parseRecommendation(json, "Q1a", 2);
		assertEquals("Q1a", rec.get("questionId"));
		assertEquals(3, ((Number) rec.get("recommendedCount")).intValue());
		assertEquals(2, ((Number) rec.get("existingCount")).intValue());
		assertEquals(2, ((List<?>) rec.get("conceptsToCover")).size());
		assertEquals(2, ((List<?>) rec.get("existingConcepts")).size());
		logger.info("[TEST] testParseRecommendation_cleanJson PASSED");
	}

	@Test
	void testParseRecommendation_withMarkdownFences() throws Exception {
		String json = """
				```json
				{
				  "questionId": "Q1b",
				  "existingConcepts": [],
				  "recommendedCount": 5,
				  "conceptsToCover": ["Normal: valid integers", "Boundary: zero", "Exception: null list"],
				  "rationale": "Full coverage needed"
				}
				```
				""";

		Map<String, Object> rec = parseRecommendation(json, "Q1b", 0);
		assertEquals(5, ((Number) rec.get("recommendedCount")).intValue());
		assertEquals(3, ((List<?>) rec.get("conceptsToCover")).size());
		logger.info("[TEST] testParseRecommendation_withMarkdownFences PASSED");
	}

	@Test
	void testParseRecommendation_invalidJson_returnsDefaults() {
		Map<String, Object> rec = parseRecommendation("invalid", "Q99", 1);
		assertEquals("Q99", rec.get("questionId"));
		assertEquals(3, ((Number) rec.get("recommendedCount")).intValue());
		logger.info("[TEST] testParseRecommendation_invalidJson_returnsDefaults PASSED");
	}

	// ── stripMarkdownFences ────────────────────────────────────────────────

	@Test
	void testStripMarkdownFences_noFences() {
		assertEquals("plain json", stripMarkdownFences("plain json"));
	}

	@Test
	void testStripMarkdownFences_jsonWithLang() {
		assertEquals("[1,2,3]", stripMarkdownFences("```json\n[1,2,3]\n```"));
	}

	@Test
	void testStripMarkdownFences_jsonNoLang() {
		assertEquals("[1,2,3]", stripMarkdownFences("```\n[1,2,3]\n```"));
	}

	@Test
	void testStripMarkdownFences_partialJson() {
		assertEquals("[1,2,3]", stripMarkdownFences("[1,2,3]\n```"));
	}

	@Test
	void testStripMarkdownFences_whitespace() {
		assertEquals("  trimmed", stripMarkdownFences("```json\n  trimmed  \n```"));
	}

	// ── Parsing helpers (mirrors TestGenerationService logic) ─────────────────

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> parseStructuredCases(String json) {
		try {
			String cleaned = stripMarkdownFences(json);
			return objectMapper.readValue(cleaned, new TypeReference<>() {
			});
		} catch (Exception e) {
			logger.warn("[TEST] JSON parse failed: {}", e.getMessage());
			return List.of();
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> parseRecommendation(String json, String questionId, int existingCaseCount) {
		try {
			String cleaned = stripMarkdownFences(json);
			Map<String, Object> m = objectMapper.readValue(cleaned, new TypeReference<>() {
			});
			int count = m.get("recommendedCount") instanceof Number n ? n.intValue() : 3;
			List<String> concepts = m.get("conceptsToCover") instanceof List<?> l ? (List<String>) l : List.of();
			List<String> existingConcepts = m.get("existingConcepts") instanceof List<?> l
					? (List<String>) l
					: List.of();
			String rationale = m.get("rationale") instanceof String s ? s : "";

			return Map.of("questionId", questionId, "recommendedCount", count, "conceptsToCover", concepts,
					"existingCount", existingCaseCount, "existingConcepts", existingConcepts, "rationale", rationale);
		} catch (Exception e) {
			logger.warn("[TEST] Recommendation parse failed: {}", e.getMessage());
			return Map.of("questionId", questionId, "recommendedCount", 3, "conceptsToCover",
					List.of("Normal: valid input", "Boundary: edge case", "Exception: error handling"), "existingCount",
					existingCaseCount, "existingConcepts", List.of(), "rationale", "");
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
			trimmed = trimmed.substring(0, trimmed.length() - 3).stripTrailing();
		}
		return trimmed;
	}
}

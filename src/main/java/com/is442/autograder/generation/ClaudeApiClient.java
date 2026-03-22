package com.is442.autograder.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.is442.autograder.config.AppConfig;
import com.is442.autograder.config.EnvLoader;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Calls the OpenRouter chat completions API to generate Java test case code.
 * API key is read from the OPENROUTER_API_KEY environment variable.
 */
public class ClaudeApiClient {

	private static final String API_URL = "https://openrouter.ai/api/v1/chat/completions";

	private final AppConfig config;
	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;

	public ClaudeApiClient(AppConfig config) {
		this.config = config;
		this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
		this.objectMapper = new ObjectMapper();
	}

	/**
	 * Ask the LLM to generate Java test case code blocks.
	 *
	 * @param questionId
	 *            question identifier, e.g. "Q1a"
	 * @param testerClassName
	 *            tester class name, e.g. "Q1aTester"
	 * @param examContext
	 *            question text extracted from the exam PDF
	 * @param existingTesterCode
	 *            existing tester file content (may be null/empty)
	 * @param numCases
	 *            number of new test cases to generate
	 * @return raw Java code string containing the generated test case blocks
	 */
	public String generateTesterCode(String questionId, String testerClassName, String examContext,
			String existingTesterCode, int numCases, String additionalContext)
			throws IOException, InterruptedException {

		String apiKey = EnvLoader.get("OPENROUTER_API_KEY");
		if (apiKey == null || apiKey.isBlank()) {
			throw new IOException("OPENROUTER_API_KEY is not set (env var or .env file).");
		}

		String methodName = extractMethodName(existingTesterCode);

		String systemPrompt = "You are a Java test case generator for an automated grading system.\n"
				+ "You are generating tests for question " + questionId + " (tester class: " + testerClassName + ").\n"
				+ (methodName != null ? "The method under test is: " + methodName + "\n" : "") + "\nRules:\n"
				+ "- Each test case MUST be a { try/catch } block matching the EXACT pattern in the existing tester\n"
				+ "- ONLY test the method shown in the existing tester — do NOT test methods from other questions\n"
				+ "- Use the SAME method name, parameter types, and comparison logic as the existing tests\n"
				+ "- Use varied, realistic inputs including edge cases (empty lists, boundary values, large inputs)\n"
				+ "- Do NOT repeat inputs from existing test cases\n"
				+ "- Output ONLY raw Java code — no markdown fences, no ``` delimiters, no explanation, no class declaration\n"
				+ "- Each generated block must be a standalone { ... } block containing a try/catch\n"
				+ "- Use placeholder: score += WEIGHT_N; where N is a sequential number (WEIGHT_1, WEIGHT_2, ...)\n"
				+ "- Increment tcNum++ in each test case\n"
				+ "- Print test info, expected, actual, and Passed/Failed exactly as in the existing tester\n"
				+ "\nCRITICAL rules for data and correctness:\n"
				+ "- If the existing tester reads from data files (e.g. .txt files), ONLY use filenames that appear in the existing tester. NEVER invent new filenames.\n"
				+ "- For file-based tests, use the REAL data from the provided file contents to compute correct expected values\n"
				+ "- For expected values, you MUST compute them precisely from the actual data. Do NOT guess or approximate.\n"
				+ "- For floating-point comparisons: match the exact comparison pattern used in the existing tester (== or .equals)\n"
				+ "- If the existing tester catches a specific exception (e.g. DataException), include both normal-path AND exception-path test cases\n";

		String userMessage = buildUserMessage(questionId, examContext, existingTesterCode, numCases, additionalContext);

		Map<String, Object> requestBody = Map.of("model", config.getAiModel(), "max_tokens", config.getAiMaxTokens(),
				"messages", List.of(Map.of("role", "system", "content", systemPrompt),
						Map.of("role", "user", "content", userMessage)));

		String requestJson = objectMapper.writeValueAsString(requestBody);

		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(API_URL))
				.header("Content-Type", "application/json").header("Authorization", "Bearer " + apiKey)
				.POST(HttpRequest.BodyPublishers.ofString(requestJson)).timeout(Duration.ofSeconds(120)).build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

		if (response.statusCode() != 200) {
			throw new IOException("OpenRouter API error " + response.statusCode() + ": " + response.body());
		}

		return stripMarkdownFences(extractContent(response.body()));
	}

	private String buildUserMessage(String questionId, String examContext, String existingTesterCode, int numCases,
			String additionalContext) {
		StringBuilder sb = new StringBuilder();
		sb.append("Question ID: ").append(questionId).append("\n\n");
		sb.append("Exam question context:\n").append(examContext).append("\n\n");

		if (existingTesterCode != null && !existingTesterCode.isBlank()) {
			sb.append("Existing tester file (you MUST follow this exact format and test the SAME method):\n")
					.append(existingTesterCode).append("\n\n");
		}

		if (additionalContext != null && !additionalContext.isBlank()) {
			sb.append(additionalContext).append("\n\n");
		}

		sb.append("Generate exactly ").append(numCases)
				.append(" new test cases as standalone { ... } blocks (no class wrapper, no imports). ")
				.append("Each block must test the SAME method as shown in the existing tester above. ")
				.append("Use WEIGHT_1, WEIGHT_2, ... WEIGHT_").append(numCases)
				.append(" as weight placeholders (one per test case). ").append("You MUST produce exactly ")
				.append(numCases).append(" { ... } blocks.");

		return sb.toString();
	}

	/**
	 * Extract the primary method name being tested from existing tester code by
	 * looking for method calls that are NOT standard Java (System.out, etc).
	 */
	private String extractMethodName(String existingCode) {
		if (existingCode == null || existingCode.isBlank()) {
			return null;
		}
		// Look for patterns like: result = methodName(inputs) or methodName(inputs)
		java.util.regex.Pattern pattern = java.util.regex.Pattern
				.compile("(?:result\\s*=\\s*)?([a-z][a-zA-Z0-9]+)\\s*\\(\\s*inputs\\s*\\)");
		java.util.regex.Matcher matcher = pattern.matcher(existingCode);
		while (matcher.find()) {
			String name = matcher.group(1);
			// Skip standard method calls
			if (!name.equals("println") && !name.equals("printf") && !name.equals("asList") && !name.equals("equals")) {
				return name;
			}
		}
		return null;
	}

	/**
	 * Strip markdown code fences (```java ... ``` or ``` ... ```) from AI
	 * responses.
	 */
	private String stripMarkdownFences(String code) {
		String trimmed = code.strip();
		// Remove opening fence: ```java or ```
		if (trimmed.startsWith("```")) {
			int firstNewline = trimmed.indexOf('\n');
			if (firstNewline > 0) {
				trimmed = trimmed.substring(firstNewline + 1);
			}
		}
		// Remove closing fence
		if (trimmed.endsWith("```")) {
			trimmed = trimmed.substring(0, trimmed.length() - 3).stripTrailing();
		}
		return trimmed;
	}

	@SuppressWarnings("unchecked")
	private String extractContent(String responseBody) throws IOException {
		Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
		List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
		if (choices == null || choices.isEmpty()) {
			throw new IOException("No choices in OpenRouter API response");
		}
		Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
		if (message == null) {
			throw new IOException("No message in OpenRouter API response choice");
		}
		Object content = message.get("content");
		if (content == null) {
			throw new IOException("No content in OpenRouter API response message");
		}
		return content.toString();
	}
}

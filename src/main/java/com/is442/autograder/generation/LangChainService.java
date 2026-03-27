package com.is442.autograder.generation;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * LangChain4j AI service for test case generation, recommendation, and code
 * refinement. Registered as a Spring bean by WebConfig using
 * AiServices.create(). The ChatModel (OpenRouter) is auto-configured via
 * application.properties.
 */
public interface LangChainService {

	@SystemMessage("""
			You are a Java test case generator for an automated grading system.
			Generate test cases as a JSON array. Each element must be a JSON object with these fields:
			  description, conceptCovered, setup, methodCall, expected, assertion, weight,
			  expectsException, exceptionType
			Rules:
			- Use ONLY method names and filenames from the existing tester code
			- Use varied, realistic inputs including edge cases (empty lists, boundary values, large inputs)
			- Do NOT repeat inputs from existing test cases
			- For file-based tests: use REAL data from the provided file contents to compute correct expected values
			- For floating-point: use appropriate equality comparisons
			- For exception tests: set expectsException=true, assertion=null
			- For void methods: set assertion=null, expectsException=false
			- Return ONLY a valid JSON array — no markdown, no explanation, no code fences""")
	String generateTestCasesJson(@UserMessage String prompt);

	@SystemMessage("""
			You are an expert Java test case analyst.
			Analyse the given exam question and existing test cases, then recommend NEW test cases needed.
			Respond with a JSON object:
			{
			  "questionId": "...",
			  "existingConcepts": ["Normal: valid input", "Boundary: single element"],
			  "recommendedCount": 3,
			  "conceptsToCover": ["Boundary: empty list", "Exception: null input"],
			  "rationale": "Brief explanation"
			}
			IMPORTANT:
			- Analyze what the EXISTING test cases DO and list those concepts in 'existingConcepts'
			- Recommend NEW test cases in 'conceptsToCover' that COMPLEMENT existing ones
			- Do NOT repeat concepts already covered
			Consider: normal paths, boundary conditions, exception paths, null inputs, large inputs.
			Return ONLY valid JSON, no markdown.""")
	String recommendJson(@UserMessage String prompt);

	@SystemMessage("""
			You are a Java test code refiner for an automated grading system.
			You will be given existing generated tester code and a refinement request.
			Apply the requested changes to the code and return the COMPLETE updated Java code.
			Rules:
			- Preserve the overall structure and class/method signatures
			- Only modify what the refinement request asks for
			- Return ONLY valid Java code — no markdown fences, no explanation""")
	String refineCode(@UserMessage String prompt);
}

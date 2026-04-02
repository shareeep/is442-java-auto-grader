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

			CRITICAL — 'description' field naming (violations will break the system):
			- Each description MUST be a specific, meaningful name that identifies exactly what is being tested.
			- If concepts or custom instructions are listed in the prompt, name each test case directly after its concept/instruction.
			- ABSOLUTELY FORBIDDEN: "Generated Test Case 1", "Test Case 2", "Test 1", "Generated Test N", or any numbered placeholder.

			Generate test cases as a JSON array. Each element MUST be a JSON object with EXACTLY these fields:

			  "description"    — short meaningful name for the test case
			  "conceptCovered" — what concept this tests
			  "setup"          — VALID JAVA CODE ONLY. Declares input variables needed for methodCall. Each statement ends with ';'. NEVER write English prose, comments, or instructions here. Example: "ArrayList<String> inputs = new ArrayList<>(); inputs.add(\\"alice\\"); inputs.add(\\"ben\\");"
			  "methodCall"     — single method invocation WITHOUT semicolon. Example: "getIsogramWords(inputs)"
			  "expected"       — human-readable description of expected output (for log printing ONLY — this is NOT used in code)
			  "assertion"      — SELF-CONTAINED boolean Java expression. The ONLY variable available is 'result' (auto-declared as the return value of methodCall). You MUST NOT reference any variable named 'expected' — it does not exist. Inline the expected value directly. Examples: "result.equals(new ArrayList<>(Arrays.asList(\\"alice\\", \\"ben\\")))", "result == 30.0", "Math.abs(result - 37.0) < 0.001", "result.toString().equals(\\"[R1=>20.0:24.0]\\")"
			  "weight"         — numeric weight (default 1.0)
			  "expectsException" — true if test should throw an exception, false otherwise
			  "exceptionType"  — exception class name if expectsException is true, null otherwise

			Rules:
			- STYLE MATCHING: If existing tester code is provided, replicate its method invocation style and variable types. Do NOT introduce APIs or patterns not present in the existing code.
			- Use ONLY method names, class names, constructors, and filenames from the provided existing tester or student code — NEVER guess or invent API signatures
			- Use varied, realistic inputs including edge cases (empty lists, boundary values, large inputs)
			- Do NOT repeat inputs from existing test cases
			- For file-based tests: use REAL data from the provided file contents to compute correct expected values. Use ONLY filenames that already exist in the provided context — NEVER invent new filenames.
			- For floating-point: use Math.abs(result - expected) < 0.001 in assertion
			- For exception tests: set expectsException=true, assertion=null
			- For void methods: set assertion=null, expectsException=false
			- Return ONLY a valid JSON array — no markdown, no explanation, no code fences""")
	String generateTestCasesJson(@UserMessage String prompt);

	@SystemMessage("""
			You are an expert Java test case analyst.
			Analyse the given exam question and existing test cases, then identify ALL new test cases needed.
			Respond with a JSON object:
			{
			  "questionId": "...",
			  "existingConcepts": ["Normal: valid input", "Boundary: single element"],
			  "recommendedCount": <number equal to conceptsToCover.length>,
			  "conceptsToCover": ["Boundary: empty list", "Exception: null input", "...all other scenarios..."],
			  "rationale": "Brief explanation"
			}
			IMPORTANT:
			- You are analyzing ONE SPECIFIC sub-question. Only suggest concepts relevant to THAT sub-question's requirements — never bleed into sibling sub-questions.
			- Analyze what the EXISTING test cases DO and list those concepts in 'existingConcepts'
			- List ONLY the concepts in 'conceptsToCover' that are genuinely meaningful and distinct for THIS specific question
			- Return at most 5 concepts — pick the highest-value ones. A simple question may need 2-3; a complex one may need 5. Never exceed 5.
			- Prioritise correctness and relevance over quantity; omit any concept that would be redundant or trivially similar to another
			- Do NOT repeat concepts already covered by existing tests
			- Consider: normal paths, boundary conditions, exception paths, null inputs, large inputs, type-specific edge cases — but only include those that genuinely apply
			- Set 'recommendedCount' equal to the number of items in 'conceptsToCover'
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

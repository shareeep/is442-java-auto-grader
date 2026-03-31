package com.is442.autograder.model;

/**
 * Structured representation of a single AI-generated test case. All fields are
 * semantic — Java code assembly is fully deterministic via
 * {@link com.is442.autograder.generation.TesterFileWriter#buildCodeFromStructured}.
 *
 * @param description
 *            brief description of what is being tested
 * @param conceptCovered
 *            what concept is exercised, e.g. "Boundary: empty list"
 * @param setup
 *            Java setup statements (may be null/blank)
 * @param methodCall
 *            the exact method call expression, e.g. "myMethod(arg1, arg2)"
 * @param expected
 *            human-readable expected value for print output
 * @param assertion
 *            Java boolean expression to verify result, e.g. "result == 5"; null
 *            for void methods
 * @param weight
 *            scoring weight for this test case
 * @param expectsException
 *            whether the test expects an exception to be thrown
 * @param exceptionType
 *            fully-simple exception class name, e.g. "DataException"; null if
 *            {@code expectsException} is false
 */
public record StructuredTestCase(String description, String conceptCovered, String setup, String methodCall,
		String expected, String assertion, double weight, boolean expectsException, String exceptionType) {
}

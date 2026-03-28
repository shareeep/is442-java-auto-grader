package com.is442.autograder.model;

/**
 * Holds one AI-generated test case with its assigned weight.
 */
public record GeneratedTestCase(String description, String inputArgs, String expectedOutput, double weight) {
}

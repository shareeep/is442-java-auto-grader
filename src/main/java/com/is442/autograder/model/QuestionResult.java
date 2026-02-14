package com.is442.autograder.model;

/**
 * Result of grading a single question for a student.
 */
public class QuestionResult {

    private final String questionId;
    private final double score;
    private final double maxScore;
    private final boolean compiled;
    private final boolean executed;
    private final String output;
    private final String errorMessage;

    public QuestionResult(String questionId, double score, double maxScore,
            boolean compiled, boolean executed,
            String output, String errorMessage) {
        this.questionId = questionId;
        this.score = score;
        this.maxScore = maxScore;
        this.compiled = compiled;
        this.executed = executed;
        this.output = output;
        this.errorMessage = errorMessage;
    }

    /** Convenience factory for a question that failed to compile. */
    public static QuestionResult compilationFailure(String questionId, double maxScore, String error) {
        return new QuestionResult(questionId, 0.0, maxScore, false, false, "", error);
    }

    /** Convenience factory for a question that timed out during execution. */
    public static QuestionResult timeout(String questionId, double maxScore) {
        return new QuestionResult(questionId, 0.0, maxScore, true, false, "", "Execution timed out");
    }

    /** Convenience factory for a missing question (no files found). */
    public static QuestionResult missing(String questionId, double maxScore) {
        return new QuestionResult(questionId, 0.0, maxScore, false, false, "", "Question files not found");
    }

    public String getQuestionId() {
        return questionId;
    }

    public double getScore() {
        return score;
    }

    public double getMaxScore() {
        return maxScore;
    }

    public boolean isCompiled() {
        return compiled;
    }

    public boolean isExecuted() {
        return executed;
    }

    public String getOutput() {
        return output;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return questionId + ": " + score + "/" + maxScore;
    }
}

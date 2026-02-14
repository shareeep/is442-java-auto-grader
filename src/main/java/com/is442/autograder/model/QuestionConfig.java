package com.is442.autograder.model;

/**
 * Configuration for a single question (e.g. Q1a).
 * Maps a question ID to its folder, tester class, and max score.
 */
public class QuestionConfig {

    private final String questionId; // e.g. "Q1a"
    private final String folder; // e.g. "Q1"
    private final String testerClassName; // e.g. "Q1aTester"
    private final double maxScore; // e.g. 3.0

    public QuestionConfig(String questionId, String folder, String testerClassName, double maxScore) {
        this.questionId = questionId;
        this.folder = folder;
        this.testerClassName = testerClassName;
        this.maxScore = maxScore;
    }

    public String getQuestionId() {
        return questionId;
    }

    public String getFolder() {
        return folder;
    }

    public String getTesterClassName() {
        return testerClassName;
    }

    public double getMaxScore() {
        return maxScore;
    }

    @Override
    public String toString() {
        return questionId + " -> " + folder + "/" + testerClassName + " (max=" + maxScore + ")";
    }
}

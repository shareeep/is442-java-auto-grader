package com.is442.autograder.reporting;

import com.is442.autograder.model.Anomaly;
import com.is442.autograder.model.QuestionConfig;
import com.is442.autograder.model.QuestionResult;
import com.is442.autograder.model.StudentSubmission;

import java.util.List;

/**
 * Prints a formatted grading summary and anomaly report to the console.
 */
public class ConsoleReporter {

    // ANSI color codes
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN = "\u001B[36m";

    /**
     * Print the complete grading summary including scores table and anomalies.
     */
    public void printSummary(List<StudentSubmission> submissions,
            List<QuestionConfig> questionConfigs) {
        System.out.println();
        printAnomalies(submissions);
        System.out.println();
        printScoreTable(submissions, questionConfigs);
    }

    /**
     * Print a formatted table of student scores.
     */
    private void printScoreTable(List<StudentSubmission> submissions,
            List<QuestionConfig> questionConfigs) {

        System.out.println(BOLD + "GRADING SUMMARY" + RESET);
        System.out.println("═".repeat(75));

        // Header
        System.out.printf(BOLD + " %-20s", "Student");
        for (QuestionConfig qc : questionConfigs) {
            System.out.printf("│ %-5s", qc.getQuestionId());
        }
        System.out.printf("│ %-6s%n" + RESET, "Total");

        System.out.println("─".repeat(21) + ("┼" + "─".repeat(6)).repeat(questionConfigs.size())
                + "┼" + "─".repeat(7));

        // Data rows
        for (StudentSubmission sub : submissions) {
            System.out.printf(" %-20s", sub.getDisplayName());

            for (QuestionConfig qc : questionConfigs) {
                double score = sub.getResults().stream()
                        .filter(r -> r.getQuestionId().equals(qc.getQuestionId()))
                        .mapToDouble(QuestionResult::getScore)
                        .findFirst()
                        .orElse(0.0);

                String color = score >= qc.getMaxScore() ? GREEN
                        : score > 0 ? YELLOW : RED;
                System.out.printf("│ %s%-5.1f%s", color, score, RESET);
            }

            double total = sub.getTotalScore();
            double maxTotal = sub.getMaxPossibleScore();
            String totalColor = total >= maxTotal ? GREEN
                    : total > 0 ? CYAN : RED;
            System.out.printf("│ %s%-6.1f%s%n", totalColor, total, RESET);
        }

        System.out.println("═".repeat(75));
    }

    /**
     * Print anomalies summary grouped by student.
     */
    private void printAnomalies(List<StudentSubmission> submissions) {
        long totalAnomalies = submissions.stream()
                .mapToLong(s -> s.getAnomalies().size())
                .sum();

        if (totalAnomalies == 0) {
            System.out.println(GREEN + "No anomalies detected." + RESET);
            return;
        }

        System.out.println(BOLD + YELLOW + "ANOMALIES DETECTED: " + totalAnomalies + RESET);

        for (StudentSubmission sub : submissions) {
            if (!sub.hasAnomalies())
                continue;

            for (Anomaly anomaly : sub.getAnomalies()) {
                String icon = anomaly.getSeverity() == Anomaly.Severity.ERROR ? RED + "  ✖ " : YELLOW + "  ⚠ ";
                System.out.println(icon + sub.getDisplayName() + " - " + anomaly.getDescription() + RESET);
            }
        }
    }

    /**
     * Print a progress update during grading.
     */
    public void printProgress(int current, int total, String studentName) {
        int barWidth = 30;
        int filled = (int) ((double) current / total * barWidth);
        int empty = barWidth - filled;

        String bar = "█".repeat(filled) + "░".repeat(empty);
        int percent = (int) ((double) current / total * 100);

        System.out.printf("\r  [%s] %3d%% │ Grading %s...", bar, percent, studentName);

        if (current == total) {
            System.out.println(); // newline after 100%
        }
    }
}

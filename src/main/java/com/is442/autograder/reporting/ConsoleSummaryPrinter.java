package com.is442.autograder.reporting;

import java.util.List;

import com.is442.autograder.model.Anomaly;
import com.is442.autograder.model.QuestionConfig;
import com.is442.autograder.model.QuestionResult;
import com.is442.autograder.model.StudentSubmission;

/**
 * Renders the post-run anomaly and score summaries to the console.
 */
final class ConsoleSummaryPrinter {

	private final String reset;
	private final String bold;
	private final String red;
	private final String green;
	private final String yellow;
	private final String cyan;

	ConsoleSummaryPrinter(String reset, String bold, String red, String green, String yellow, String cyan) {
		this.reset = reset;
		this.bold = bold;
		this.red = red;
		this.green = green;
		this.yellow = yellow;
		this.cyan = cyan;
	}

	void printSummary(List<StudentSubmission> submissions, List<QuestionConfig> questionConfigs) {
		System.out.println();
		printAnomalies(submissions);
		System.out.println();
		printScoreTable(submissions, questionConfigs);
	}

	private void printAnomalies(List<StudentSubmission> submissions) {
		long totalAnomalies = submissions.stream().mapToLong(s -> s.getAnomalies().size()).sum();
		if (totalAnomalies == 0) {
			System.out.println(green + "No anomalies detected." + reset);
			return;
		}
		System.out.println(bold + yellow + "ANOMALIES DETECTED: " + totalAnomalies + reset);
		for (StudentSubmission sub : submissions) {
			if (!sub.hasAnomalies()) {
				continue;
			}
			for (Anomaly anomaly : sub.getAnomalies()) {
				String icon = anomaly.getSeverity() == Anomaly.Severity.ERROR
						? red + "  [ERROR] "
						: yellow + "  [WARN]  ";
				System.out.println(icon + sub.getDisplayName() + " - " + anomaly.getDescription() + reset);
			}
		}
	}

	private void printScoreTable(List<StudentSubmission> submissions, List<QuestionConfig> questionConfigs) {
		System.out.println(bold + "GRADING SUMMARY" + reset);
		System.out.println("=".repeat(75));
		System.out.printf(bold + " %-20s", "Student");
		for (QuestionConfig qc : questionConfigs) {
			System.out.printf("| %-5s", qc.getQuestionId());
		}
		System.out.printf("| %-6s%n" + reset, "Total");
		System.out.println("-".repeat(21) + ("+" + "-".repeat(6)).repeat(questionConfigs.size()) + "+" + "-".repeat(7));
		for (StudentSubmission sub : submissions) {
			System.out.printf(" %-20s", sub.getDisplayName());
			for (QuestionConfig qc : questionConfigs) {
				double score = sub.getResults().stream().filter(r -> r.getQuestionId().equals(qc.getQuestionId()))
						.mapToDouble(QuestionResult::getScore).findFirst().orElse(0.0);
				String color = score >= qc.getMaxScore() ? green : score > 0 ? yellow : red;
				System.out.printf("| %s%-5.1f%s", color, score, reset);
			}
			double total = sub.getTotalScore();
			double maxTotal = sub.getMaxPossibleScore();
			String totalColor = total >= maxTotal ? green : total > 0 ? cyan : red;
			System.out.printf("| %s%-6.1f%s%n", totalColor, total, reset);
		}
		System.out.println("=".repeat(75));
	}
}

package com.is442.autograder.reporting;

import com.is442.autograder.model.Anomaly;
import com.is442.autograder.model.QuestionConfig;
import com.is442.autograder.model.QuestionResult;
import com.is442.autograder.model.StudentSubmission;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
	private static final String ANSI_SAVE = "\u001B[s";
	private static final String ANSI_RESTORE = "\u001B[u";
	private static final String ANSI_CLEAR_LINE = "\u001B[2K";
	private static final String ANSI_MOVE_DOWN_FMT = "\u001B[%dB";
	private static final String ANSI_MOVE_DOWN_ONE = "\u001B[1B";
	private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
			.withZone(ZoneId.systemDefault());

	private boolean progressActive = false;
	private int progressTotal = 0;
	private int logLines = 0;

	/**
	 * Print the complete grading summary including scores table and anomalies.
	 */
	public void printSummary(List<StudentSubmission> submissions, List<QuestionConfig> questionConfigs) {
		System.out.println();
		printAnomalies(submissions);
		System.out.println();
		printScoreTable(submissions, questionConfigs);
	}

	public void startProgress(int total) {
		progressActive = true;
		progressTotal = total;
		logLines = 0;
		System.out.print(ANSI_SAVE);
		System.out.print(renderProgress(0, total));
		System.out.println();
		System.out.println("  Current: (starting...)");
	}

	public void updateCurrentStudent(String studentName) {
		if (!progressActive) {
			startProgress(progressTotal > 0 ? progressTotal : 1);
		}
		System.out.print(ANSI_RESTORE);
		System.out.print(ANSI_MOVE_DOWN_ONE);
		System.out.print(ANSI_CLEAR_LINE);
		System.out.print("  Current: " + studentName);
		System.out.print(String.format(ANSI_MOVE_DOWN_FMT, Math.max(0, logLines)));
	}

	public void logInfo(String message) {
		printLogLine(message);
	}

	public void logWarning(String message) {
		printLogLine("WARNING: " + message);
	}

	public void logError(String message) {
		printLogLine("ERROR: " + message);
	}

	public void logRaw(String message) {
		printLogLine(message);
	}

	/**
	 * Print a formatted table of student scores.
	 */
	private void printScoreTable(List<StudentSubmission> submissions, List<QuestionConfig> questionConfigs) {

		System.out.println(BOLD + "GRADING SUMMARY" + RESET);
		System.out.println("═".repeat(75));

		// Header
		System.out.printf(BOLD + " %-20s", "Student");
		for (QuestionConfig qc : questionConfigs) {
			System.out.printf("│ %-5s", qc.getQuestionId());
		}
		System.out.printf("│ %-6s%n" + RESET, "Total");

		System.out.println("─".repeat(21) + ("┼" + "─".repeat(6)).repeat(questionConfigs.size()) + "┼" + "─".repeat(7));

		// Data rows
		for (StudentSubmission sub : submissions) {
			System.out.printf(" %-20s", sub.getDisplayName());

			for (QuestionConfig qc : questionConfigs) {
				double score = sub.getResults().stream().filter(r -> r.getQuestionId().equals(qc.getQuestionId()))
						.mapToDouble(QuestionResult::getScore).findFirst().orElse(0.0);

				String color = score >= qc.getMaxScore() ? GREEN : score > 0 ? YELLOW : RED;
				System.out.printf("│ %s%-5.1f%s", color, score, RESET);
			}

			double total = sub.getTotalScore();
			double maxTotal = sub.getMaxPossibleScore();
			String totalColor = total >= maxTotal ? GREEN : total > 0 ? CYAN : RED;
			System.out.printf("│ %s%-6.1f%s%n", totalColor, total, RESET);
		}

		System.out.println("═".repeat(75));
	}

	/**
	 * Print anomalies summary grouped by student.
	 */
	private void printAnomalies(List<StudentSubmission> submissions) {
		long totalAnomalies = submissions.stream().mapToLong(s -> s.getAnomalies().size()).sum();

		if (totalAnomalies == 0) {
			System.out.println(GREEN + "No anomalies detected." + RESET);
			return;
		}

		System.out.println(BOLD + YELLOW + "ANOMALIES DETECTED: " + totalAnomalies + RESET);

		for (StudentSubmission sub : submissions) {
			if (!sub.hasAnomalies()) {
				continue;
			}

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
		if (!progressActive) {
			startProgress(total);
		}
		progressTotal = total;
		System.out.print(ANSI_RESTORE);
		System.out.print(ANSI_CLEAR_LINE);
		System.out.print(renderProgress(current, total));
		System.out.print(String.format(ANSI_MOVE_DOWN_FMT, 1 + Math.max(0, logLines)));
	}

	public void endProgress() {
		if (!progressActive) {
			return;
		}
		System.out.print(ANSI_RESTORE);
		System.out.print(String.format(ANSI_MOVE_DOWN_FMT, 2 + Math.max(0, logLines)));
		System.out.println();
		progressActive = false;
	}

	private String renderProgress(int current, int total) {
		int barWidth = 30;
		int filled = total > 0 ? (int) ((double) current / total * barWidth) : 0;
		int empty = barWidth - filled;
		String bar = "█".repeat(filled) + "░".repeat(empty);
		int percent = total > 0 ? (int) ((double) current / total * 100) : 0;
		return String.format("  [%s] %3d%%", bar, percent);
	}

	private void printLogLine(String message) {
		if (!progressActive) {
			System.out.println("[" + TS_FORMATTER.format(Instant.now()) + "] " + message);
			return;
		}
		System.out.print(ANSI_RESTORE);
		System.out.print(String.format(ANSI_MOVE_DOWN_FMT, 2 + Math.max(0, logLines)));
		System.out.print(ANSI_CLEAR_LINE);
		System.out.println("[" + TS_FORMATTER.format(Instant.now()) + "] " + message);
		logLines++;
	}
}

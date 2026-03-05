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

	private static final String RESET = "\u001B[0m";
	private static final String BOLD = "\u001B[1m";
	private static final String RED = "\u001B[31m";
	private static final String GREEN = "\u001B[32m";
	private static final String YELLOW = "\u001B[33m";
	private static final String CYAN = "\u001B[36m";

	private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
			.withZone(ZoneId.systemDefault());

	private boolean progressActive = false;
	private int progressTotal = 0;
	private int currentProgress = 0;
	private String currentStudent = "(starting...)";
	/** Number of log lines printed below the bar since it was last anchored. */
	private int linesBelow = 0;

	public void startProgress(int total) {
		progressActive = true;
		progressTotal = total;
		currentProgress = 0;
		linesBelow = 0;
		currentStudent = "(starting...)";
		// Print bar + newline. Cursor now sits exactly 1 line below the bar.
		System.out.println(renderBar());
	}

	public void updateCurrentStudent(String studentName) {
		this.currentStudent = studentName;
		redrawBar();
	}

	public void printProgress(int current, int total, String studentName) {
		if (!progressActive) {
			startProgress(total);
		}
		this.currentProgress = current;
		this.progressTotal = total;
		this.currentStudent = studentName;

		redrawBar();
	}

	public void endProgress() {
		if (!progressActive) {
			return;
		}
		progressActive = false;
		currentProgress = progressTotal;
		redrawBar();
		System.out.println();
	}

	/**
	 * Compute the bar string without printing.
	 */
	private String renderBar() {
		int barWidth = 30;
		int filled = progressTotal == 0 ? 0 : (int) ((double) currentProgress / progressTotal * barWidth);
		String bar = "\u2588".repeat(filled) + "\u2591".repeat(barWidth - filled);
		int percent = progressTotal == 0 ? 0 : (int) ((double) currentProgress / progressTotal * 100);
		return "  [" + bar + "] " + percent + "%  Current: " + currentStudent;
	}

	/**
	 * Go up {@code (1 + linesBelow)} lines to the bar, clear and redraw it, then
	 * return the cursor to its original position. {@code linesBelow} is unchanged —
	 * the cursor stays where it was before the call.
	 */
	private void redrawBar() {
		int moveUp = 1 + linesBelow;
		System.out.print("\u001B[" + moveUp + "A\r\u001B[2K" + renderBar());
		System.out.print("\u001B[" + moveUp + "B\r");
		System.out.flush();
	}

	/**
	 * Print the complete grading summary including scores table and anomalies.
	 */
	public void printSummary(List<StudentSubmission> submissions, List<QuestionConfig> questionConfigs) {
		System.out.println();
		printAnomalies(submissions);
		System.out.println();
		printScoreTable(submissions, questionConfigs);
	}

	public void logInfo(String message) {
		printLog(message);
	}

	public void logWarning(String message) {
		printLog("WARNING: " + message);
	}

	public void logError(String message) {
		printLog("ERROR: " + message);
	}

	public void logRaw(String message) {
		printLog(message);
	}

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
				String icon = anomaly.getSeverity() == Anomaly.Severity.ERROR
						? RED + "  \u2716 "
						: YELLOW + "  \u26a0 ";
				System.out.println(icon + sub.getDisplayName() + " - " + anomaly.getDescription() + RESET);
			}
		}
	}

	private void printScoreTable(List<StudentSubmission> submissions, List<QuestionConfig> questionConfigs) {
		System.out.println(BOLD + "GRADING SUMMARY" + RESET);
		System.out.println("\u2550".repeat(75));
		System.out.printf(BOLD + " %-20s", "Student");
		for (QuestionConfig qc : questionConfigs) {
			System.out.printf("\u2502 %-5s", qc.getQuestionId());
		}
		System.out.printf("\u2502 %-6s%n" + RESET, "Total");
		System.out.println("\u2500".repeat(21) + ("\u253c" + "\u2500".repeat(6)).repeat(questionConfigs.size())
				+ "\u253c" + "\u2500".repeat(7));
		for (StudentSubmission sub : submissions) {
			System.out.printf(" %-20s", sub.getDisplayName());
			for (QuestionConfig qc : questionConfigs) {
				double score = sub.getResults().stream().filter(r -> r.getQuestionId().equals(qc.getQuestionId()))
						.mapToDouble(QuestionResult::getScore).findFirst().orElse(0.0);
				String color = score >= qc.getMaxScore() ? GREEN : score > 0 ? YELLOW : RED;
				System.out.printf("\u2502 %s%-5.1f%s", color, score, RESET);
			}
			double total = sub.getTotalScore();
			double maxTotal = sub.getMaxPossibleScore();
			String totalColor = total >= maxTotal ? GREEN : total > 0 ? CYAN : RED;
			System.out.printf("\u2502 %s%-6.1f%s%n", totalColor, total, RESET);
		}
		System.out.println("\u2550".repeat(75));
	}

	private void printLog(String message) {
		String log = "[" + TS_FORMATTER.format(Instant.now()) + "] " + message;

		if (!progressActive) {
			System.out.println(log);
			return;
		}

		// Go up to the bar, redraw it, return to current position, clear the
		// line, and print the log. Each call pushes the cursor 1 line further
		// below the bar, so linesBelow must be incremented.
		int moveUp = 1 + linesBelow;
		System.out.print("\u001B[" + moveUp + "A\r\u001B[2K" + renderBar());
		System.out.print("\u001B[" + moveUp + "B\r\u001B[2K");
		System.out.println(log);
		linesBelow++;
	}
}

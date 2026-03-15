package com.is442.autograder.reporting;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import com.is442.autograder.model.Anomaly;
import com.is442.autograder.model.QuestionConfig;
import com.is442.autograder.model.QuestionResult;
import com.is442.autograder.model.StudentSubmission;

/**
 * Prints a formatted grading summary and anomaly report to the console. During
 * grading, shows a Lanterna TUI with a pinned progress bar at the top and a
 * scrolling log panel below. Falls back to a simple \r-based bar if the
 * terminal does not support Lanterna (e.g. piped output, TERM=dumb).
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

	// Progress state
	private boolean progressActive = false;
	private int progressTotal = 0;
	private int currentProgress = 0;
	private String currentStudent = "(starting...)";

	// Lanterna state
	private Screen screen;
	private boolean lanternaMode = false;
	private final List<String> logLines = new ArrayList<>();
	private PrintStream savedOut;
	private PrintStream savedErr;

	// Stop-grading flag (set when user presses 'q' in Lanterna mode)
	private volatile boolean stopRequested = false;
	private Thread keyPollerThread = null;

	// Log grouping state
	private String pendingStudentHeader = null;
	private boolean inStudentGroup = false;

	/** Returns true if the user pressed 'q' to request a graceful stop. */
	public boolean isStopRequested() {
		return stopRequested;
	}

	// ── Progress API
	// ──────────────────────────────────────────────────────────────

	public void startProgress(int total) {
		progressActive = true;
		progressTotal = total;
		currentProgress = 0;
		currentStudent = "(starting...)";
		logLines.clear();

		try {
			Terminal terminal = new DefaultTerminalFactory().createTerminal();
			screen = new TerminalScreen(terminal);
			screen.startScreen();
			screen.setCursorPosition(null);

			// Suppress stdout/stderr so stray System.out calls don't corrupt the TUI
			savedOut = System.out;
			savedErr = System.err;
			PrintStream sink = new PrintStream(OutputStream.nullOutputStream());
			System.setOut(sink);
			System.setErr(sink);

			lanternaMode = true;
			redrawScreen();

			// Background thread: detect 'q' keypress immediately
			keyPollerThread = new Thread(() -> {
				while (!Thread.currentThread().isInterrupted() && lanternaMode) {
					try {
						KeyStroke key = screen.readInput();
						if (key != null && key.getKeyType() == KeyType.Character) {
							char c = key.getCharacter();
							if ((c == 'q' || c == 'Q') && !stopRequested) {
								stopRequested = true;
								logLines.add("[" + TS_FORMATTER.format(Instant.now())
										+ "] ⚠ Stopping after current student completes...");
								redrawScreen();
							}
						}
					} catch (Exception ignored) {
						break;
					}
				}
			}, "key-poller");
			keyPollerThread.setDaemon(true);
			keyPollerThread.start();
		} catch (Exception e) {
			// Not a real terminal — fall back to simple \r bar
			screen = null;
			lanternaMode = false;
			savedOut = null;
			savedErr = null;
			System.out.print(barSimple());
			System.out.flush();
		}
	}

	public void updateCurrentStudent(String studentName) {
		this.currentStudent = studentName;
		if (lanternaMode)
			redrawScreen();
		else
			redrawBarSimple();
	}

	public void printProgress(int current, int total, String studentName) {
		if (!progressActive)
			startProgress(total);
		this.currentProgress = current;
		this.progressTotal = total;
		this.currentStudent = studentName;
		if (lanternaMode)
			redrawScreen();
		else
			redrawBarSimple();
	}

	public void endProgress() {
		if (!progressActive)
			return;
		progressActive = false;
		currentProgress = progressTotal;

		if (lanternaMode) {
			lanternaMode = false;
			if (keyPollerThread != null) {
				keyPollerThread.interrupt();
				keyPollerThread = null;
			}
			try {
				screen.stopScreen();
			} catch (IOException ignored) {
			}
			screen = null;
			System.setOut(savedOut);
			System.setErr(savedErr);
			savedOut = null;
			savedErr = null;
			// Replay captured log lines so they reach run.log via ConsoleLogCapture tee
			for (String line : logLines) {
				System.out.println(line);
			}
		} else {
			System.out.print("\r\u001B[2K" + barSimple() + "\n");
			System.out.flush();
		}
	}

	// ── Lanterna rendering
	// ────────────────────────────────────────────────────────

	private void redrawScreen() {
		if (screen == null)
			return;
		try {
			screen.doResizeIfNecessary();
			TerminalSize size = screen.getTerminalSize();
			int h = size.getRows();
			int w = size.getColumns();
			TextGraphics g = screen.newTextGraphics();
			g.setBackgroundColor(TextColor.ANSI.DEFAULT);

			// Row 0: progress bar
			g.setForegroundColor(TextColor.ANSI.CYAN);
			g.putString(0, 0, pad(barLanterna(w), w));

			// Row 1: stop hint
			if (stopRequested) {
				g.setForegroundColor(TextColor.ANSI.RED);
				g.putString(0, 1, pad("  ⚠  Stopping — waiting for current student to finish...", w));
			} else {
				g.setForegroundColor(TextColor.ANSI.YELLOW);
				g.putString(0, 1, pad("  Press q to stop grading after the current student finishes.", w));
			}

			// Row 2: divider
			g.setForegroundColor(TextColor.ANSI.WHITE);
			g.putString(0, 2, "-".repeat(w));
			int logRows = Math.max(0, h - 3);
			int start = Math.max(0, logLines.size() - logRows);
			for (int r = 0; r < logRows; r++) {
				int idx = start + r;
				String raw = idx < logLines.size() ? stripAnsi(logLines.get(idx)) : "";
				String lower = raw.toLowerCase();
				if (raw.startsWith("[ERROR]") || lower.contains("error") || lower.contains("failed")) {
					g.setForegroundColor(TextColor.ANSI.RED);
				} else if (raw.startsWith("[WARN]") || lower.contains("warning") || lower.contains("timed out")) {
					g.setForegroundColor(TextColor.ANSI.YELLOW);
				} else {
					g.setForegroundColor(TextColor.ANSI.DEFAULT);
				}
				g.putString(0, 2 + r, pad(raw, w));
			}

			screen.refresh();
		} catch (IOException ignored) {
		}
	}

	private String barLanterna(int width) {
		int barW = Math.max(10, Math.min(30, width - 30));
		int filled = progressTotal == 0 ? 0 : (int) ((double) currentProgress / progressTotal * barW);
		String bar = "#".repeat(filled) + "-".repeat(Math.max(0, barW - filled));
		int pct = progressTotal == 0 ? 0 : (int) ((double) currentProgress / progressTotal * 100);
		return "  [" + bar + "] " + pct + "%  Current: " + currentStudent;
	}

	private static String pad(String s, int width) {
		if (s.length() >= width)
			return s.substring(0, width);
		return s + " ".repeat(width - s.length());
	}

	private static String stripAnsi(String s) {
		return s.replaceAll("\u001B\\[[\\d;]*[A-Za-z]", "");
	}

	// ── Simple fallback rendering
	// ─────────────────────────────────────────────────

	private String barSimple() {
		int barW = 30;
		int filled = progressTotal == 0 ? 0 : (int) ((double) currentProgress / progressTotal * barW);
		String bar = "#".repeat(filled) + "-".repeat(Math.max(0, barW - filled));
		int pct = progressTotal == 0 ? 0 : (int) ((double) currentProgress / progressTotal * 100);
		String line = "  [" + bar + "] " + pct + "%  Current: " + currentStudent;
		int cols = terminalWidth();
		return cols > 10 && line.length() > cols ? line.substring(0, cols - 1) : line;
	}

	private void redrawBarSimple() {
		System.out.print("\r\u001B[2K" + barSimple());
		System.out.flush();
	}

	private int terminalWidth() {
		String env = System.getenv("COLUMNS");
		if (env != null) {
			try {
				return Integer.parseInt(env.trim());
			} catch (NumberFormatException ignored) {
			}
		}
		try {
			Process p = new ProcessBuilder("sh", "-c", "tput cols 2>/dev/null").start();
			String out = new String(p.getInputStream().readAllBytes()).trim();
			if (!out.isEmpty())
				return Integer.parseInt(out);
		} catch (Exception ignored) {
		}
		return 120;
	}

	// ── Logging
	// ───────────────────────────────────────────────────────────────────

	public void beginStudentLog(String name) {
		pendingStudentHeader = name;
		inStudentGroup = false;
	}

	public void logInfo(String message) {
		printLog(message);
	}

	public void logWarning(String message) {
		printLog("[WARN]  " + message);
	}

	public void logError(String message) {
		printLog("[ERROR] " + message);
	}

	public void logRaw(String message) {
		printLog(message);
	}

	private void printLog(String message) {
		if (pendingStudentHeader != null) {
			String header = "[" + TS_FORMATTER.format(Instant.now()) + "] ▸ " + pendingStudentHeader;
			pendingStudentHeader = null;
			inStudentGroup = true;
			emitLogLine(header);
		}
		String indent = inStudentGroup ? "  " : "";
		String log = "[" + TS_FORMATTER.format(Instant.now()) + "] " + indent + message;
		emitLogLine(log);
	}

	private void emitLogLine(String log) {
		if (!progressActive) {
			System.out.println(log);
			return;
		}

		logLines.add(log);
		if (lanternaMode) {
			redrawScreen();
		} else {
			System.out.print("\r\u001B[2K" + log + "\n" + barSimple());
			System.out.flush();
		}
	}

	// ── Summary (printed after progress ends) ────────────────────────────────────

	/**
	 * Print the complete grading summary including scores table and anomalies.
	 */
	public void printSummary(List<StudentSubmission> submissions, List<QuestionConfig> questionConfigs) {
		System.out.println();
		printAnomalies(submissions);
		System.out.println();
		printScoreTable(submissions, questionConfigs);
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
						? RED + "  [ERROR] "
						: YELLOW + "  [WARN]  ";
				System.out.println(icon + sub.getDisplayName() + " - " + anomaly.getDescription() + RESET);
			}
		}
	}

	private void printScoreTable(List<StudentSubmission> submissions, List<QuestionConfig> questionConfigs) {
		System.out.println(BOLD + "GRADING SUMMARY" + RESET);
		System.out.println("=".repeat(75));
		System.out.printf(BOLD + " %-20s", "Student");
		for (QuestionConfig qc : questionConfigs) {
			System.out.printf("| %-5s", qc.getQuestionId());
		}
		System.out.printf("| %-6s%n" + RESET, "Total");
		System.out.println("-".repeat(21) + ("+" + "-".repeat(6)).repeat(questionConfigs.size()) + "+" + "-".repeat(7));
		for (StudentSubmission sub : submissions) {
			System.out.printf(" %-20s", sub.getDisplayName());
			for (QuestionConfig qc : questionConfigs) {
				double score = sub.getResults().stream().filter(r -> r.getQuestionId().equals(qc.getQuestionId()))
						.mapToDouble(QuestionResult::getScore).findFirst().orElse(0.0);
				String color = score >= qc.getMaxScore() ? GREEN : score > 0 ? YELLOW : RED;
				System.out.printf("| %s%-5.1f%s", color, score, RESET);
			}
			double total = sub.getTotalScore();
			double maxTotal = sub.getMaxPossibleScore();
			String totalColor = total >= maxTotal ? GREEN : total > 0 ? CYAN : RED;
			System.out.printf("| %s%-6.1f%s%n", totalColor, total, RESET);
		}
		System.out.println("=".repeat(75));
	}
}

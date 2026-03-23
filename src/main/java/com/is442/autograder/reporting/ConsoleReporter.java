package com.is442.autograder.reporting;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
import com.is442.autograder.model.QuestionConfig;
import com.is442.autograder.model.StudentSubmission;

/**
 * Prints formatted grading progress and summaries to the console.
 */
public class ConsoleReporter {

	private static final String RESET = "\u001B[0m";
	private static final String BOLD = "\u001B[1m";
	private static final String RED = "\u001B[31m";
	private static final String GREEN = "\u001B[32m";
	private static final String YELLOW = "\u001B[33m";
	private static final String CYAN = "\u001B[36m";
	private static final String STOPPING_MESSAGE = "\u26A0 Stopping after current student completes...";

	private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
			.withZone(ZoneId.systemDefault());

	private final ConsoleSummaryPrinter summaryPrinter = new ConsoleSummaryPrinter(RESET, BOLD, RED, GREEN, YELLOW,
			CYAN);
	private final ConsoleLogBuffer logBuffer = new ConsoleLogBuffer();

	// Progress state
	private boolean progressActive = false;
	private int progressTotal = 0;
	private int currentProgress = 0;
	private String currentStudent = "(starting...)";

	// Lanterna state
	private Screen screen;
	private boolean lanternaMode = false;
	private PrintStream savedOut;
	private PrintStream savedErr;

	// Stop-grading flag (set when user presses 'q' in Lanterna mode)
	private volatile boolean stopRequested = false;
	private Thread keyPollerThread = null;

	/** Returns true if the user pressed 'q' to request a graceful stop. */
	public boolean isStopRequested() {
		return stopRequested;
	}

	public void startProgress(int total) {
		progressActive = true;
		progressTotal = total;
		currentProgress = 0;
		currentStudent = "(starting...)";
		logBuffer.clear();

		try {
			Terminal terminal = new DefaultTerminalFactory().createTerminal();
			screen = new TerminalScreen(terminal);
			screen.startScreen();
			screen.setCursorPosition(null);

			// Suppress stdout/stderr so stray System.out calls don't corrupt the TUI.
			savedOut = System.out;
			savedErr = System.err;
			PrintStream sink = new PrintStream(OutputStream.nullOutputStream());
			System.setOut(sink);
			System.setErr(sink);

			lanternaMode = true;
			redrawScreen();

			keyPollerThread = new Thread(() -> {
				while (!Thread.currentThread().isInterrupted() && lanternaMode) {
					try {
						KeyStroke key = screen.readInput();
						if (key != null && key.getKeyType() == KeyType.Character) {
							char c = key.getCharacter();
							if ((c == 'q' || c == 'Q') && !stopRequested) {
								stopRequested = true;
								logBuffer.addRawLine(TS_FORMATTER, STOPPING_MESSAGE);
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
			// Not a real terminal; fall back to a simple progress bar.
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
		if (lanternaMode) {
			redrawScreen();
		} else {
			redrawBarSimple();
		}
	}

	public void printProgress(int current, int total, String studentName) {
		if (!progressActive) {
			startProgress(total);
		}
		this.currentProgress = current;
		this.progressTotal = total;
		this.currentStudent = studentName;
		if (lanternaMode) {
			redrawScreen();
		} else {
			redrawBarSimple();
		}
	}

	public void endProgress() {
		if (!progressActive) {
			return;
		}
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

			for (String line : logBuffer.lines()) {
				System.out.println(line);
			}
		} else {
			System.out.print("\r\u001B[2K" + barSimple() + "\n");
			System.out.flush();
		}
	}

	private void redrawScreen() {
		if (screen == null) {
			return;
		}
		try {
			screen.doResizeIfNecessary();
			TerminalSize size = screen.getTerminalSize();
			int height = size.getRows();
			int width = size.getColumns();
			TextGraphics graphics = screen.newTextGraphics();
			graphics.setBackgroundColor(TextColor.ANSI.DEFAULT);

			graphics.setForegroundColor(TextColor.ANSI.CYAN);
			graphics.putString(0, 0, pad(barLanterna(width), width));

			if (stopRequested) {
				graphics.setForegroundColor(TextColor.ANSI.RED);
				graphics.putString(0, 1,
						pad("  \u26A0  Stopping \u2014 waiting for current student to finish...", width));
			} else {
				graphics.setForegroundColor(TextColor.ANSI.YELLOW);
				graphics.putString(0, 1, pad("  Press q to stop grading after the current student finishes.", width));
			}

			graphics.setForegroundColor(TextColor.ANSI.WHITE);
			graphics.putString(0, 2, "-".repeat(width));

			List<String> lines = logBuffer.lines();
			int logRows = Math.max(0, height - 3);
			int start = Math.max(0, lines.size() - logRows);
			for (int row = 0; row < logRows; row++) {
				int index = start + row;
				String raw = index < lines.size() ? stripAnsi(lines.get(index)) : "";
				String lower = raw.toLowerCase();
				if (raw.startsWith("[ERROR]") || lower.contains("error") || lower.contains("failed")) {
					graphics.setForegroundColor(TextColor.ANSI.RED);
				} else if (raw.startsWith("[WARN]") || lower.contains("warning") || lower.contains("timed out")) {
					graphics.setForegroundColor(TextColor.ANSI.YELLOW);
				} else {
					graphics.setForegroundColor(TextColor.ANSI.DEFAULT);
				}
				graphics.putString(0, 2 + row, pad(raw, width));
			}

			screen.refresh();
		} catch (IOException ignored) {
		}
	}

	private String barLanterna(int width) {
		int barWidth = Math.max(10, Math.min(30, width - 30));
		int filled = progressTotal == 0 ? 0 : (int) ((double) currentProgress / progressTotal * barWidth);
		String bar = "#".repeat(filled) + "-".repeat(Math.max(0, barWidth - filled));
		int pct = progressTotal == 0 ? 0 : (int) ((double) currentProgress / progressTotal * 100);
		return "  [" + bar + "] " + pct + "%  Current: " + currentStudent;
	}

	private static String pad(String value, int width) {
		if (value.length() >= width) {
			return value.substring(0, width);
		}
		return value + " ".repeat(width - value.length());
	}

	private static String stripAnsi(String value) {
		return value.replaceAll("\u001B\\[[\\d;]*[A-Za-z]", "");
	}

	private String barSimple() {
		int barWidth = 30;
		int filled = progressTotal == 0 ? 0 : (int) ((double) currentProgress / progressTotal * barWidth);
		String bar = "#".repeat(filled) + "-".repeat(Math.max(0, barWidth - filled));
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
			Process process = new ProcessBuilder("sh", "-c", "tput cols 2>/dev/null").start();
			String out = new String(process.getInputStream().readAllBytes()).trim();
			if (!out.isEmpty()) {
				return Integer.parseInt(out);
			}
		} catch (Exception ignored) {
		}
		return 120;
	}

	public void beginStudentLog(String name) {
		logBuffer.beginStudentLog(name);
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
		String log = logBuffer.formatLogLine(TS_FORMATTER, message);
		emitLogLine(log);
	}

	private void emitLogLine(String log) {
		if (!progressActive) {
			System.out.println(log);
			return;
		}

		if (lanternaMode) {
			redrawScreen();
		} else {
			System.out.print("\r\u001B[2K" + log + "\n" + barSimple());
			System.out.flush();
		}
	}

	/**
	 * Print the complete grading summary including scores table and anomalies.
	 */
	public void printSummary(List<StudentSubmission> submissions, List<QuestionConfig> questionConfigs) {
		summaryPrinter.printSummary(submissions, questionConfigs);
	}
}

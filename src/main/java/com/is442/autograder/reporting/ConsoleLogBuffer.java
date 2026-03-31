package com.is442.autograder.reporting;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Buffers formatted console log lines and manages per-student grouping.
 */
final class ConsoleLogBuffer {

	private final List<String> logLines = new ArrayList<>();
	private String pendingStudentHeader;
	private boolean inStudentGroup;

	void clear() {
		logLines.clear();
		pendingStudentHeader = null;
		inStudentGroup = false;
	}

	void beginStudentLog(String name) {
		pendingStudentHeader = name;
		inStudentGroup = false;
	}

	void addRawLine(DateTimeFormatter formatter, String message) {
		logLines.add("[" + formatter.format(Instant.now()) + "] " + message);
	}

	List<String> lines() {
		return Collections.unmodifiableList(logLines);
	}

	String formatLogLine(DateTimeFormatter formatter, String message) {
		if (pendingStudentHeader != null) {
			String header = "[" + formatter.format(Instant.now()) + "] \u25B8 " + pendingStudentHeader;
			pendingStudentHeader = null;
			inStudentGroup = true;
			logLines.add(header);
		}
		String indent = inStudentGroup ? "  " : "";
		String log = "[" + formatter.format(Instant.now()) + "] " + indent + message;
		logLines.add(log);
		return log;
	}
}

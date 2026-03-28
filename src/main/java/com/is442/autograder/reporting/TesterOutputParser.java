package com.is442.autograder.reporting;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared parser for tester stdout/stderr used in report generation.
 */
final class TesterOutputParser {

	record ParsedTestResult(String testNumber, String status, String expected, String actual, String failReason,
			String methodCall) {
	}

	record ParsedException(String simpleClassName, String message, String studentSourceFile, String lineNumber) {
	}

	private TesterOutputParser() {
	}

	static List<ParsedTestResult> parseTesterOutput(String stdout) {
		List<ParsedTestResult> results = new ArrayList<>();
		if (stdout == null || stdout.isBlank()) {
			return results;
		}
		String testNum = null;
		String methodCall = null;
		String expected = null;
		String actual = null;
		String failReason = null;
		boolean inTest = false;

		for (String rawLine : stdout.split("\n")) {
			String line = rawLine.trim();
			if (line.isEmpty()) {
				continue;
			}
			try {
				Double.parseDouble(line);
				continue;
			} catch (NumberFormatException ignored) {
			}
			if (line.startsWith("-") && line.chars().filter(c -> c == '-').count() > 5) {
				continue;
			}
			if (line.matches("Test \\d+:.*")) {
				int colonIdx = line.indexOf(':');
				testNum = line.substring(5, colonIdx).trim();
				methodCall = line.substring(colonIdx + 1).trim();
				expected = null;
				actual = null;
				failReason = null;
				inTest = true;
				continue;
			}
			if (!inTest) {
				continue;
			}
			if (line.equals("Passed")) {
				results.add(new ParsedTestResult(testNum, "PASS", expected, actual, failReason, methodCall));
				inTest = false;
				continue;
			}
			if (line.startsWith("Failed -> ")) {
				failReason = line.substring("Failed -> ".length()).trim();
				results.add(new ParsedTestResult(testNum, "FAIL", expected, actual, failReason, methodCall));
				inTest = false;
				continue;
			}
			if (line.equals("Failed")) {
				results.add(new ParsedTestResult(testNum, "FAIL", expected, actual, failReason, methodCall));
				inTest = false;
				continue;
			}
			if (line.startsWith("Expected")) {
				int barStart = line.indexOf(":|");
				int barEnd = line.lastIndexOf('|');
				if (barStart >= 0 && barEnd > barStart + 1) {
					expected = line.substring(barStart + 2, barEnd);
				}
				continue;
			}
			if (line.startsWith("Actual")) {
				int barStart = line.indexOf(":|");
				int barEnd = line.lastIndexOf('|');
				if (barStart >= 0 && barEnd > barStart + 1) {
					actual = line.substring(barStart + 2, barEnd);
				}
			}
		}
		return results;
	}

	static List<ParsedException> parseExceptions(String stderr) {
		List<ParsedException> results = new ArrayList<>();
		if (stderr == null || stderr.isBlank()) {
			return results;
		}
		String exClass = null;
		String exMessage = null;
		String exFile = null;
		String exLine = null;
		boolean inException = false;

		for (String rawLine : stderr.split("\n")) {
			String line = rawLine.trim();
			if (line.isEmpty()) {
				continue;
			}
			if (line.startsWith("at ")) {
				if (inException && exFile == null) {
					java.util.regex.Matcher m = java.util.regex.Pattern.compile("at [^(]+\\(([^:)]+):?(\\d*)\\)")
							.matcher(line);
					if (m.find()) {
						String file = m.group(1);
						String lineNum = m.group(2);
						if (!file.contains("Tester")) {
							exFile = file;
							exLine = lineNum;
						}
					}
				}
				continue;
			}
			if (!rawLine.startsWith("\t") && !rawLine.startsWith("    ")) {
				if (inException) {
					results.add(new ParsedException(exClass, exMessage, exFile, exLine));
				}
				int colonIdx = line.indexOf(": ");
				String fullClass = colonIdx >= 0 ? line.substring(0, colonIdx) : line;
				int dotIdx = fullClass.lastIndexOf('.');
				exClass = dotIdx >= 0 ? fullClass.substring(dotIdx + 1) : fullClass;
				exMessage = colonIdx >= 0 ? line.substring(colonIdx + 2) : null;
				exFile = null;
				exLine = null;
				inException = true;
			}
		}
		if (inException) {
			results.add(new ParsedException(exClass, exMessage, exFile, exLine));
		}
		return results;
	}
}

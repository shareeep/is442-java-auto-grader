package com.is442.autograder.reporting;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.is442.autograder.model.Anomaly;
import com.is442.autograder.model.QuestionConfig;
import com.is442.autograder.model.QuestionResult;
import com.is442.autograder.model.StudentSubmission;

/**
 * Generates a comprehensive instructor report summarising detected anomalies,
 * common mistakes, class-level insights, and per-student diagnostics.
 *
 * <p>
 * The report is fully data-driven: question names, counts, and assessment
 * details are read from the runtime configuration and grading results — nothing
 * is hardcoded. The same generator works for any assignment.
 *
 * <p>
 * Report sections:
 * <ol>
 * <li>Grading Overview (class-level statistics + question performance)</li>
 * <li>Anomaly Summary (categorised anomaly counts + common-mistake table)</li>
 * <li>Structural Validation (per-student folder-structure status)</li>
 * <li>Execution &amp; Grading Diagnostics (compilation / runtime table)</li>
 * <li>Per-Student Results (scores + error snippets)</li>
 * <li>Instructor Insights (observations + recommendations)</li>
 * <li>Error Log Appendix (full error messages)</li>
 * </ol>
 */
public class InstructorReportGenerator {

	// ── Anomaly classification sets (purely enum-based, no hardcoding) ───────

	private static final Set<Anomaly.Type> STRUCTURAL_TYPES = EnumSet.of(Anomaly.Type.FOLDER_NOT_RENAMED,
			Anomaly.Type.STUDENT_ID_AS_FOLDER, Anomaly.Type.NO_PARENT_FOLDER, Anomaly.Type.EXTRA_NESTING,
			Anomaly.Type.MISSING_QUESTION_FOLDER, Anomaly.Type.MISSING_JAVA_FILE, Anomaly.Type.IDENTITY_MISMATCH);

	private static final Set<Anomaly.Type> METADATA_TYPES = EnumSet.of(Anomaly.Type.MISSING_HEADER,
			Anomaly.Type.INCOMPLETE_HEADER);

	private static final Set<Anomaly.Type> EXECUTION_TYPES = EnumSet.of(Anomaly.Type.COMPILATION_ERROR,
			Anomaly.Type.RUNTIME_ERROR, Anomaly.Type.EXECUTION_TIMEOUT);

	// ── Formatting constants ──────────────────────────────────────────────────

	private static final String THICK_LINE = "═".repeat(72);
	private static final String THIN_LINE = "─".repeat(72);
	private static final String SECTION_PAD = "  ";
	private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	// ── Fields ────────────────────────────────────────────────────────────────

	private final String assessmentName;

	public InstructorReportGenerator(String assessmentName) {
		this.assessmentName = assessmentName;
	}

	// ══════════════════════════════════════════════════════════════════════════
	// Public entry point
	// ══════════════════════════════════════════════════════════════════════════

	/**
	 * Generate the instructor report and write it to {@code outputPath}.
	 *
	 * @param submissions
	 *            all graded student submissions
	 * @param questionConfigs
	 *            question configuration list (defines IDs, max scores, etc.)
	 * @param outputPath
	 *            destination file path
	 */
	public void generate(List<StudentSubmission> submissions, List<QuestionConfig> questionConfigs, Path outputPath)
			throws IOException {

		Files.createDirectories(outputPath.getParent());

		// Sort submissions by OrgDefinedId, then by display name
		List<StudentSubmission> sorted = submissions.stream()
				.sorted(Comparator.comparing((StudentSubmission s) -> normalizeOrgId(s.getOrgDefinedId()))
						.thenComparing(StudentSubmission::getDisplayName, String.CASE_INSENSITIVE_ORDER))
				.collect(Collectors.toList());

		try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(outputPath))) {
			writeReportHeader(w);
			writeGradingOverview(w, sorted, questionConfigs);
			writeAnomalySummary(w, sorted);
			writeStructuralValidation(w, sorted, questionConfigs);
			writeExecutionDiagnostics(w, sorted, questionConfigs);
			writePerStudentResults(w, sorted, questionConfigs);
			writeInstructorInsights(w, sorted, questionConfigs);
			writeReportFooter(w);
		}
	}

	// ══════════════════════════════════════════════════════════════════════════
	// Section: Report Header
	// ══════════════════════════════════════════════════════════════════════════

	private void writeReportHeader(PrintWriter w) {
		w.println(THICK_LINE);
		w.println(SECTION_PAD + "AUTO-GRADER — INSTRUCTOR REPORT");
		w.println(SECTION_PAD + "Assessment : " + assessmentName);
		w.println(SECTION_PAD + "Generated  : " + TS_FMT.format(LocalDateTime.now()));
		w.println(THICK_LINE);
		w.println();
	}

	private void writeReportFooter(PrintWriter w) {
		w.println();
		w.println(THICK_LINE);
		w.println(SECTION_PAD + "END OF REPORT");
		w.println(THICK_LINE);
	}

	// ══════════════════════════════════════════════════════════════════════════
	// Section 1: Grading Overview
	// ══════════════════════════════════════════════════════════════════════════

	private void writeGradingOverview(PrintWriter w, List<StudentSubmission> subs, List<QuestionConfig> qcs) {

		sectionHeader(w, "1", "GRADING OVERVIEW");

		int total = subs.size();
		long withCritical = subs.stream()
				.filter(s -> s.getAnomalies().stream().anyMatch(a -> a.getSeverity() == Anomaly.Severity.ERROR))
				.count();
		long successful = total - withCritical;

		double maxPossible = qcs.stream().mapToDouble(QuestionConfig::getMaxScore).sum();
		double avg = subs.stream().mapToDouble(StudentSubmission::getTotalScore).average().orElse(0);
		double highest = subs.stream().mapToDouble(StudentSubmission::getTotalScore).max().orElse(0);
		double lowest = subs.stream().mapToDouble(StudentSubmission::getTotalScore).min().orElse(0);
		long passing = subs.stream().filter(s -> maxPossible > 0 && s.getTotalScore() / maxPossible >= 0.5).count();
		long totalAnomalies = subs.stream().mapToLong(s -> s.getAnomalies().size()).sum();

		w.println(SECTION_PAD + "Total Submissions Processed  : " + total);
		w.println(SECTION_PAD + "Submissions Fully Processed  : " + successful);
		w.println(SECTION_PAD + "Submissions With Errors      : " + withCritical);
		w.println(SECTION_PAD + "Passing (>= 50%)             : " + passing + " / " + total);
		w.println(SECTION_PAD + "Total Anomalies Detected     : " + totalAnomalies);
		w.println();
		w.println(SECTION_PAD + "Average Score  : " + fmt(avg) + " / " + fmt(maxPossible));
		w.println(SECTION_PAD + "Highest Score  : " + fmt(highest) + " / " + fmt(maxPossible));
		w.println(SECTION_PAD + "Lowest Score   : " + fmt(lowest) + " / " + fmt(maxPossible));
		w.println();

		// Score distribution
		w.println(SECTION_PAD + "Score Distribution");
		w.println(SECTION_PAD + "─".repeat(36));
		int binWidth = Math.max(1, (int) Math.ceil(maxPossible / 5.0));
		for (int lo = 0; lo < (int) maxPossible; lo += binWidth) {
			int hi = (int) Math.min(lo + binWidth - 1, maxPossible - 1);
			final double flo = lo;
			final double fhi = hi;
			long cnt = subs.stream().filter(s -> s.getTotalScore() >= flo && s.getTotalScore() <= fhi).count();
			String range = (lo == hi) ? String.valueOf(lo) : lo + " – " + hi;
			w.println(SECTION_PAD + "  " + rpad(range, 10) + ": " + cnt + " student" + (cnt == 1 ? "" : "s"));
		}
		long perfect = subs.stream().filter(s -> s.getTotalScore() >= maxPossible).count();
		w.println(SECTION_PAD + "  " + rpad((int) maxPossible + " (full)", 10) + ": " + perfect + " student"
				+ (perfect == 1 ? "" : "s"));
		w.println();

		// Question performance table
		w.println(SECTION_PAD + "Question Performance");
		w.println(SECTION_PAD + THIN_LINE.substring(0, 56));
		w.println(SECTION_PAD + tblRow("Question", "Max Pts", "Avg Score", "Pass Rate", "Partial Rate"));
		w.println(SECTION_PAD + THIN_LINE.substring(0, 56));

		QuestionConfig lowestPassQ = null;
		double lowestPassRate = Double.MAX_VALUE;

		for (QuestionConfig qc : qcs) {
			double avgScore = avgScoreForQuestion(subs, qc);
			double passRate = passRateForQuestion(subs, qc);
			double partialRate = partialRateForQuestion(subs, qc);

			w.println(SECTION_PAD + tblRow(qc.getQuestionId(), fmt(qc.getMaxScore()),
					fmt(avgScore) + " / " + fmt(qc.getMaxScore()), pct(passRate), pct(partialRate)));

			if (passRate < lowestPassRate) {
				lowestPassRate = passRate;
				lowestPassQ = qc;
			}
		}
		w.println(SECTION_PAD + THIN_LINE.substring(0, 56));
		w.println();

		// Quick insight
		w.println(SECTION_PAD + "Key Insight");
		if (lowestPassQ != null) {
			w.println(SECTION_PAD + "  • " + lowestPassQ.getQuestionId() + " had the lowest full-pass rate ("
					+ pct(lowestPassRate) + ") — students struggled most here.");
		}
		long zeroCount = subs.stream().filter(s -> s.getTotalScore() == 0).count();
		if (zeroCount > 0) {
			String zeroLabel = zeroCount == 1 ? "1 submission scored" : zeroCount + " submissions scored";
			w.println(SECTION_PAD + "  • " + zeroLabel + " zero overall.");
		}
		w.println();
	}

	// ══════════════════════════════════════════════════════════════════════════
	// Section 2: Anomaly Summary
	// ══════════════════════════════════════════════════════════════════════════

	private void writeAnomalySummary(PrintWriter w, List<StudentSubmission> subs) {
		sectionHeader(w, "2", "ANOMALY SUMMARY");

		// Count per anomaly type across all submissions
		Map<Anomaly.Type, Long> typeCounts = new EnumMap<>(Anomaly.Type.class);
		for (StudentSubmission sub : subs) {
			for (Anomaly a : sub.getAnomalies()) {
				typeCounts.merge(a.getType(), 1L, Long::sum);
			}
		}

		if (typeCounts.isEmpty()) {
			w.println(SECTION_PAD + "No anomalies detected across all submissions.");
			w.println();
			return;
		}

		// Structural
		w.println(SECTION_PAD + "Submission Structure Issues");
		w.println(SECTION_PAD + "─".repeat(40));
		long structTotal = printAnomalyCategory(w, typeCounts, STRUCTURAL_TYPES);
		if (structTotal == 0) {
			w.println(SECTION_PAD + "  (none)");
		}
		w.println();

		// Metadata
		w.println(SECTION_PAD + "Code Metadata Issues");
		w.println(SECTION_PAD + "─".repeat(40));
		long metaTotal = printAnomalyCategory(w, typeCounts, METADATA_TYPES);
		if (metaTotal == 0) {
			w.println(SECTION_PAD + "  (none)");
		}
		w.println();

		// Execution
		w.println(SECTION_PAD + "Execution Issues");
		w.println(SECTION_PAD + "─".repeat(40));
		long execTotal = printAnomalyCategory(w, typeCounts, EXECUTION_TYPES);
		if (execTotal == 0) {
			w.println(SECTION_PAD + "  (none)");
		}
		w.println();

		// Affected students per anomaly type (top issues table)
		w.println(SECTION_PAD + "Common Issues by Frequency");
		w.println(SECTION_PAD + THIN_LINE.substring(0, 52));
		w.println(SECTION_PAD + tbl2Row("Issue Type", "Occurrences"));
		w.println(SECTION_PAD + THIN_LINE.substring(0, 52));

		typeCounts.entrySet().stream().sorted(Map.Entry.<Anomaly.Type, Long>comparingByValue().reversed()).forEach(
				e -> w.println(SECTION_PAD + tbl2Row(friendlyAnomalyName(e.getKey()), String.valueOf(e.getValue()))));

		w.println(SECTION_PAD + THIN_LINE.substring(0, 52));
		w.println();
	}

	private long printAnomalyCategory(PrintWriter w, Map<Anomaly.Type, Long> counts, Set<Anomaly.Type> category) {
		long total = 0;
		for (Anomaly.Type t : category) {
			long c = counts.getOrDefault(t, 0L);
			if (c > 0) {
				w.println(SECTION_PAD + "  " + lpad(friendlyAnomalyName(t), 36) + " : " + c);
				total += c;
			}
		}
		return total;
	}

	// ══════════════════════════════════════════════════════════════════════════
	// Section 3: Structural Validation
	// ══════════════════════════════════════════════════════════════════════════

	private void writeStructuralValidation(PrintWriter w, List<StudentSubmission> subs, List<QuestionConfig> qcs) {

		sectionHeader(w, "3", "STRUCTURAL VALIDATION");

		w.println(SECTION_PAD + "Checks that each submission follows the required folder structure.");
		w.println(SECTION_PAD + "Expected: <username>/<question-folder>/<java-files>");
		w.println();

		w.println(SECTION_PAD + THIN_LINE.substring(0, 68));
		w.println(SECTION_PAD + tblStructRow("Student", "Status", "Issues"));
		w.println(SECTION_PAD + THIN_LINE.substring(0, 68));

		for (StudentSubmission sub : subs) {
			List<Anomaly> structural = anomaliesInCategory(sub, STRUCTURAL_TYPES);
			String status = structural.isEmpty() ? "OK" : "ISSUES";
			String issues = structural.isEmpty()
					? "None"
					: structural.stream().map(a -> friendlyAnomalyName(a.getType())).distinct()
							.collect(Collectors.joining(", "));
			w.println(SECTION_PAD + tblStructRow(sub.getDisplayName(), status, issues));
		}

		w.println(SECTION_PAD + THIN_LINE.substring(0, 68));
		w.println();

		// Detail blocks for students with issues
		boolean anyIssue = subs.stream().anyMatch(s -> !anomaliesInCategory(s, STRUCTURAL_TYPES).isEmpty());

		if (anyIssue) {
			w.println(SECTION_PAD + "Detail — Submissions With Structural Issues");
			w.println();
			for (StudentSubmission sub : subs) {
				List<Anomaly> structural = anomaliesInCategory(sub, STRUCTURAL_TYPES);
				if (structural.isEmpty()) {
					continue;
				}
				w.println(SECTION_PAD + "  Student: " + sub.getDisplayName());
				for (Anomaly a : structural) {
					String qPart = a.getQuestionId() != null ? " [" + a.getQuestionId() + "]" : "";
					w.println(SECTION_PAD + "    ⚠" + qPart + " " + a.getDescription());
				}
				w.println();
			}
		}
	}

	// ══════════════════════════════════════════════════════════════════════════
	// Section 4: Execution & Grading Diagnostics
	// ══════════════════════════════════════════════════════════════════════════

	private void writeExecutionDiagnostics(PrintWriter w, List<StudentSubmission> subs, List<QuestionConfig> qcs) {

		sectionHeader(w, "4", "EXECUTION & GRADING DIAGNOSTICS");

		// Header row: Student | Q1a | Q1b | ... | Runtime Notes
		int nameCol = 22;
		int qCol = 9;
		int notesCol = 20;

		StringBuilder header = new StringBuilder();
		header.append(SECTION_PAD).append(rpad("Student", nameCol));
		for (QuestionConfig qc : qcs) {
			header.append(rpad(qc.getQuestionId(), qCol));
		}
		header.append("Notes");
		w.println(header);

		int totalWidth = nameCol + qCol * qcs.size() + notesCol;
		w.println(SECTION_PAD + "─".repeat(Math.min(totalWidth, 72)));

		for (StudentSubmission sub : subs) {
			StringBuilder row = new StringBuilder();
			row.append(SECTION_PAD).append(rpad(truncate(sub.getDisplayName(), nameCol - 1), nameCol));

			List<String> notes = new ArrayList<>();

			for (QuestionConfig qc : qcs) {
				Optional<QuestionResult> resultOpt = sub.getResults().stream()
						.filter(r -> r.getQuestionId().equals(qc.getQuestionId())).findFirst();

				if (resultOpt.isEmpty()) {
					row.append(rpad("MISSING", qCol));
					notes.add(qc.getQuestionId() + ": missing");
					continue;
				}

				QuestionResult qr = resultOpt.get();
				String cell;

				if (!qr.isCompiled()) {
					if ("Question files not found".equals(qr.getErrorMessage())) {
						cell = "MISSING";
						notes.add(qc.getQuestionId() + ": files missing");
					} else {
						cell = "ERR-COMP";
						notes.add(qc.getQuestionId() + ": compile error");
					}
				} else if (!qr.isExecuted()) {
					if (qr.getErrorMessage() != null && qr.getErrorMessage().contains("timed out")) {
						cell = "TIMEOUT";
						notes.add(qc.getQuestionId() + ": timeout");
					} else {
						cell = "ERR-RUN";
						notes.add(qc.getQuestionId() + ": runtime error");
					}
				} else if (qr.getScore() >= qr.getMaxScore()) {
					cell = "PASS";
				} else if (qr.getScore() > 0) {
					cell = "PARTIAL";
				} else {
					cell = "FAIL";
				}

				row.append(rpad(cell, qCol));
			}

			String notesStr = notes.isEmpty() ? "—" : String.join("; ", notes);
			row.append(truncate(notesStr, notesCol));
			w.println(row);
		}

		w.println(SECTION_PAD + "─".repeat(Math.min(nameCol + qCol * qcs.size() + notesCol, 72)));
		w.println();
		w.println(SECTION_PAD + "Legend: PASS=full marks  PARTIAL=some marks  FAIL=no marks");
		w.println(SECTION_PAD + "        ERR-COMP=compilation failed  ERR-RUN=runtime error");
		w.println(SECTION_PAD + "        TIMEOUT=execution exceeded time limit  MISSING=file not found");
		w.println();

		// Detailed timeout blocks
		boolean anyTimeout = subs.stream()
				.anyMatch(s -> s.getAnomalies().stream().anyMatch(a -> a.getType() == Anomaly.Type.EXECUTION_TIMEOUT));

		if (anyTimeout) {
			w.println(SECTION_PAD + "Timeout Detail");
			w.println(SECTION_PAD + "─".repeat(40));
			for (StudentSubmission sub : subs) {
				for (Anomaly a : sub.getAnomalies()) {
					if (a.getType() != Anomaly.Type.EXECUTION_TIMEOUT) {
						continue;
					}
					String qId = a.getQuestionId() != null ? a.getQuestionId() : "?";
					Optional<QuestionResult> qr = sub.getResults().stream().filter(r -> r.getQuestionId().equals(qId))
							.findFirst();
					double partial = qr.map(QuestionResult::getScore).orElse(0.0);
					double max = qr.map(QuestionResult::getMaxScore).orElse(0.0);
					w.println(SECTION_PAD + "  Student : " + sub.getDisplayName());
					w.println(SECTION_PAD + "  Question: " + qId);
					w.println(SECTION_PAD + "  Action  : Process terminated after time limit exceeded");
					w.println(SECTION_PAD + "  Score   : " + fmt(partial) + " / " + fmt(max) + " (partial credit)");
					w.println();
				}
			}
		}
	}

	// ══════════════════════════════════════════════════════════════════════════
	// Section 5: Per-Student Results
	// ══════════════════════════════════════════════════════════════════════════

	private void writePerStudentResults(PrintWriter w, List<StudentSubmission> subs, List<QuestionConfig> qcs) {

		sectionHeader(w, "5", "PER-STUDENT RESULTS");

		double classMax = qcs.stream().mapToDouble(QuestionConfig::getMaxScore).sum();

		for (int i = 0; i < subs.size(); i++) {
			StudentSubmission sub = subs.get(i);

			w.println(SECTION_PAD + THIN_LINE);
			String orgId = sub.getOrgDefinedId() != null ? "   OrgId: " + sub.getOrgDefinedId() : "";
			w.println(SECTION_PAD + "[" + (i + 1) + "] "
					+ (sub.getName() != null && !sub.getName().isEmpty() ? sub.getName() : sub.getDisplayName()) + " ("
					+ sub.getDisplayName() + ")" + orgId);
			w.println(SECTION_PAD + "    TOTAL SCORE: " + fmt(sub.getTotalScore()) + " / " + fmt(classMax));
			w.println(SECTION_PAD + THIN_LINE);

			// Non-execution anomalies (structural / metadata)
			List<Anomaly> allIssues = sub.getAnomalies();
			boolean hasNonExecIssues = allIssues.stream().anyMatch(a -> !EXECUTION_TYPES.contains(a.getType()));
			if (!hasNonExecIssues) {
				w.println(SECTION_PAD + "  Structure / Metadata: OK — no submission issues.");
			} else {
				long nonExecCount = allIssues.stream().filter(a -> !EXECUTION_TYPES.contains(a.getType())).count();
				w.println(SECTION_PAD + "  Submission Issues (" + nonExecCount + "):");
				for (Anomaly a : allIssues) {
					if (EXECUTION_TYPES.contains(a.getType())) {
						continue;
					}
					String icon = a.getSeverity() == Anomaly.Severity.ERROR ? "  ✖ " : "  ⚠ ";
					String qPart = a.getQuestionId() != null ? "[" + a.getQuestionId() + "] " : "";
					w.println(SECTION_PAD + icon + qPart + a.getDescription());
				}
			}

			w.println();
			w.println(SECTION_PAD + "  Question Results:");
			w.println();

			for (QuestionConfig qc : qcs) {
				Optional<QuestionResult> resultOpt = sub.getResults().stream()
						.filter(r -> r.getQuestionId().equals(qc.getQuestionId())).findFirst();

				if (resultOpt.isEmpty()) {
					w.println(SECTION_PAD + "    " + rpad(qc.getQuestionId(), 6)
							+ rpad("0 / " + fmt(qc.getMaxScore()), 12) + "[MISSING]");
					w.println(SECTION_PAD + "       No question folder or files found.");
					w.println();
					continue;
				}

				QuestionResult qr = resultOpt.get();
				String status = scoreStatus(qr);
				w.println(SECTION_PAD + "    " + rpad(qc.getQuestionId(), 6)
						+ rpad(fmt(qr.getScore()) + " / " + fmt(qr.getMaxScore()), 12) + "[" + status + "]");

				if ("PASS".equals(status)) {
					w.println(SECTION_PAD + "       All " + (int) qr.getMaxScore() + " test case(s) passed ✓");

				} else if ("ERR-COMP".equals(status)) {
					w.println(SECTION_PAD + "       Compilation failed:");
					printCompileError(w, qr.getErrorMessage());

				} else if ("TIMEOUT".equals(status)) {
					w.println(SECTION_PAD + "       Execution timed out — possible infinite loop.");
					double ts = qr.getScore();
					String timeoutDetail = ts > 0
							? (int) ts + " test(s) completed before termination"
							: "No tests completed before termination";
					w.println(SECTION_PAD + "       " + timeoutDetail + ".");
					w.println(SECTION_PAD + "       Score: " + fmt(ts) + " / " + fmt(qr.getMaxScore()));

				} else {
					// PARTIAL or FAIL — parse tester output into structured test results
					List<String[]> tests = parseTesterOutput(qr.getOutput());
					List<String[]> exceptions = parseExceptions(qr.getErrorMessage());

					if (tests.isEmpty()) {
						// No parseable tests — fall back to raw error
						if (qr.getErrorMessage() != null && !qr.getErrorMessage().isBlank()) {
							w.println(SECTION_PAD + "       ✖ " + firstLine(qr.getErrorMessage()));
						}
					} else {
						// Passed summary line
						List<String> passedNames = tests.stream().filter(t -> "PASS".equals(t[1]))
								.map(t -> "Test " + t[0]).collect(Collectors.toList());
						if (!passedNames.isEmpty()) {
							w.println(SECTION_PAD + "       ✓ Passed : " + String.join(", ", passedNames));
						}

						// Failed tests — detailed
						int exceptionIdx = 0;
						for (String[] test : tests) {
							if ("PASS".equals(test[1])) {
								continue;
							}
							// test: [number, status, expected, actual, failReason, methodCall]
							w.println(SECTION_PAD + "       ✗ Test " + test[0] + ": " + test[5]);

							String expected = test[2];
							String actual = test[3];
							String failReason = test[4];

							if (expected != null && !expected.isEmpty()) {
								w.println(SECTION_PAD + "          Expected : " + expected);
								w.println(SECTION_PAD + "          Actual   : "
										+ (actual != null ? actual : "(no output)"));
							}

							if ("Exception".equals(failReason) && exceptionIdx < exceptions.size()) {
								String[] ex = exceptions.get(exceptionIdx++);
								w.println(SECTION_PAD + "          Exception: " + ex[0]);
								if (ex[1] != null && !ex[1].isEmpty()) {
									w.println(SECTION_PAD + "          Message  : " + ex[1]);
								}
								if (ex[2] != null && !ex[2].isEmpty()) {
									w.println(SECTION_PAD + "          Location : " + ex[2]
											+ (ex[3] != null && !ex[3].isEmpty() ? ", line " + ex[3] : ""));
								}
							} else if (failReason != null && !failReason.isEmpty() && !"Exception".equals(failReason)) {
								w.println(SECTION_PAD + "          Reason   : " + failReason);
							}
							w.println();
						}
					}
				}
				w.println();
			}
		}

		w.println(SECTION_PAD + THIN_LINE);
		w.println();
	}

	/**
	 * Print a compilation error message, extracting only the relevant error lines
	 * (skipping "Note:" lines and showing at most 6 lines).
	 */
	private void printCompileError(PrintWriter w, String errorMessage) {
		if (errorMessage == null || errorMessage.isBlank()) {
			return;
		}
		int printed = 0;
		for (String line : errorMessage.split("\n")) {
			String t = line.trim();
			if (t.isEmpty() || t.startsWith("Note:")) {
				continue;
			}
			w.println(SECTION_PAD + "       " + t);
			if (++printed >= 6) {
				w.println(SECTION_PAD + "       [... see logs for full error]");
				break;
			}
		}
	}

	/**
	 * Parse tester stdout into a list of test-case results. Each entry:
	 * [testNumber, status, expected, actual, failReason, methodCall] where status
	 * is "PASS" or "FAIL" and failReason is the text after "Failed -> " (e.g.
	 * "Exception", "Expecting a Data Exception", or null).
	 */
	private List<String[]> parseTesterOutput(String stdout) {
		List<String[]> results = new ArrayList<>();
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

			// Skip the final numeric score line
			try {
				Double.parseDouble(line);
				continue;
			} catch (NumberFormatException ignored) {
			}

			// Separator lines ("---..." or "----- Q1a -----")
			if (line.startsWith("-") && line.chars().filter(c -> c == '-').count() > 5) {
				continue;
			}

			// New test case: "Test N: methodCall(args)"
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
				results.add(new String[]{testNum, "PASS", expected, actual, failReason, methodCall});
				inTest = false;
				continue;
			}

			if (line.startsWith("Failed -> ")) {
				failReason = line.substring("Failed -> ".length()).trim();
				results.add(new String[]{testNum, "FAIL", expected, actual, failReason, methodCall});
				inTest = false;
				continue;
			}

			if (line.equals("Failed")) {
				results.add(new String[]{testNum, "FAIL", expected, actual, failReason, methodCall});
				inTest = false;
				continue;
			}

			// Expected :|value|
			if (line.startsWith("Expected")) {
				int barStart = line.indexOf(":|");
				int barEnd = line.lastIndexOf('|');
				if (barStart >= 0 && barEnd > barStart + 1) {
					expected = line.substring(barStart + 2, barEnd);
				}
				continue;
			}

			// Actual :|value|
			if (line.startsWith("Actual")) {
				int barStart = line.indexOf(":|");
				int barEnd = line.lastIndexOf('|');
				if (barStart >= 0 && barEnd > barStart + 1) {
					actual = line.substring(barStart + 2, barEnd);
				}
				continue;
			}
		}

		return results;
	}

	/**
	 * Parse stderr into a list of exception details. Each entry: [simpleClassName,
	 * message, studentSourceFile, lineNumber]. Results are in the same order as
	 * exceptions appear in stderr, so they can be matched positionally to "Failed
	 * -> Exception" test cases.
	 */
	private List<String[]> parseExceptions(String stderr) {
		List<String[]> results = new ArrayList<>();
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

			// Stack frame: "at ClassName.method(File.java:line)"
			if (line.startsWith("at ")) {
				if (inException && exFile == null) {
					// Extract the first frame that belongs to student code (not a Tester class)
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

			// New exception — line does not start with whitespace and does not start with
			// "at "
			// (rawLine has no leading whitespace means it's a new exception header)
			if (!rawLine.startsWith("\t") && !rawLine.startsWith("    ")) {
				if (inException) {
					results.add(new String[]{exClass, exMessage, exFile, exLine});
				}
				// Parse "some.package.ClassName: message" or just "ClassName"
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
			results.add(new String[]{exClass, exMessage, exFile, exLine});
		}

		return results;
	}

	// ══════════════════════════════════════════════════════════════════════════
	// Section 6: Instructor Insights
	// ══════════════════════════════════════════════════════════════════════════

	private void writeInstructorInsights(PrintWriter w, List<StudentSubmission> subs, List<QuestionConfig> qcs) {

		sectionHeader(w, "6", "INSTRUCTOR INSIGHTS");

		int total = subs.size();
		if (total == 0) {
			w.println(SECTION_PAD + "No submissions to analyse.");
			w.println();
			return;
		}

		// Compute metrics
		long compileErrors = subs.stream()
				.filter(s -> s.getAnomalies().stream().anyMatch(a -> a.getType() == Anomaly.Type.COMPILATION_ERROR))
				.count();
		long timeouts = subs.stream()
				.filter(s -> s.getAnomalies().stream().anyMatch(a -> a.getType() == Anomaly.Type.EXECUTION_TIMEOUT))
				.count();
		long structIssues = subs.stream().filter(s -> !anomaliesInCategory(s, STRUCTURAL_TYPES).isEmpty()).count();
		long metaIssues = subs.stream().filter(s -> !anomaliesInCategory(s, METADATA_TYPES).isEmpty()).count();
		long zeroScore = subs.stream().filter(s -> s.getTotalScore() == 0).count();

		double maxPossible = qcs.stream().mapToDouble(QuestionConfig::getMaxScore).sum();
		long passing = subs.stream().filter(s -> maxPossible > 0 && s.getTotalScore() / maxPossible >= 0.5).count();

		// Question with lowest full-pass rate
		QuestionConfig hardestQ = qcs.stream().min(Comparator.comparingDouble(qc -> passRateForQuestion(subs, qc)))
				.orElse(null);
		QuestionConfig easiestQ = qcs.stream().max(Comparator.comparingDouble(qc -> passRateForQuestion(subs, qc)))
				.orElse(null);

		w.println(SECTION_PAD + "Key Observations");
		w.println(SECTION_PAD + "─".repeat(44));
		w.println();

		int obs = 1;

		w.println(SECTION_PAD + "  " + obs++ + ". " + passing + " out of " + total + " submissions ("
				+ pct((double) passing / total) + ") scored 50% or above.");

		if (compileErrors > 0) {
			w.println(SECTION_PAD + "  " + obs++ + ". " + compileErrors + " submission(s) ("
					+ pct((double) compileErrors / total) + ") had compilation errors.");
		}

		if (timeouts > 0) {
			w.println(SECTION_PAD + "  " + obs++ + ". " + timeouts + " submission(s) (" + pct((double) timeouts / total)
					+ ") caused execution timeout — possible infinite loops.");
		}

		if (structIssues > 0) {
			w.println(SECTION_PAD + "  " + obs++ + ". " + structIssues + " submission(s) ("
					+ pct((double) structIssues / total) + ") had folder structure issues.");
		}

		if (metaIssues > 0) {
			w.println(SECTION_PAD + "  " + obs++ + ". " + metaIssues + " submission(s) ("
					+ pct((double) metaIssues / total) + ") had missing or incomplete code header comments.");
		}

		if (zeroScore > 0) {
			String zeroObs = zeroScore == 1 ? "1 submission scored" : zeroScore + " submissions scored";
			w.println(SECTION_PAD + "  " + obs++ + ". " + zeroObs + " zero — may require immediate follow-up.");
		}

		if (hardestQ != null) {
			double rate = passRateForQuestion(subs, hardestQ);
			w.println(SECTION_PAD + "  " + obs++ + ". " + hardestQ.getQuestionId() + " had the lowest full-pass rate ("
					+ pct(rate) + ").");
		}

		if (easiestQ != null && hardestQ != null && !easiestQ.getQuestionId().equals(hardestQ.getQuestionId())) {
			double rate = passRateForQuestion(subs, easiestQ);
			w.println(SECTION_PAD + "  " + obs++ + ". " + easiestQ.getQuestionId() + " had the highest full-pass rate ("
					+ pct(rate) + ").");
		}

		// Per-question failure breakdown
		w.println();
		w.println(SECTION_PAD + "Per-Question Failure Breakdown");
		w.println(SECTION_PAD + "─".repeat(44));
		w.println();

		for (QuestionConfig qc : qcs) {
			long failed = subs.stream().filter(s -> {
				Optional<QuestionResult> r = s.getResults().stream()
						.filter(x -> x.getQuestionId().equals(qc.getQuestionId())).findFirst();
				return r.isEmpty() || r.get().getScore() < r.get().getMaxScore();
			}).count();

			long compFail = subs.stream().filter(s -> s.getResults().stream()
					.anyMatch(r -> r.getQuestionId().equals(qc.getQuestionId()) && !r.isCompiled())).count();

			long toFail = subs.stream()
					.filter(s -> s.getResults().stream()
							.anyMatch(r -> r.getQuestionId().equals(qc.getQuestionId()) && r.isCompiled()
									&& !r.isExecuted() && r.getErrorMessage() != null
									&& r.getErrorMessage().contains("timed out")))
					.count();

			w.println(SECTION_PAD + "  " + qc.getQuestionId() + ": " + failed + " not fully correct"
					+ (compFail > 0 ? ", " + compFail + " compile error(s)" : "")
					+ (toFail > 0 ? ", " + toFail + " timeout(s)" : ""));
		}

		// Recommendations
		w.println();
		w.println(SECTION_PAD + "Recommendations");
		w.println(SECTION_PAD + "─".repeat(44));
		w.println();

		int rec = 1;

		if (hardestQ != null && passRateForQuestion(subs, hardestQ) < 0.6) {
			w.println(SECTION_PAD + "  " + rec++ + ". Revisit " + hardestQ.getQuestionId()
					+ " in future lectures — most students did not achieve full marks.");
		}

		if (structIssues > 0) {
			w.println(SECTION_PAD + "  " + rec++ + ". Remind students to follow the required"
					+ " submission folder structure before the next assessment.");
		}

		if (compileErrors > 0) {
			w.println(SECTION_PAD + "  " + rec++ + ". Encourage students to compile locally"
					+ " before submitting to catch syntax errors.");
		}

		if (timeouts > 0) {
			w.println(SECTION_PAD + "  " + rec++ + ". Highlight infinite loop risks in lab sessions" + " — " + timeouts
					+ " submission(s) timed out.");
		}

		if (metaIssues > 0) {
			w.println(SECTION_PAD + "  " + rec++ + ". Reiterate the requirement for complete"
					+ " Name/Email header comments in all submitted files.");
		}

		if (rec == 1) {
			w.println(SECTION_PAD + "  No specific recommendations — class performed well overall.");
		}

		w.println();
	}

	// ══════════════════════════════════════════════════════════════════════════
	// Helpers — statistics
	// ══════════════════════════════════════════════════════════════════════════

	private double avgScoreForQuestion(List<StudentSubmission> subs, QuestionConfig qc) {
		return subs.stream().flatMap(s -> s.getResults().stream())
				.filter(r -> r.getQuestionId().equals(qc.getQuestionId())).mapToDouble(QuestionResult::getScore)
				.average().orElse(0.0);
	}

	/** Full-pass rate: score == maxScore */
	private double passRateForQuestion(List<StudentSubmission> subs, QuestionConfig qc) {
		if (subs.isEmpty()) {
			return 0.0;
		}
		long passed = subs.stream().flatMap(s -> s.getResults().stream())
				.filter(r -> r.getQuestionId().equals(qc.getQuestionId()) && r.getScore() >= r.getMaxScore()).count();
		return (double) passed / subs.size();
	}

	/** Partial rate: 0 < score < maxScore */
	private double partialRateForQuestion(List<StudentSubmission> subs, QuestionConfig qc) {
		if (subs.isEmpty()) {
			return 0.0;
		}
		long partial = subs.stream().flatMap(s -> s.getResults().stream()).filter(
				r -> r.getQuestionId().equals(qc.getQuestionId()) && r.getScore() > 0 && r.getScore() < r.getMaxScore())
				.count();
		return (double) partial / subs.size();
	}

	private List<Anomaly> anomaliesInCategory(StudentSubmission sub, Set<Anomaly.Type> category) {
		return sub.getAnomalies().stream().filter(a -> category.contains(a.getType())).collect(Collectors.toList());
	}

	private String scoreStatus(QuestionResult qr) {
		if (!qr.isCompiled()) {
			return "Question files not found".equals(qr.getErrorMessage()) ? "MISSING" : "ERR-COMP";
		}
		if (!qr.isExecuted()) {
			return qr.getErrorMessage() != null && qr.getErrorMessage().contains("timed out") ? "TIMEOUT" : "RUN-ERR";
		}
		if (qr.getScore() >= qr.getMaxScore()) {
			return "PASS";
		}
		return qr.getScore() > 0 ? "PARTIAL" : "FAIL";
	}

	// ══════════════════════════════════════════════════════════════════════════
	// Helpers — formatting
	// ══════════════════════════════════════════════════════════════════════════

	private void sectionHeader(PrintWriter w, String num, String title) {
		w.println(THICK_LINE);
		w.println(SECTION_PAD + "SECTION " + num + ": " + title);
		w.println(THICK_LINE);
		w.println();
	}

	/** Fixed-width table row: 3-column structural table */
	private String tblStructRow(String student, String status, String issues) {
		return rpad(student, 26) + rpad(status, 10) + issues;
	}

	/** Fixed-width table row: 5-column question performance table */
	private String tblRow(String q, String max, String avg, String pass, String partial) {
		return rpad(q, 12) + rpad(max, 10) + rpad(avg, 14) + rpad(pass, 12) + partial;
	}

	/** Fixed-width table row: 2-column anomaly count table */
	private String tbl2Row(String label, String count) {
		return rpad(label, 42) + count;
	}

	private String rpad(String s, int width) {
		if (s == null) {
			s = "";
		}
		if (s.length() >= width) {
			return s.substring(0, width);
		}
		return s + " ".repeat(width - s.length());
	}

	private String lpad(String s, int width) {
		if (s == null) {
			s = "";
		}
		if (s.length() >= width) {
			return s;
		}
		return " ".repeat(width - s.length()) + s;
	}

	private String fmt(double v) {
		if (v == Math.floor(v) && !Double.isInfinite(v)) {
			return String.valueOf((long) v);
		}
		return String.format("%.1f", v);
	}

	private String pct(double ratio) {
		return String.format("%.0f%%", ratio * 100);
	}

	private String truncate(String s, int max) {
		if (s == null) {
			return "";
		}
		return s.length() <= max ? s : s.substring(0, max - 1) + "…";
	}

	private String firstLine(String s) {
		if (s == null || s.isBlank()) {
			return "";
		}
		for (String line : s.split("\n")) {
			String t = line.trim();
			if (!t.isEmpty()) {
				return t;
			}
		}
		return s.trim();
	}

	private String normalizeOrgId(String orgId) {
		if (orgId == null) {
			return "";
		}
		return orgId.startsWith("#") ? orgId.substring(1) : orgId;
	}

	/** Human-readable name for each anomaly type. */
	private String friendlyAnomalyName(Anomaly.Type type) {
		return switch (type) {
			case FOLDER_NOT_RENAMED -> "Folder Not Renamed";
			case STUDENT_ID_AS_FOLDER -> "Student ID Used as Folder Name";
			case NO_PARENT_FOLDER -> "Missing Parent Username Folder";
			case EXTRA_NESTING -> "Extra Folder Nesting";
			case MISSING_QUESTION_FOLDER -> "Missing Question Folder";
			case MISSING_JAVA_FILE -> "Missing Java File";
			case IDENTITY_MISMATCH -> "Folder Name / Identity Mismatch";
			case MISSING_HEADER -> "Missing Name/Email Header";
			case INCOMPLETE_HEADER -> "Incomplete Name/Email Header";
			case COMPILATION_ERROR -> "Compilation Error";
			case RUNTIME_ERROR -> "Runtime Error";
			case EXECUTION_TIMEOUT -> "Execution Timeout";
		};
	}
}

package com.is442.autograder.reporting;

import java.awt.Color;
import java.io.FileOutputStream;
import java.io.IOException;
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
import com.is442.autograder.util.StringUtils;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

/**
 * Generates a PDF instructor report that mirrors the six-section structure of
 * the text report, with charts embedded where appropriate.
 *
 * <p>
 * Sections:
 * <ol>
 * <li>Grading Overview — summary statistics + score distribution chart</li>
 * <li>Question Performance — per-question metrics + pass-rate chart</li>
 * <li>Anomaly Summary — categorised counts + anomaly frequency chart</li>
 * <li>Structural Validation — per-student folder-structure status table</li>
 * <li>Execution &amp; Grading Diagnostics — status grid + timeout detail</li>
 * <li>Per-Student Results — per-question scores and test-case breakdowns</li>
 * <li>Instructor Insights — observations, per-question breakdown,
 * recommendations</li>
 * </ol>
 */
public class PdfReportGenerator {

	// ── Anomaly classification sets ──────────────────────────────────────────
	private static final Set<Anomaly.Type> STRUCTURAL_TYPES = EnumSet.of(Anomaly.Type.FOLDER_NOT_RENAMED,
			Anomaly.Type.STUDENT_ID_AS_FOLDER, Anomaly.Type.NO_PARENT_FOLDER, Anomaly.Type.EXTRA_NESTING,
			Anomaly.Type.MISSING_QUESTION_FOLDER, Anomaly.Type.MISSING_JAVA_FILE, Anomaly.Type.IDENTITY_MISMATCH);

	private static final Set<Anomaly.Type> METADATA_TYPES = EnumSet.of(Anomaly.Type.MISSING_HEADER,
			Anomaly.Type.INCOMPLETE_HEADER);

	private static final Set<Anomaly.Type> EXECUTION_TYPES = EnumSet.of(Anomaly.Type.COMPILATION_ERROR,
			Anomaly.Type.RUNTIME_ERROR, Anomaly.Type.EXECUTION_TIMEOUT);

	// ── Layout constants ─────────────────────────────────────────────────────
	private static final float MARGIN = 54f;

	// ── Colour palette ───────────────────────────────────────────────────────
	private static final Color HDR_BG = new Color(26, 58, 92);
	private static final Color TBL_HDR_BG = new Color(210, 224, 244);
	private static final Color ROW_ALT = new Color(248, 250, 253);
	private static final Color SECTION_BAR = new Color(26, 58, 92);
	private static final Color SUB_BAR = new Color(230, 238, 250);
	private static final Color PASS_COLOR = new Color(212, 244, 216);
	private static final Color PARTIAL_COLOR = new Color(255, 251, 205);
	private static final Color FAIL_COLOR = new Color(255, 218, 218);
	private static final Color TIMEOUT_COLOR = new Color(255, 231, 196);
	private static final Color MISSING_COLOR = new Color(232, 232, 232);
	private static final Color OK_STATUS = new Color(46, 130, 70);
	private static final Color WARN_STATUS = new Color(180, 100, 20);
	private static final Color ERR_STATUS = new Color(180, 40, 40);
	private static final Color INSIGHT_BG = new Color(245, 248, 255);

	private final String assessmentName;

	public PdfReportGenerator(String assessmentName) {
		this.assessmentName = assessmentName;
	}

	// ══════════════════════════════════════════════════════════════════════════
	// Public entry point
	// ══════════════════════════════════════════════════════════════════════════

	/**
	 * Generates the PDF report and writes it to {@code outputPath}.
	 *
	 * @param subs
	 *            all graded student submissions
	 * @param qcs
	 *            question configurations (for max-score metadata)
	 * @param outputPath
	 *            destination PDF file
	 */
	public void generate(List<StudentSubmission> subs, List<QuestionConfig> qcs, Path outputPath) throws IOException {
		List<StudentSubmission> sorted = subs.stream()
				.sorted(Comparator.comparing(StudentSubmission::getDisplayName, String.CASE_INSENSITIVE_ORDER))
				.collect(Collectors.toList());

		double classMax = qcs.stream().mapToDouble(QuestionConfig::getMaxScore).sum();

		Document doc = new Document(PageSize.A4, MARGIN, MARGIN, MARGIN, MARGIN);
		try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
			PdfWriter.getInstance(doc, fos);
			doc.open();

			writeReportHeader(doc, sorted.size());
			writeSectionOverview(doc, sorted, classMax);
			doc.newPage();
			writeSectionQuestions(doc, sorted, qcs);
			doc.newPage();
			writeSectionAnomalies(doc, sorted);
			doc.newPage();
			writeSectionStructural(doc, sorted);
			doc.newPage();
			writeSectionExecutionDiagnostics(doc, sorted, qcs);
			doc.newPage();
			writeSectionStudentsDetailed(doc, sorted, qcs);
			doc.newPage();
			writeSectionInsights(doc, sorted, qcs);

			doc.close();
		}
	}

	// ══════════════════════════════════════════════════════════════════════════
	// Report header (compact, top of page 1)
	// ══════════════════════════════════════════════════════════════════════════

	private void writeReportHeader(Document doc, int submissionCount) throws IOException {
		String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm"));

		PdfPTable tbl = new PdfPTable(1);
		tbl.setWidthPercentage(100);
		tbl.setSpacingBefore(0f);
		tbl.setSpacingAfter(14f);

		Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, Font.BOLD, new Color(255, 255, 255));
		Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 10f, new Color(210, 228, 252));

		Paragraph content = new Paragraph();
		content.add(new Phrase("Assessment : ", labelFont));
		content.add(new Phrase(assessmentName + "\n", valueFont));
		content.add(new Phrase("Generated  : ", labelFont));
		content.add(new Phrase(timestamp + "   |   Submissions: " + submissionCount, valueFont));

		PdfPCell cell = new PdfPCell(content);
		cell.setBackgroundColor(new Color(HDR_BG.getRed(), HDR_BG.getGreen(), HDR_BG.getBlue()));
		cell.setPadding(10f);
		cell.setBorder(Rectangle.NO_BORDER);
		tbl.addCell(cell);
		doc.add(tbl);
	}

	// ══════════════════════════════════════════════════════════════════════════
	// Section 1 — Grading Overview
	// ══════════════════════════════════════════════════════════════════════════

	private void writeSectionOverview(Document doc, List<StudentSubmission> subs, double classMax) throws IOException {
		addSectionHeading(doc, "1", "Grading Overview");

		int total = subs.size();
		long withErrors = subs.stream()
				.filter(s -> s.getAnomalies().stream().anyMatch(a -> a.getSeverity() == Anomaly.Severity.ERROR))
				.count();
		long successful = total - withErrors;
		long passing = classMax > 0 ? subs.stream().filter(s -> s.getTotalScore() / classMax >= 0.5).count() : 0;
		long totalAnomalies = subs.stream().mapToLong(s -> s.getAnomalies().size()).sum();
		double avg = subs.stream().mapToDouble(StudentSubmission::getTotalScore).average().orElse(0);
		double highest = subs.stream().mapToDouble(StudentSubmission::getTotalScore).max().orElse(0);
		double lowest = subs.stream().mapToDouble(StudentSubmission::getTotalScore).min().orElse(0);
		double median = computeMedian(subs);

		String[][] summaryRows = {{"Total Submissions Processed", String.valueOf(total)},
				{"Submissions Fully Processed", successful + " / " + total},
				{"Submissions With Errors", withErrors + " / " + total}, {"Passing (>= 50%)", passing + " / " + total},
				{"Total Anomalies Detected", String.valueOf(totalAnomalies)},
				{"Average Score", fmt(avg) + " / " + fmt(classMax)},
				{"Median Score", fmt(median) + " / " + fmt(classMax)},
				{"Highest Score", fmt(highest) + " / " + fmt(classMax)},
				{"Lowest Score", fmt(lowest) + " / " + fmt(classMax)},};
		addTwoColumnTable(doc, "Metric", "Value", summaryRows);

		if (classMax > 0 && !subs.isEmpty()) {
			addSubHeading(doc, "Score Distribution");
			byte[] chartPng = ChartGenerator.scoreDistributionChart(subs, classMax);
			Image img = Image.getInstance(chartPng);
			img.scaleToFit(480, 240);
			img.setAlignment(Element.ALIGN_CENTER);
			img.setSpacingBefore(6f);
			img.setSpacingAfter(6f);
			doc.add(img);
		}
	}

	// ══════════════════════════════════════════════════════════════════════════
	// Section 2 — Question Performance
	// ══════════════════════════════════════════════════════════════════════════

	private void writeSectionQuestions(Document doc, List<StudentSubmission> subs, List<QuestionConfig> qcs)
			throws IOException {
		addSectionHeading(doc, "2", "Question Performance");

		int total = subs.size();
		PdfPTable tbl = newTable(new float[]{70, 55, 65, 70, 70, 70});
		addHeaderRow(tbl, "Question", "Max Pts", "Avg Score", "Attempted", "Full Pass", "Partial");

		boolean alt = false;
		for (QuestionConfig qc : qcs) {
			String qId = qc.getQuestionId();
			long attempted = subs.stream().flatMap(s -> s.getResults().stream())
					.filter(r -> r.getQuestionId().equals(qId) && (r.isCompiled() || r.isExecuted())).count();
			long fullPass = subs.stream().flatMap(s -> s.getResults().stream())
					.filter(r -> r.getQuestionId().equals(qId) && r.getScore() >= r.getMaxScore()).count();
			long partial = subs.stream().flatMap(s -> s.getResults().stream())
					.filter(r -> r.getQuestionId().equals(qId) && r.getScore() > 0 && r.getScore() < r.getMaxScore())
					.count();
			double avg = subs.stream().flatMap(s -> s.getResults().stream()).filter(r -> r.getQuestionId().equals(qId))
					.mapToDouble(QuestionResult::getScore).average().orElse(0);
			addDataRow(tbl, alt, qId, fmt(qc.getMaxScore()), fmt(avg) + " / " + fmt(qc.getMaxScore()),
					attempted + " / " + total, pct((double) fullPass / total), pct((double) partial / total));
			alt = !alt;
		}
		doc.add(tbl);
		gap(doc);

		if (!subs.isEmpty() && !qcs.isEmpty()) {
			addSubHeading(doc, "Full-Pass Rate by Question");
			byte[] chartPng = ChartGenerator.passRateChart(subs, qcs);
			Image img = Image.getInstance(chartPng);
			img.scaleToFit(480, 220);
			img.setAlignment(Element.ALIGN_CENTER);
			img.setSpacingBefore(6f);
			img.setSpacingAfter(6f);
			doc.add(img);
		}

		// Key insight bullets
		if (!qcs.isEmpty()) {
			QuestionConfig hardest = qcs.stream().min(Comparator.comparingDouble(qc -> passRateForQuestion(subs, qc)))
					.orElse(null);
			QuestionConfig easiest = qcs.stream().max(Comparator.comparingDouble(qc -> passRateForQuestion(subs, qc)))
					.orElse(null);
			if (hardest != null) {
				addSubHeading(doc, "Key Insight");
				StringBuilder insight = new StringBuilder();
				insight.append("• ").append(hardest.getQuestionId()).append(" had the lowest full-pass rate (")
						.append(pct(passRateForQuestion(subs, hardest))).append(") — students struggled most here.");
				if (easiest != null && !easiest.getQuestionId().equals(hardest.getQuestionId())) {
					insight.append("\n• ").append(easiest.getQuestionId()).append(" had the highest full-pass rate (")
							.append(pct(passRateForQuestion(subs, easiest))).append(").");
				}
				long zeroScore = subs.stream().filter(s -> s.getTotalScore() == 0).count();
				if (zeroScore > 0) {
					insight.append("\n• ").append(zeroScore).append(" submission").append(zeroScore == 1 ? "" : "s")
							.append(" scored zero overall.");
				}
				Font f = FontFactory.getFont(FontFactory.HELVETICA, 9);
				Paragraph p = new Paragraph(insight.toString(), f);
				p.setSpacingBefore(4f);
				p.setSpacingAfter(4f);
				doc.add(p);
			}
		}
	}

	// ══════════════════════════════════════════════════════════════════════════
	// Section 3 — Anomaly Summary
	// ══════════════════════════════════════════════════════════════════════════

	private void writeSectionAnomalies(Document doc, List<StudentSubmission> subs) throws IOException {
		addSectionHeading(doc, "3", "Anomaly Summary");

		Map<Anomaly.Type, Long> counts = subs.stream().flatMap(s -> s.getAnomalies().stream()).collect(Collectors
				.groupingBy(Anomaly::getType, () -> new EnumMap<>(Anomaly.Type.class), Collectors.counting()));

		if (counts.isEmpty()) {
			addBodyText(doc, "No anomalies were detected.");
			return;
		}

		addCategoryAnomalyTable(doc, "Submission Structure Issues", counts, STRUCTURAL_TYPES);
		addCategoryAnomalyTable(doc, "Code Metadata Issues", counts, METADATA_TYPES);
		addCategoryAnomalyTable(doc, "Execution Issues", counts, EXECUTION_TYPES);

		addSubHeading(doc, "Common Issues by Frequency");
		PdfPTable tbl = newTable(new float[]{340, 70});
		addHeaderRow(tbl, "Issue Type", "Occurrences");
		boolean alt = false;
		for (Map.Entry<Anomaly.Type, Long> e : counts.entrySet().stream()
				.sorted(Map.Entry.<Anomaly.Type, Long>comparingByValue().reversed()).collect(Collectors.toList())) {
			addDataRow(tbl, alt, ChartGenerator.friendlyName(e.getKey()), String.valueOf(e.getValue()));
			alt = !alt;
		}
		doc.add(tbl);
		gap(doc);

		addSubHeading(doc, "Anomaly Frequency (Top 8)");
		byte[] chartPng = ChartGenerator.anomalyFrequencyChart(counts);
		Image img = Image.getInstance(chartPng);
		img.scaleToFit(480, 240);
		img.setAlignment(Element.ALIGN_CENTER);
		img.setSpacingBefore(6f);
		img.setSpacingAfter(6f);
		doc.add(img);
	}

	private void addCategoryAnomalyTable(Document doc, String heading, Map<Anomaly.Type, Long> counts,
			Set<Anomaly.Type> types) throws IOException {
		List<Map.Entry<Anomaly.Type, Long>> relevant = counts.entrySet().stream()
				.filter(e -> types.contains(e.getKey())).collect(Collectors.toList());
		if (relevant.isEmpty()) {
			return;
		}
		addSubHeading(doc, heading);
		PdfPTable tbl = newTable(new float[]{340, 70});
		addHeaderRow(tbl, "Issue", "Count");
		boolean alt = false;
		for (Map.Entry<Anomaly.Type, Long> e : relevant) {
			addDataRow(tbl, alt, ChartGenerator.friendlyName(e.getKey()), String.valueOf(e.getValue()));
			alt = !alt;
		}
		doc.add(tbl);
		gap(doc);
	}

	// ══════════════════════════════════════════════════════════════════════════
	// Section 4 — Structural Validation
	// ══════════════════════════════════════════════════════════════════════════

	private void writeSectionStructural(Document doc, List<StudentSubmission> subs) throws IOException {
		addSectionHeading(doc, "4", "Structural Validation");

		Font descFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, new Color(80, 80, 90));
		Paragraph descP = new Paragraph("Checks that each submission follows the required folder structure: "
				+ "<username>/<question-folder>/<java-files>", descFont);
		descP.setSpacingBefore(2f);
		descP.setSpacingAfter(8f);
		doc.add(descP);

		PdfPTable tbl = newTable(new float[]{140, 55, 295});
		addHeaderRow(tbl, "Student", "Status", "Issues");
		boolean alt = false;
		for (StudentSubmission sub : subs) {
			List<Anomaly> structural = anomaliesInCategory(sub, STRUCTURAL_TYPES);
			boolean ok = structural.isEmpty();
			String status = ok ? "OK" : "ISSUES";
			String issues = ok
					? "None"
					: structural.stream().map(a -> ChartGenerator.friendlyName(a.getType())).distinct()
							.collect(Collectors.joining(", "));
			Color rowBg = alt ? ROW_ALT : Color.WHITE;
			Font df = FontFactory.getFont(FontFactory.HELVETICA, 8.5f);
			Font sf = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, Font.BOLD, ok ? OK_STATUS : WARN_STATUS);
			tbl.addCell(styledCell(sub.getDisplayName(), df, rowBg, Element.ALIGN_LEFT));
			tbl.addCell(styledCell(status, sf, rowBg, Element.ALIGN_CENTER));
			tbl.addCell(styledCell(issues, df, rowBg, Element.ALIGN_LEFT));
			alt = !alt;
		}
		doc.add(tbl);
		gap(doc);

		boolean anyIssue = subs.stream().anyMatch(s -> !anomaliesInCategory(s, STRUCTURAL_TYPES).isEmpty());
		if (anyIssue) {
			addSubHeading(doc, "Detail — Submissions With Structural Issues");
			for (StudentSubmission sub : subs) {
				List<Anomaly> structural = anomaliesInCategory(sub, STRUCTURAL_TYPES);
				if (structural.isEmpty()) {
					continue;
				}
				List<String> lines = structural.stream().map(
						a -> (a.getQuestionId() != null ? "[" + a.getQuestionId() + "] " : "") + a.getDescription())
						.collect(Collectors.toList());
				addStudentDetailBlock(doc, sub, lines);
			}
		}
	}

	// ══════════════════════════════════════════════════════════════════════════
	// Section 5 — Execution & Grading Diagnostics
	// ══════════════════════════════════════════════════════════════════════════

	private void writeSectionExecutionDiagnostics(Document doc, List<StudentSubmission> subs, List<QuestionConfig> qcs)
			throws IOException {
		addSectionHeading(doc, "5", "Execution & Grading Diagnostics");

		int qCount = qcs.size();
		float nameW = 140f;
		float qW = Math.max(50f, (600f - nameW) / Math.max(qCount, 1));
		float[] widths = new float[1 + qCount];
		widths[0] = nameW;
		for (int i = 0; i < qCount; i++) {
			widths[1 + i] = qW;
		}

		PdfPTable tbl = newTable(widths);
		Font hf = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Font.BOLD, new Color(40, 55, 80));
		addRawCell(tbl, "Student", TBL_HDR_BG, hf, Element.ALIGN_LEFT);
		for (QuestionConfig qc : qcs) {
			addRawCell(tbl, qc.getQuestionId(), TBL_HDR_BG, hf, Element.ALIGN_CENTER);
		}

		boolean alt = false;
		for (StudentSubmission sub : subs) {
			Color rowBg = alt ? ROW_ALT : Color.WHITE;
			Font nf = FontFactory.getFont(FontFactory.HELVETICA, 8f);
			addRawCell(tbl, StringUtils.truncate(sub.getDisplayName(), 26), rowBg, nf, Element.ALIGN_LEFT);

			for (QuestionConfig qc : qcs) {
				Optional<QuestionResult> rOpt = sub.getResults().stream()
						.filter(r -> r.getQuestionId().equals(qc.getQuestionId())).findFirst();
				String cell;
				if (rOpt.isEmpty()) {
					cell = "MISSING";
				} else {
					cell = scoreStatus(rOpt.get());
				}
				Color statusBg = statusColor(cell);
				Font sf = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, Font.BOLD);
				PdfPCell sc = new PdfPCell(new Phrase(cell, sf));
				sc.setBackgroundColor(new Color(statusBg.getRed(), statusBg.getGreen(), statusBg.getBlue()));
				sc.setHorizontalAlignment(Element.ALIGN_CENTER);
				sc.setVerticalAlignment(Element.ALIGN_MIDDLE);
				sc.setPadding(4f);
				sc.setBorderColor(new Color(210, 210, 210));
				tbl.addCell(sc);
			}
			alt = !alt;
		}
		doc.add(tbl);
		gap(doc);

		Font legendFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, new Color(80, 80, 80));
		doc.add(new Paragraph(
				"PASS = full marks   PARTIAL = some marks   FAIL = no marks   "
						+ "ERR-COMP = compilation failed   TIMEOUT = time limit exceeded   MISSING = files not found",
				legendFont));
		gap(doc);

		boolean anyTimeout = subs.stream()
				.anyMatch(s -> s.getAnomalies().stream().anyMatch(a -> a.getType() == Anomaly.Type.EXECUTION_TIMEOUT));
		if (anyTimeout) {
			addSubHeading(doc, "Timeout Detail");
			for (StudentSubmission sub : subs) {
				for (Anomaly a : sub.getAnomalies()) {
					if (a.getType() != Anomaly.Type.EXECUTION_TIMEOUT) {
						continue;
					}
					String qId = a.getQuestionId() != null ? a.getQuestionId() : "?";
					Optional<QuestionResult> qrOpt = sub.getResults().stream()
							.filter(r -> r.getQuestionId().equals(qId)).findFirst();
					double partial = qrOpt.map(QuestionResult::getScore).orElse(0.0);
					double max = qrOpt.map(QuestionResult::getMaxScore).orElse(0.0);
					String testsMsg = partial > 0
							? (int) partial + " test(s) completed before termination"
							: "No tests completed before termination";
					PdfPTable dtbl = newTable(new float[]{120, 370});
					dtbl.setSpacingBefore(4f);
					dtbl.setSpacingAfter(4f);
					Font kf = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, Font.BOLD, new Color(60, 60, 80));
					Font vf = FontFactory.getFont(FontFactory.HELVETICA, 8.5f);
					addDetailRow(dtbl, kf, vf, "Student", sub.getDisplayName());
					addDetailRow(dtbl, kf, vf, "Question", qId);
					addDetailRow(dtbl, kf, vf, "Action", "Process terminated after time limit exceeded");
					addDetailRow(dtbl, kf, vf, "Tests", testsMsg);
					addDetailRow(dtbl, kf, vf, "Score", fmt(partial) + " / " + fmt(max));
					doc.add(dtbl);
				}
			}
		}
	}

	// ══════════════════════════════════════════════════════════════════════════
	// Section 6 — Per-Student Results (detailed)
	// ══════════════════════════════════════════════════════════════════════════

	private void writeSectionStudentsDetailed(Document doc, List<StudentSubmission> subs, List<QuestionConfig> qcs)
			throws IOException {
		addSectionHeading(doc, "6", "Per-Student Results");

		double classMax = qcs.stream().mapToDouble(QuestionConfig::getMaxScore).sum();

		for (int i = 0; i < subs.size(); i++) {
			StudentSubmission sub = subs.get(i);

			// Student header banner
			String nameStr = (sub.getName() != null && !sub.getName().isBlank())
					? sub.getName() + " (" + sub.getDisplayName() + ")"
					: sub.getDisplayName();
			String orgId = sub.getOrgDefinedId() != null ? sub.getOrgDefinedId() : "";
			String scoreStr = fmt(sub.getTotalScore()) + " / " + fmt(classMax);

			PdfPTable bannerTbl = newTable(new float[]{360, 100});
			bannerTbl.setSpacingBefore(8f);
			bannerTbl.setSpacingAfter(4f);

			Font bannerNameFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, Font.BOLD,
					new Color(255, 255, 255));
			Font bannerSubFont = FontFactory.getFont(FontFactory.HELVETICA, 8f, new Color(210, 225, 245));
			Font bannerScoreFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11f, Font.BOLD,
					new Color(255, 255, 255));

			Paragraph nameAndOrg = new Paragraph();
			nameAndOrg.add(new Phrase("[" + (i + 1) + "] " + nameStr, bannerNameFont));
			if (!orgId.isEmpty()) {
				nameAndOrg.add(new Phrase("\nStudent ID: " + orgId, bannerSubFont));
			}

			PdfPCell nameCell = new PdfPCell(nameAndOrg);
			nameCell.setBackgroundColor(HDR_BG);
			nameCell.setPadding(8f);
			nameCell.setBorder(Rectangle.NO_BORDER);
			bannerTbl.addCell(nameCell);

			PdfPCell scoreCell = new PdfPCell(new Phrase("Score\n" + scoreStr, bannerScoreFont));
			scoreCell.setBackgroundColor(HDR_BG);
			scoreCell.setPadding(8f);
			scoreCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
			scoreCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
			scoreCell.setBorder(Rectangle.NO_BORDER);
			bannerTbl.addCell(scoreCell);
			doc.add(bannerTbl);

			// Submission issues
			List<Anomaly> allIssues = sub.getAnomalies();
			boolean hasNonExec = allIssues.stream().anyMatch(a -> !EXECUTION_TYPES.contains(a.getType()));

			if (!hasNonExec) {
				Font okFont = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, OK_STATUS);
				Paragraph okP = new Paragraph("Structure / Metadata: OK — no submission issues.", okFont);
				okP.setSpacingBefore(3f);
				okP.setSpacingAfter(5f);
				doc.add(okP);
			} else {
				long nonExecCount = allIssues.stream().filter(a -> !EXECUTION_TYPES.contains(a.getType())).count();
				addSubHeading(doc, "Submission Issues (" + nonExecCount + ")");
				for (Anomaly a : allIssues) {
					if (EXECUTION_TYPES.contains(a.getType())) {
						continue;
					}
					String icon = a.getSeverity() == Anomaly.Severity.ERROR ? "✖ " : "⚠ ";
					String qPart = a.getQuestionId() != null ? "[" + a.getQuestionId() + "] " : "";
					Color iconColor = a.getSeverity() == Anomaly.Severity.ERROR ? ERR_STATUS : WARN_STATUS;
					Font issueFont = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, iconColor);
					Paragraph issueP = new Paragraph("   " + icon + qPart + a.getDescription(), issueFont);
					issueP.setSpacingBefore(1f);
					doc.add(issueP);
				}
				gap(doc);
			}

			// Per-question results
			for (QuestionConfig qc : qcs) {
				Optional<QuestionResult> resultOpt = sub.getResults().stream()
						.filter(r -> r.getQuestionId().equals(qc.getQuestionId())).findFirst();

				if (resultOpt.isEmpty()) {
					addQuestionBlock(doc, qc.getQuestionId(), "0 / " + fmt(qc.getMaxScore()), "MISSING", MISSING_COLOR,
							"✖ Question files not found", null, new ArrayList<>(), new ArrayList<>());
					continue;
				}

				QuestionResult qr = resultOpt.get();
				String status = scoreStatus(qr);
				Color statusBg = statusColor(status);
				String scoreLabel = fmt(qr.getScore()) + " / " + fmt(qr.getMaxScore());

				if ("PASS".equals(status)) {
					addQuestionBlock(doc, qc.getQuestionId(), scoreLabel, status, statusBg,
							"All " + (int) qr.getMaxScore() + " test case(s) passed ✓", null, new ArrayList<>(),
							new ArrayList<>());
				} else if ("ERR-COMP".equals(status)) {
					addQuestionBlock(doc, qc.getQuestionId(), scoreLabel, status, statusBg, null,
							buildCompileErrorText(qr.getErrorMessage()), new ArrayList<>(), new ArrayList<>());
				} else if ("TIMEOUT".equals(status)) {
					double ts = qr.getScore();
					String timeoutMsg = "Execution timed out — possible infinite loop.\n"
							+ (ts > 0
									? (int) ts + " test(s) completed before termination."
									: "No tests completed before termination.")
							+ "\nScore: " + fmt(ts) + " / " + fmt(qr.getMaxScore());
					addQuestionBlock(doc, qc.getQuestionId(), scoreLabel, status, statusBg, timeoutMsg, null,
							new ArrayList<>(), new ArrayList<>());
				} else if ("MISSING".equals(status)) {
					addQuestionBlock(doc, qc.getQuestionId(), scoreLabel, status, statusBg,
							"✖ Question files not found", null, new ArrayList<>(), new ArrayList<>());
				} else {
					// PARTIAL or FAIL
					List<String[]> tests = parseTesterOutput(qr.getOutput());
					List<String[]> exceptions = parseExceptions(qr.getErrorMessage());
					addQuestionBlock(doc, qc.getQuestionId(), scoreLabel, status, statusBg, null, null, tests,
							exceptions);
				}
			}

			// Thin separator
			PdfPTable sep = new PdfPTable(1);
			sep.setWidthPercentage(100);
			sep.setSpacingBefore(8f);
			sep.setSpacingAfter(4f);
			PdfPCell sepCell = new PdfPCell(new Phrase(" "));
			sepCell.setBackgroundColor(new Color(200, 215, 235));
			sepCell.setBorder(Rectangle.NO_BORDER);
			sepCell.setFixedHeight(1.5f);
			sep.addCell(sepCell);
			doc.add(sep);
		}
	}

	/** Render one question's result block inside the per-student section. */
	private void addQuestionBlock(Document doc, String qId, String scoreLabel, String status, Color statusBg,
			String simpleMsg, String monospaceMsg, List<String[]> tests, List<String[]> exceptions) throws IOException {
		// Mini-header: QID stripe with score and status
		PdfPTable qHeader = newTable(new float[]{55, 120, 80});
		qHeader.setSpacingBefore(5f);
		qHeader.setSpacingAfter(2f);

		Font qIdFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f, Font.BOLD, new Color(40, 60, 100));
		Font qScoreFont = FontFactory.getFont(FontFactory.HELVETICA, 9f, new Color(60, 60, 60));
		Font qStatusFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, Font.BOLD);

		PdfPCell qIdCell = new PdfPCell(new Phrase(qId, qIdFont));
		qIdCell.setBorder(Rectangle.NO_BORDER);
		qIdCell.setBackgroundColor(SUB_BAR);
		qIdCell.setPadding(5f);
		qHeader.addCell(qIdCell);

		PdfPCell qScoreCell = new PdfPCell(new Phrase(scoreLabel, qScoreFont));
		qScoreCell.setBorder(Rectangle.NO_BORDER);
		qScoreCell.setBackgroundColor(SUB_BAR);
		qScoreCell.setPadding(5f);
		qScoreCell.setHorizontalAlignment(Element.ALIGN_CENTER);
		qHeader.addCell(qScoreCell);

		PdfPCell qStatusCell = new PdfPCell(new Phrase("[" + status + "]", qStatusFont));
		qStatusCell.setBackgroundColor(new Color(statusBg.getRed(), statusBg.getGreen(), statusBg.getBlue()));
		qStatusCell.setPadding(5f);
		qStatusCell.setBorder(Rectangle.NO_BORDER);
		qStatusCell.setHorizontalAlignment(Element.ALIGN_CENTER);
		qStatusCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
		qHeader.addCell(qStatusCell);
		doc.add(qHeader);

		Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 8.5f);
		Font monoFont = FontFactory.getFont(FontFactory.COURIER, 7.5f, new Color(60, 40, 40));

		if (simpleMsg != null) {
			Paragraph p = new Paragraph("   " + simpleMsg, bodyFont);
			p.setSpacingBefore(2f);
			p.setSpacingAfter(4f);
			doc.add(p);
		}

		if (monospaceMsg != null) {
			Paragraph p = new Paragraph(monospaceMsg, monoFont);
			p.setSpacingBefore(2f);
			p.setSpacingAfter(4f);
			p.setIndentationLeft(12f);
			doc.add(p);
		}

		if (!tests.isEmpty()) {
			List<String> passedNames = tests.stream().filter(t -> "PASS".equals(t[1])).map(t -> "Test " + t[0])
					.collect(Collectors.toList());
			if (!passedNames.isEmpty()) {
				Font passFont = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, OK_STATUS);
				Paragraph passP = new Paragraph("   ✓ Passed: " + String.join(", ", passedNames), passFont);
				passP.setSpacingBefore(2f);
				doc.add(passP);
			}

			List<String[]> failedTests = tests.stream().filter(t -> !"PASS".equals(t[1])).collect(Collectors.toList());
			if (!failedTests.isEmpty()) {
				PdfPTable ftbl = newTable(new float[]{60, 390});
				ftbl.setSpacingBefore(3f);
				ftbl.setSpacingAfter(3f);
				Font fhFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, Font.BOLD);
				addRawCell(ftbl, "Test", FAIL_COLOR, fhFont, Element.ALIGN_CENTER);
				addRawCell(ftbl, "Details", FAIL_COLOR, fhFont, Element.ALIGN_LEFT);

				int exceptionIdx = 0;
				boolean rowAlt = false;
				for (String[] test : failedTests) {
					// test: [number, status, expected, actual, failReason, methodCall]
					Color rowBg = rowAlt ? new Color(255, 232, 232) : new Color(255, 243, 243);
					Font tf = FontFactory.getFont(FontFactory.COURIER, 7.5f);
					addRawCell(ftbl, "Test " + test[0], rowBg, tf, Element.ALIGN_CENTER);

					StringBuilder detail = new StringBuilder();
					if (test[5] != null) {
						detail.append("✗ ").append(test[5]);
					}
					String expected = test[2];
					String actual = test[3];
					String failReason = test[4];

					if (expected != null && !expected.isEmpty()) {
						detail.append("\n   Expected : ").append(expected);
						detail.append("\n   Actual   : ").append(actual != null ? actual : "(no output)");
					}
					if ("Exception".equals(failReason) && exceptionIdx < exceptions.size()) {
						String[] ex = exceptions.get(exceptionIdx++);
						detail.append("\n   Exception: ").append(ex[0]);
						if (ex[1] != null && !ex[1].isEmpty()) {
							detail.append("\n   Message  : ").append(ex[1]);
						}
						if (ex[2] != null && !ex[2].isEmpty()) {
							detail.append("\n   Location : ").append(ex[2]);
							if (ex[3] != null && !ex[3].isEmpty()) {
								detail.append(", line ").append(ex[3]);
							}
						}
					} else if (failReason != null && !failReason.isEmpty() && !"Exception".equals(failReason)) {
						detail.append("\n   Reason   : ").append(failReason);
					}

					PdfPCell detailCell = new PdfPCell(new Phrase(detail.toString(), tf));
					detailCell.setBackgroundColor(rowBg);
					detailCell.setPadding(4f);
					detailCell.setBorderColor(new Color(230, 180, 180));
					ftbl.addCell(detailCell);
					rowAlt = !rowAlt;
				}
				doc.add(ftbl);
			}
		}
	}

	// ══════════════════════════════════════════════════════════════════════════
	// Section 7 — Instructor Insights
	// ══════════════════════════════════════════════════════════════════════════

	private void writeSectionInsights(Document doc, List<StudentSubmission> subs, List<QuestionConfig> qcs)
			throws IOException {
		addSectionHeading(doc, "7", "Instructor Insights");

		if (subs.isEmpty()) {
			addBodyText(doc, "No submissions to analyse.");
			return;
		}

		int total = subs.size();
		double classMax = qcs.stream().mapToDouble(QuestionConfig::getMaxScore).sum();
		long compileErrors = subs.stream()
				.filter(s -> s.getAnomalies().stream().anyMatch(a -> a.getType() == Anomaly.Type.COMPILATION_ERROR))
				.count();
		long timeouts = subs.stream()
				.filter(s -> s.getAnomalies().stream().anyMatch(a -> a.getType() == Anomaly.Type.EXECUTION_TIMEOUT))
				.count();
		long structIssues = subs.stream().filter(s -> !anomaliesInCategory(s, STRUCTURAL_TYPES).isEmpty()).count();
		long metaIssues = subs.stream().filter(s -> !anomaliesInCategory(s, METADATA_TYPES).isEmpty()).count();
		long zeroScore = subs.stream().filter(s -> s.getTotalScore() == 0).count();
		long passing = classMax > 0 ? subs.stream().filter(s -> s.getTotalScore() / classMax >= 0.5).count() : 0;

		QuestionConfig hardestQ = qcs.stream().min(Comparator.comparingDouble(qc -> passRateForQuestion(subs, qc)))
				.orElse(null);
		QuestionConfig easiestQ = qcs.stream().max(Comparator.comparingDouble(qc -> passRateForQuestion(subs, qc)))
				.orElse(null);

		// ── Key Observations ────────────────────────────────────────────────
		addSubHeading(doc, "Key Observations");
		List<String> observations = new ArrayList<>();
		observations.add(passing + " out of " + total + " submissions (" + pct((double) passing / total)
				+ ") scored 50% or above.");
		if (compileErrors > 0) {
			observations.add(compileErrors + " submission(s) (" + pct((double) compileErrors / total)
					+ ") had compilation errors.");
		}
		if (timeouts > 0) {
			observations.add(timeouts + " submission(s) (" + pct((double) timeouts / total)
					+ ") caused execution timeout — possible infinite loops.");
		}
		if (structIssues > 0) {
			observations.add(structIssues + " submission(s) (" + pct((double) structIssues / total)
					+ ") had folder structure issues.");
		}
		if (metaIssues > 0) {
			observations.add(metaIssues + " submission(s) (" + pct((double) metaIssues / total)
					+ ") had missing or incomplete code header comments.");
		}
		if (zeroScore > 0) {
			observations.add((zeroScore == 1 ? "1 submission scored" : zeroScore + " submissions scored")
					+ " zero — may require immediate follow-up.");
		}
		if (hardestQ != null) {
			observations.add(hardestQ.getQuestionId() + " had the lowest full-pass rate ("
					+ pct(passRateForQuestion(subs, hardestQ)) + ").");
		}
		if (easiestQ != null && hardestQ != null && !easiestQ.getQuestionId().equals(hardestQ.getQuestionId())) {
			observations.add(easiestQ.getQuestionId() + " had the highest full-pass rate ("
					+ pct(passRateForQuestion(subs, easiestQ)) + ").");
		}

		PdfPTable obsTbl = newTable(new float[]{30, 420});
		obsTbl.setSpacingBefore(4f);
		obsTbl.setSpacingAfter(10f);
		Font obsFont = FontFactory.getFont(FontFactory.HELVETICA, 9f);
		boolean alt = false;
		for (int i = 0; i < observations.size(); i++) {
			Color rowBg = alt ? ROW_ALT : INSIGHT_BG;
			obsTbl.addCell(styledCell(String.valueOf(i + 1) + ".", obsFont, rowBg, Element.ALIGN_RIGHT));
			obsTbl.addCell(styledCell(observations.get(i), obsFont, rowBg, Element.ALIGN_LEFT));
			alt = !alt;
		}
		doc.add(obsTbl);

		// ── Per-Question Failure Breakdown ───────────────────────────────────
		addSubHeading(doc, "Per-Question Failure Breakdown");
		PdfPTable qTbl = newTable(new float[]{60, 80, 80, 80, 150});
		addHeaderRow(qTbl, "Question", "Not Correct", "Compile Err", "Timeouts", "Notes");
		alt = false;
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
			String notes = compFail > 0 && toFail > 0
					? compFail + " compile, " + toFail + " timeout"
					: compFail > 0 ? compFail + " compile error(s)" : toFail > 0 ? toFail + " timeout(s)" : "—";
			addDataRow(qTbl, alt, qc.getQuestionId(), String.valueOf(failed), String.valueOf(compFail),
					String.valueOf(toFail), notes);
			alt = !alt;
		}
		doc.add(qTbl);
		gap(doc);

		// ── Recommendations ──────────────────────────────────────────────────
		addSubHeading(doc, "Recommendations");
		List<String> recs = new ArrayList<>();
		if (hardestQ != null && passRateForQuestion(subs, hardestQ) < 0.6) {
			recs.add("Revisit " + hardestQ.getQuestionId()
					+ " in future lectures — most students did not achieve full marks.");
		}
		if (structIssues > 0) {
			recs.add("Remind students to follow the required submission folder structure before the next assessment.");
		}
		if (compileErrors > 0) {
			recs.add("Encourage students to compile locally before submitting to catch syntax errors.");
		}
		if (timeouts > 0) {
			recs.add("Highlight infinite loop risks in lab sessions — " + timeouts + " submission(s) timed out.");
		}
		if (metaIssues > 0) {
			recs.add("Reiterate the requirement for complete Name/Email header comments in all submitted files.");
		}
		if (recs.isEmpty()) {
			recs.add("No specific recommendations — class performed well overall.");
		}

		PdfPTable recTbl = newTable(new float[]{30, 420});
		recTbl.setSpacingBefore(4f);
		Font recFont = FontFactory.getFont(FontFactory.HELVETICA, 9f);
		alt = false;
		for (int i = 0; i < recs.size(); i++) {
			Color rowBg = alt ? ROW_ALT : INSIGHT_BG;
			recTbl.addCell(styledCell(String.valueOf(i + 1) + ".", recFont, rowBg, Element.ALIGN_RIGHT));
			recTbl.addCell(styledCell(recs.get(i), recFont, rowBg, Element.ALIGN_LEFT));
			alt = !alt;
		}
		doc.add(recTbl);
	}

	// ══════════════════════════════════════════════════════════════════════════
	// Tester output parsing (mirrors InstructorReportGenerator)
	// ══════════════════════════════════════════════════════════════════════════

	/**
	 * Parse tester stdout into a list of test-case results. Each entry:
	 * [testNumber, status, expected, actual, failReason, methodCall].
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

	/**
	 * Parse stderr into a list of exception details. Each entry: [simpleClassName,
	 * message, studentSourceFile, lineNumber].
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
					results.add(new String[]{exClass, exMessage, exFile, exLine});
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
			results.add(new String[]{exClass, exMessage, exFile, exLine});
		}
		return results;
	}

	// ══════════════════════════════════════════════════════════════════════════
	// Shared table helpers
	// ══════════════════════════════════════════════════════════════════════════

	private PdfPTable newTable(float[] widths) throws IOException {
		PdfPTable tbl = new PdfPTable(widths.length);
		tbl.setWidthPercentage(100);
		tbl.setWidths(widths);
		tbl.setSpacingBefore(4f);
		tbl.setSpacingAfter(4f);
		return tbl;
	}

	private void addHeaderRow(PdfPTable tbl, String... headers) {
		Font hFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.BOLD, new Color(40, 55, 80));
		for (String h : headers) {
			PdfPCell cell = new PdfPCell(new Phrase(h, hFont));
			cell.setBackgroundColor(new Color(TBL_HDR_BG.getRed(), TBL_HDR_BG.getGreen(), TBL_HDR_BG.getBlue()));
			cell.setPadding(5f);
			cell.setBorderColor(new Color(180, 200, 225));
			cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
			tbl.addCell(cell);
		}
	}

	private void addDataRow(PdfPTable tbl, boolean alt, String... values) {
		Font dFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
		Color rowBg = alt ? ROW_ALT : Color.WHITE;
		for (int i = 0; i < values.length; i++) {
			PdfPCell cell = new PdfPCell(new Phrase(values[i] != null ? values[i] : "", dFont));
			cell.setBackgroundColor(new Color(rowBg.getRed(), rowBg.getGreen(), rowBg.getBlue()));
			cell.setPadding(4.5f);
			cell.setBorderColor(new Color(210, 210, 210));
			cell.setHorizontalAlignment(i == 0 ? Element.ALIGN_LEFT : Element.ALIGN_CENTER);
			cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
			tbl.addCell(cell);
		}
	}

	private void addTwoColumnTable(Document doc, String col1, String col2, String[][] rows) throws IOException {
		PdfPTable tbl = newTable(new float[]{250, 240});
		addHeaderRow(tbl, col1, col2);
		boolean alt = false;
		for (String[] row : rows) {
			addDataRow(tbl, alt, row[0], row[1]);
			alt = !alt;
		}
		doc.add(tbl);
	}

	private void addRawCell(PdfPTable tbl, String text, Color bg, Font font, int hAlign) {
		PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
		cell.setBackgroundColor(new Color(bg.getRed(), bg.getGreen(), bg.getBlue()));
		cell.setHorizontalAlignment(hAlign);
		cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
		cell.setPadding(4.5f);
		cell.setBorderColor(new Color(180, 200, 225));
		tbl.addCell(cell);
	}

	private PdfPCell styledCell(String text, Font font, Color bg, int hAlign) {
		PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
		cell.setBackgroundColor(new Color(bg.getRed(), bg.getGreen(), bg.getBlue()));
		cell.setHorizontalAlignment(hAlign);
		cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
		cell.setPadding(4.5f);
		cell.setBorderColor(new Color(210, 210, 210));
		return cell;
	}

	private void addDetailRow(PdfPTable tbl, Font kFont, Font vFont, String key, String value) {
		PdfPCell k = new PdfPCell(new Phrase(key, kFont));
		k.setBorderColor(new Color(200, 200, 200));
		k.setPadding(4f);
		k.setBackgroundColor(new Color(245, 247, 252));
		tbl.addCell(k);
		PdfPCell v = new PdfPCell(new Phrase(value != null ? value : "", vFont));
		v.setBorderColor(new Color(200, 200, 200));
		v.setPadding(4f);
		tbl.addCell(v);
	}

	private void addStudentDetailBlock(Document doc, StudentSubmission sub, List<String> lines) throws IOException {
		PdfPTable tbl = newTable(new float[]{490});
		tbl.setSpacingBefore(3f);
		tbl.setSpacingAfter(6f);
		Font hFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, Font.BOLD, new Color(26, 58, 92));
		Font bFont = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, WARN_STATUS);

		PdfPCell hCell = new PdfPCell(new Phrase(sub.getDisplayName(), hFont));
		hCell.setBackgroundColor(new Color(235, 243, 255));
		hCell.setPadding(5f);
		hCell.setBorderColor(new Color(180, 210, 240));
		tbl.addCell(hCell);

		for (String line : lines) {
			PdfPCell bCell = new PdfPCell(new Phrase("  ⚠ " + line, bFont));
			bCell.setBackgroundColor(Color.WHITE);
			bCell.setPadding(4f);
			bCell.setBorderColor(new Color(220, 220, 220));
			tbl.addCell(bCell);
		}
		doc.add(tbl);
	}

	// ══════════════════════════════════════════════════════════════════════════
	// Typography helpers
	// ══════════════════════════════════════════════════════════════════════════

	private void addSectionHeading(Document doc, String num, String title) throws IOException {
		Font secFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, Font.BOLD, new Color(255, 255, 255));
		Paragraph p = new Paragraph("Section " + num + ": " + title, secFont);
		p.setSpacingBefore(8f);
		p.setSpacingAfter(10f);

		PdfPTable banner = new PdfPTable(1);
		banner.setWidthPercentage(100);
		banner.setSpacingBefore(4f);
		banner.setSpacingAfter(8f);
		PdfPCell cell = new PdfPCell(p);
		cell.setBackgroundColor(new Color(SECTION_BAR.getRed(), SECTION_BAR.getGreen(), SECTION_BAR.getBlue()));
		cell.setPadding(8f);
		cell.setBorder(Rectangle.NO_BORDER);
		banner.addCell(cell);
		doc.add(banner);
	}

	private void addSubHeading(Document doc, String text) throws IOException {
		Font f = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.BOLD, new Color(60, 80, 110));
		Paragraph p = new Paragraph(text, f);
		p.setSpacingBefore(10f);
		p.setSpacingAfter(4f);
		doc.add(p);
	}

	private void addBodyText(Document doc, String text) throws IOException {
		Font f = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(80, 80, 80));
		Paragraph p = new Paragraph(text, f);
		p.setSpacingBefore(4f);
		p.setSpacingAfter(4f);
		doc.add(p);
	}

	private void gap(Document doc) throws IOException {
		Paragraph p = new Paragraph(" ");
		p.setSpacingAfter(4f);
		doc.add(p);
	}

	// ══════════════════════════════════════════════════════════════════════════
	// Score / status helpers
	// ══════════════════════════════════════════════════════════════════════════

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

	private Color statusColor(String status) {
		return switch (status) {
			case "PASS" -> PASS_COLOR;
			case "PARTIAL" -> PARTIAL_COLOR;
			case "FAIL", "ERR-COMP", "RUN-ERR" -> FAIL_COLOR;
			case "TIMEOUT" -> TIMEOUT_COLOR;
			case "MISSING" -> MISSING_COLOR;
			default -> Color.WHITE;
		};
	}

	private String buildCompileErrorText(String errorMessage) {
		if (errorMessage == null || errorMessage.isBlank()) {
			return "Compilation failed.";
		}
		StringBuilder sb = new StringBuilder("Compilation failed:\n");
		int printed = 0;
		for (String line : errorMessage.split("\n")) {
			String t = line.trim();
			if (t.isEmpty() || t.startsWith("Note:")) {
				continue;
			}
			sb.append(t).append("\n");
			if (++printed >= 6) {
				sb.append("[... see logs for full error]");
				break;
			}
		}
		return sb.toString();
	}

	// ══════════════════════════════════════════════════════════════════════════
	// Statistics helpers
	// ══════════════════════════════════════════════════════════════════════════

	private double passRateForQuestion(List<StudentSubmission> subs, QuestionConfig qc) {
		if (subs.isEmpty()) {
			return 0.0;
		}
		long passed = subs.stream().flatMap(s -> s.getResults().stream())
				.filter(r -> r.getQuestionId().equals(qc.getQuestionId()) && r.getScore() >= r.getMaxScore()).count();
		return (double) passed / subs.size();
	}

	private List<Anomaly> anomaliesInCategory(StudentSubmission sub, Set<Anomaly.Type> category) {
		return sub.getAnomalies().stream().filter(a -> category.contains(a.getType())).collect(Collectors.toList());
	}

	private double computeMedian(List<StudentSubmission> subs) {
		List<Double> scores = subs.stream().map(StudentSubmission::getTotalScore).sorted().collect(Collectors.toList());
		if (scores.isEmpty()) {
			return 0;
		}
		int n = scores.size();
		return n % 2 == 0 ? (scores.get(n / 2 - 1) + scores.get(n / 2)) / 2.0 : scores.get(n / 2);
	}

	// ══════════════════════════════════════════════════════════════════════════
	// Formatting utilities
	// ══════════════════════════════════════════════════════════════════════════

	private String fmt(double v) {
		if (v == Math.floor(v)) {
			return String.valueOf((int) v);
		}
		return String.format("%.1f", v);
	}

	private String pct(double ratio) {
		return (int) Math.round(ratio * 100) + "%";
	}
}

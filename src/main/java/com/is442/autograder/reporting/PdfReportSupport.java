package com.is442.autograder.reporting;

import java.awt.Color;
import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.is442.autograder.model.Anomaly;
import com.is442.autograder.model.QuestionResult;
import com.is442.autograder.model.StudentSubmission;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;

/**
 * Shared support for PDF reporting: theme constants, anomaly groupings,
 * formatting helpers, and common PDF element builders.
 */
final class PdfReportSupport {

	static final float MARGIN = 54f;

	static final Color HEADER_BACKGROUND = new Color(26, 58, 92);
	static final Color TABLE_HEADER_BACKGROUND = new Color(210, 224, 244);
	static final Color ALTERNATE_ROW_BACKGROUND = new Color(248, 250, 253);
	static final Color SECTION_BAR = new Color(26, 58, 92);
	static final Color PASS_COLOR = new Color(212, 244, 216);
	static final Color PARTIAL_COLOR = new Color(255, 251, 205);
	static final Color FAIL_COLOR = new Color(255, 218, 218);
	static final Color TIMEOUT_COLOR = new Color(255, 231, 196);
	static final Color MISSING_COLOR = new Color(232, 232, 232);
	static final Color WARN_STATUS = new Color(180, 100, 20);

	static final Set<Anomaly.Type> STRUCTURAL_TYPES = EnumSet.of(Anomaly.Type.FOLDER_NOT_RENAMED,
			Anomaly.Type.STUDENT_ID_AS_FOLDER, Anomaly.Type.NO_PARENT_FOLDER, Anomaly.Type.EXTRA_NESTING,
			Anomaly.Type.MISSING_QUESTION_FOLDER, Anomaly.Type.MISSING_JAVA_FILE, Anomaly.Type.IDENTITY_MISMATCH);

	static final Set<Anomaly.Type> METADATA_TYPES = EnumSet.of(Anomaly.Type.MISSING_HEADER,
			Anomaly.Type.INCOMPLETE_HEADER);

	static final Set<Anomaly.Type> EXECUTION_TYPES = EnumSet.of(Anomaly.Type.COMPILATION_ERROR,
			Anomaly.Type.RUNTIME_ERROR, Anomaly.Type.EXECUTION_TIMEOUT);

	private PdfReportSupport() {
	}

	static String scoreStatus(QuestionResult result) {
		if (!result.isCompiled()) {
			return "Question files not found".equals(result.getErrorMessage()) ? "MISSING" : "ERR-COMP";
		}
		if (!result.isExecuted()) {
			return result.getErrorMessage() != null && result.getErrorMessage().contains("timed out") ? "TIMEOUT"
					: "RUN-ERR";
		}
		if (result.getScore() >= result.getMaxScore()) {
			return "PASS";
		}
		return result.getScore() > 0 ? "PARTIAL" : "FAIL";
	}

	static Color statusColor(String status) {
		return switch (status) {
			case "PASS" -> PASS_COLOR;
			case "PARTIAL" -> PARTIAL_COLOR;
			case "FAIL", "ERR-COMP", "RUN-ERR" -> FAIL_COLOR;
			case "TIMEOUT" -> TIMEOUT_COLOR;
			case "MISSING" -> MISSING_COLOR;
			default -> Color.WHITE;
		};
	}

	static String buildCompileErrorText(String errorMessage) {
		if (errorMessage == null || errorMessage.isBlank()) {
			return "Compilation failed.";
		}
		StringBuilder builder = new StringBuilder("Compilation failed:\n");
		int printed = 0;
		for (String line : errorMessage.split("\n")) {
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("Note:")) {
				continue;
			}
			builder.append(trimmed).append("\n");
			if (++printed >= 6) {
				builder.append("[... see logs for full error]");
				break;
			}
		}
		return builder.toString();
	}

	static String fmt(double value) {
		if (value == Math.floor(value)) {
			return String.valueOf((int) value);
		}
		return String.format("%.1f", value);
	}

	static String pct(double ratio) {
		return (int) Math.round(ratio * 100) + "%";
	}

	static PdfPTable newTable(float[] widths) throws IOException {
		PdfPTable table = new PdfPTable(widths.length);
		table.setWidthPercentage(100);
		table.setWidths(widths);
		table.setSpacingBefore(4f);
		table.setSpacingAfter(4f);
		return table;
	}

	static void addHeaderRow(PdfPTable table, String... headers) {
		Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.BOLD, new Color(40, 55, 80));
		for (String header : headers) {
			PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
			cell.setBackgroundColor(new Color(TABLE_HEADER_BACKGROUND.getRed(), TABLE_HEADER_BACKGROUND.getGreen(),
					TABLE_HEADER_BACKGROUND.getBlue()));
			cell.setPadding(5f);
			cell.setBorderColor(new Color(180, 200, 225));
			cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
			table.addCell(cell);
		}
	}

	static void addDataRow(PdfPTable table, boolean alternate, String... values) {
		Font dataFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
		Color rowBackground = alternate ? ALTERNATE_ROW_BACKGROUND : Color.WHITE;
		for (int index = 0; index < values.length; index++) {
			PdfPCell cell = new PdfPCell(new Phrase(values[index] != null ? values[index] : "", dataFont));
			cell.setBackgroundColor(
					new Color(rowBackground.getRed(), rowBackground.getGreen(), rowBackground.getBlue()));
			cell.setPadding(4.5f);
			cell.setBorderColor(new Color(210, 210, 210));
			cell.setHorizontalAlignment(index == 0 ? Element.ALIGN_LEFT : Element.ALIGN_CENTER);
			cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
			table.addCell(cell);
		}
	}

	static void addTwoColumnTable(Document document, String column1, String column2, String[][] rows)
			throws IOException {
		PdfPTable table = newTable(new float[]{250, 240});
		addHeaderRow(table, column1, column2);
		boolean alternate = false;
		for (String[] row : rows) {
			addDataRow(table, alternate, row[0], row[1]);
			alternate = !alternate;
		}
		document.add(table);
	}

	static void addRawCell(PdfPTable table, String text, Color background, Font font, int horizontalAlignment) {
		PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
		cell.setBackgroundColor(new Color(background.getRed(), background.getGreen(), background.getBlue()));
		cell.setHorizontalAlignment(horizontalAlignment);
		cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
		cell.setPadding(4.5f);
		cell.setBorderColor(new Color(180, 200, 225));
		table.addCell(cell);
	}

	static PdfPCell styledCell(String text, Font font, Color background, int horizontalAlignment) {
		PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
		cell.setBackgroundColor(new Color(background.getRed(), background.getGreen(), background.getBlue()));
		cell.setHorizontalAlignment(horizontalAlignment);
		cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
		cell.setPadding(4.5f);
		cell.setBorderColor(new Color(210, 210, 210));
		return cell;
	}

	static void addDetailRow(PdfPTable table, Font keyFont, Font valueFont, String key, String value) {
		PdfPCell keyCell = new PdfPCell(new Phrase(key, keyFont));
		keyCell.setBorderColor(new Color(200, 200, 200));
		keyCell.setPadding(4f);
		keyCell.setBackgroundColor(new Color(245, 247, 252));
		table.addCell(keyCell);

		PdfPCell valueCell = new PdfPCell(new Phrase(value != null ? value : "", valueFont));
		valueCell.setBorderColor(new Color(200, 200, 200));
		valueCell.setPadding(4f);
		table.addCell(valueCell);
	}

	static void addStudentDetailBlock(Document document, StudentSubmission submission, List<String> lines)
			throws IOException {
		PdfPTable table = newTable(new float[]{490});
		table.setSpacingBefore(3f);
		table.setSpacingAfter(6f);
		Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, Font.BOLD,
				new Color(26, 58, 92));
		Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, WARN_STATUS);

		PdfPCell headerCell = new PdfPCell(new Phrase(submission.getDisplayName(), headerFont));
		headerCell.setBackgroundColor(new Color(235, 243, 255));
		headerCell.setPadding(5f);
		headerCell.setBorderColor(new Color(180, 210, 240));
		table.addCell(headerCell);

		for (String line : lines) {
			PdfPCell bodyCell = new PdfPCell(new Phrase("  ⚠ " + line, bodyFont));
			bodyCell.setBackgroundColor(Color.WHITE);
			bodyCell.setPadding(4f);
			bodyCell.setBorderColor(new Color(220, 220, 220));
			table.addCell(bodyCell);
		}
		document.add(table);
	}

	static void addSectionHeading(Document document, String number, String title) throws IOException {
		Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, Font.BOLD, new Color(255, 255, 255));
		Paragraph paragraph = new Paragraph("Section " + number + ": " + title, sectionFont);
		paragraph.setSpacingBefore(8f);
		paragraph.setSpacingAfter(10f);

		PdfPTable banner = new PdfPTable(1);
		banner.setWidthPercentage(100);
		banner.setSpacingBefore(4f);
		banner.setSpacingAfter(8f);
		PdfPCell cell = new PdfPCell(paragraph);
		cell.setBackgroundColor(new Color(SECTION_BAR.getRed(), SECTION_BAR.getGreen(), SECTION_BAR.getBlue()));
		cell.setPadding(8f);
		cell.setBorder(Rectangle.NO_BORDER);
		banner.addCell(cell);
		document.add(banner);
	}

	static void addSubHeading(Document document, String text) throws IOException {
		Font font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.BOLD, new Color(60, 80, 110));
		Paragraph paragraph = new Paragraph(text, font);
		paragraph.setSpacingBefore(10f);
		paragraph.setSpacingAfter(4f);
		document.add(paragraph);
	}

	static void addBodyText(Document document, String text) throws IOException {
		Font font = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(80, 80, 80));
		Paragraph paragraph = new Paragraph(text, font);
		paragraph.setSpacingBefore(4f);
		paragraph.setSpacingAfter(4f);
		document.add(paragraph);
	}

	static void gap(Document document) throws IOException {
		Paragraph paragraph = new Paragraph(" ");
		paragraph.setSpacingAfter(4f);
		document.add(paragraph);
	}
}

package com.is442.autograder.generation;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Extracts text from exam PDF files using Apache PDFBox.
 */
public class ExamPdfParser {

	/**
	 * Extract all text from the PDF.
	 *
	 * @param pdfPath
	 *            path to the PDF file
	 * @return full text content
	 */
	public String extractAllText(Path pdfPath) throws IOException {
		try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
			PDFTextStripper stripper = new PDFTextStripper();
			return stripper.getText(document);
		}
	}

	/**
	 * Best-effort extraction of a question-specific section from the PDF. Looks for
	 * headings like "Question 1a", "Q1a", etc. and returns text until the next
	 * heading.
	 *
	 * @param pdfPath
	 *            path to the PDF file
	 * @param questionId
	 *            e.g. "Q1a"
	 * @return extracted section text, or full PDF text if section not found
	 */
	public String extractQuestionSection(Path pdfPath, String questionId) throws IOException {
		String allText = extractAllText(pdfPath);

		// Build patterns to search for: "Q1a", "Question 1a", "1a)", "1a."
		String normalized = questionId.replaceFirst("^Q", "");
		String[] patterns = {"Q" + normalized, "Question " + normalized, normalized + ")", normalized + "."};

		int startIdx = -1;
		for (String pattern : patterns) {
			int idx = allText.indexOf(pattern);
			if (idx >= 0) {
				startIdx = idx;
				break;
			}
		}

		if (startIdx < 0) {
			// Section not found — return full text with a note
			return "Full exam content (section '" + questionId + "' not found):\n\n" + allText;
		}

		// Find the end: next question heading or end of document
		int endIdx = allText.length();
		// Try to find next question marker after the current one
		String[] nextMarkers = {"Question ", "\nQ1", "\nQ2", "\nQ3", "\nQ4", "\nQ5"};
		for (String marker : nextMarkers) {
			int next = allText.indexOf(marker, startIdx + 10);
			if (next > startIdx && next < endIdx) {
				endIdx = next;
			}
		}

		return allText.substring(startIdx, endIdx).trim();
	}
}

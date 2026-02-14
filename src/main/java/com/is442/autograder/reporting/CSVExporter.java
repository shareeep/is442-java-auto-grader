package com.is442.autograder.reporting;

import com.is442.autograder.model.QuestionConfig;
import com.is442.autograder.model.QuestionResult;
import com.is442.autograder.model.StudentSubmission;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exports grading results to CSV in the format matching IS442-ScoreSheet.csv.
 *
 * CSV format: OrgDefinedId,Username,Last Name,First Name,Email, Calculated
 * Final Grade Numerator,Calculated Final Grade Denominator,End-of-Line
 * Indicator
 *
 * Username values are prefixed with # (e.g. "#ping.lee.2023").
 */
public class CSVExporter {

	/**
	 * Read the template CSV, match students by username, fill in scores, and write
	 * output.
	 *
	 * @param templateCsvPath
	 *            path to the original IS442-ScoreSheet.csv
	 * @param outputCsvPath
	 *            path to write the graded CSV
	 * @param submissions
	 *            graded submissions to export
	 */
	public void export(Path templateCsvPath, Path outputCsvPath, List<StudentSubmission> submissions)
			throws IOException {

		// Build a map of username -> totalScore
		Map<String, Double> scoreMap = new HashMap<>();
		for (StudentSubmission sub : submissions) {
			if (sub.getUsername() != null) {
				scoreMap.put(sub.getUsername().toLowerCase(), sub.getTotalScore());
			}
		}

		List<String> inputLines = Files.readAllLines(templateCsvPath);
		List<String> outputLines = new ArrayList<>();

		for (int i = 0; i < inputLines.size(); i++) {
			String line = inputLines.get(i);

			if (i == 0) {
				// Header line — pass through
				outputLines.add(line);
				continue;
			}

			if (line.trim().isEmpty()) {
				outputLines.add(line);
				continue;
			}

			// Parse CSV line
			String[] parts = line.split(",", -1);
			if (parts.length < 8) {
				outputLines.add(line); // malformed, keep as-is
				continue;
			}

			// Username is in column index 1, prefixed with #
			String rawUsername = parts[1].trim();
			String username = rawUsername.startsWith("#") ? rawUsername.substring(1) : rawUsername;

			// Look up score
			Double score = scoreMap.get(username.toLowerCase());
			if (score != null) {
				// Column index 5 = "Calculated Final Grade Numerator"
				parts[5] = String.valueOf(score);
			}

			outputLines.add(String.join(",", parts));
		}

		// Ensure output directory exists
		Files.createDirectories(outputCsvPath.getParent());
		Files.write(outputCsvPath, outputLines);
	}

	/**
	 * Export a detailed grading report CSV with per-question scores.
	 */
	public void exportDetailed(Path outputPath, List<StudentSubmission> submissions,
			List<QuestionConfig> questionConfigs) throws IOException {

		Files.createDirectories(outputPath.getParent());

		try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(outputPath))) {
			// Header
			StringBuilder header = new StringBuilder("OrgDefinedId,Username,Name");
			for (QuestionConfig qc : questionConfigs) {
				header.append(",").append(qc.getQuestionId());
			}
			header.append(",Total,Anomalies");
			writer.println(header);

			// Data rows
			List<StudentSubmission> sortedSubs = submissions.stream().sorted((a, b) -> {
				String aId = normalizeOrgId(a.getOrgDefinedId());
				String bId = normalizeOrgId(b.getOrgDefinedId());
				if (aId.isEmpty() && bId.isEmpty()) {
					return a.getDisplayName().compareToIgnoreCase(b.getDisplayName());
				}
				if (aId.isEmpty()) {
					return 1;
				}
				if (bId.isEmpty()) {
					return -1;
				}
				int idCompare = aId.compareToIgnoreCase(bId);
				return idCompare != 0 ? idCompare : a.getDisplayName().compareToIgnoreCase(b.getDisplayName());
			}).toList();

			for (StudentSubmission sub : sortedSubs) {
				StringBuilder row = new StringBuilder();
				// OrgDefinedId: only populated if scoresheet was provided
				row.append(sub.getOrgDefinedId() != null ? sub.getOrgDefinedId() : "");
				row.append(",").append(sub.getDisplayName());
				row.append(",").append(sub.getName() != null ? sub.getName() : "");

				for (QuestionConfig qc : questionConfigs) {
					double score = sub.getResults().stream().filter(r -> r.getQuestionId().equals(qc.getQuestionId()))
							.mapToDouble(QuestionResult::getScore).findFirst().orElse(0.0);
					row.append(",").append(score);
				}

				row.append(",").append(sub.getTotalScore());

				// Anomalies count
				row.append(",").append(sub.getAnomalies().size());

				writer.println(row);
			}
		}
	}

	private String normalizeOrgId(String orgId) {
		if (orgId == null) {
			return "";
		}
		String trimmed = orgId.trim();
		if (trimmed.startsWith("#")) {
			return trimmed.substring(1);
		}
		return trimmed;
	}
}

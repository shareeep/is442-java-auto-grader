package com.is442.autograder.reporting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.is442.autograder.model.QuestionConfig;
import com.is442.autograder.model.QuestionResult;
import com.is442.autograder.model.StudentSubmission;

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
	public void export(Path templateCsvPath, Path outputCsvPath, List<StudentSubmission> submissions,
			List<QuestionConfig> questionConfigs) throws IOException {

		// Build a map of username -> submission
		Map<String, StudentSubmission> subMap = new HashMap<>();
		for (StudentSubmission sub : submissions) {
			if (sub.getUsername() != null) {
				subMap.put(sub.getUsername().toLowerCase(), sub);
			}
		}

		// Header: OrgDefinedId,Username,Name,Q1a,Q1b,...,Total
		List<String> headerList = new java.util.ArrayList<>();
		headerList.add("OrgDefinedId");
		headerList.add("Username");
		headerList.add("Name");
		for (QuestionConfig qc : questionConfigs) {
			headerList.add(qc.getQuestionId());
		}
		headerList.add("Total");

		// Build one row per student from the template (preserves all students,
		// sorted alphabetically by Name)
		List<List<String>> dataRows = new java.util.ArrayList<>();
		for (ScoresheetRow scoresheetRow : ScoresheetParser.parse(templateCsvPath)) {
			StudentSubmission sub = subMap.get(scoresheetRow.username().toLowerCase());

			List<String> row = new java.util.ArrayList<>();
			row.add(scoresheetRow.orgDefinedId());
			row.add(scoresheetRow.rawUsername());
			row.add(scoresheetRow.displayName());
			for (QuestionConfig qc : questionConfigs) {
				double score = sub != null
						? sub.getResults().stream().filter(r -> r.getQuestionId().equals(qc.getQuestionId()))
								.mapToDouble(QuestionResult::getScore).findFirst().orElse(0.0)
						: 0.0;
				row.add(formatScore(score));
			}
			row.add(sub != null ? formatScore(sub.getTotalScore()) : "0");

			dataRows.add(row);
		}

		// Sort alphabetically by Name (col 2)
		dataRows.sort((a, b) -> a.get(2).compareToIgnoreCase(b.get(2)));

		List<String> outputLines = new java.util.ArrayList<>();
		outputLines.add(String.join(",", headerList));
		for (List<String> row : dataRows) {
			outputLines.add(String.join(",", row));
		}

		Files.createDirectories(outputCsvPath.getParent());
		Files.write(outputCsvPath, outputLines);
	}

	private String formatScore(double score) {
		return String.format("%.1f", score);
	}

}

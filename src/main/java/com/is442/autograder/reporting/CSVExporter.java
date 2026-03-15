package com.is442.autograder.reporting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

		// Read the template to get OrgDefinedId / Username / Name for each student
		List<String> inputLines = Files.readAllLines(templateCsvPath);

		// Header: OrgDefinedId,Username,Name,Q1a,Q1b,...,Total
		List<String> headerList = new ArrayList<>();
		headerList.add("OrgDefinedId");
		headerList.add("Username");
		headerList.add("Name");
		for (QuestionConfig qc : questionConfigs) {
			headerList.add(qc.getQuestionId());
		}
		headerList.add("Total");

		// Build one row per student from the template (preserves all students,
		// sorted alphabetically by Name)
		List<List<String>> dataRows = new ArrayList<>();
		for (int i = 1; i < inputLines.size(); i++) {
			String line = inputLines.get(i);
			if (line.trim().isEmpty()) {
				continue;
			}
			String[] parts = line.split(",", -1);
			if (parts.length < 5) {
				continue;
			}

			// Template columns: [0]=OrgDefinedId [1]=Username [2]=Last Name [3]=First Name
			String orgId = parts[0].trim();
			String rawUsername = parts[1].trim();
			// Name = First Name + Last Name (cols 3 then 2); last name is "_" placeholder
			String lastName = parts[2].trim().equals("_") ? "" : parts[2].trim();
			String firstName = parts[3].trim();
			String name = (firstName + (lastName.isEmpty() ? "" : " " + lastName)).trim();

			String username = rawUsername.startsWith("#") ? rawUsername.substring(1) : rawUsername;
			StudentSubmission sub = subMap.get(username.toLowerCase());

			List<String> row = new ArrayList<>();
			row.add(orgId);
			row.add(rawUsername);
			row.add(name);
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

		List<String> outputLines = new ArrayList<>();
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

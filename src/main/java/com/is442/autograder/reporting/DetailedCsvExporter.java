package com.is442.autograder.reporting;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.is442.autograder.model.QuestionConfig;
import com.is442.autograder.model.QuestionResult;
import com.is442.autograder.model.StudentSubmission;

public class DetailedCsvExporter {

	public void exportDetailed(Path outputPath, List<StudentSubmission> submissions,
			List<QuestionConfig> questionConfigs) throws IOException {

		Files.createDirectories(outputPath.getParent());

		try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(outputPath))) {
			// Header
			StringBuilder header = new StringBuilder("Username,Name");
			for (QuestionConfig qc : questionConfigs) {
				header.append(",").append(qc.getQuestionId());
			}
			header.append(",Total,Anomalies");
			writer.println(header);

			// Data rows
			for (StudentSubmission sub : submissions) {
				StringBuilder row = new StringBuilder();
				row.append(sub.getDisplayName());
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
}

package com.is442.autograder.reporting;

import java.util.List;
import java.util.Map;

import org.jfree.data.category.DefaultCategoryDataset;

import com.is442.autograder.model.Anomaly;
import com.is442.autograder.model.QuestionConfig;
import com.is442.autograder.model.StudentSubmission;

/**
 * Builds chart datasets for reporting visualisations.
 */
final class ChartDatasetFactory {

	private ChartDatasetFactory() {
	}

	static DefaultCategoryDataset scoreDistributionDataset(List<StudentSubmission> submissions, double maxPossible) {
		DefaultCategoryDataset dataset = new DefaultCategoryDataset();
		int binWidth = Math.max(1, (int) Math.ceil(maxPossible / 5.0));
		for (int low = 0; low < (int) maxPossible; low += binWidth) {
			int high = (int) Math.min(low + binWidth - 1, (int) maxPossible - 1);
			final double lowerBound = low;
			final double upperBound = high;
			long count = submissions.stream()
					.filter(submission -> submission.getTotalScore() >= lowerBound
							&& submission.getTotalScore() <= upperBound)
					.count();
			String label = low == high ? String.valueOf(low) : low + "–" + high;
			dataset.addValue(count, "Students", label);
		}

		long perfectCount = submissions.stream().filter(submission -> submission.getTotalScore() >= maxPossible).count();
		dataset.addValue(perfectCount, "Students", (int) maxPossible + " (full)");
		return dataset;
	}

	static DefaultCategoryDataset passRateDataset(List<StudentSubmission> submissions, List<QuestionConfig> questions) {
		DefaultCategoryDataset dataset = new DefaultCategoryDataset();
		int totalSubmissions = submissions.size();
		for (QuestionConfig question : questions) {
			long passed = submissions.stream().flatMap(submission -> submission.getResults().stream())
					.filter(result -> result.getQuestionId().equals(question.getQuestionId())
							&& result.getScore() >= result.getMaxScore())
					.count();
			double passRate = totalSubmissions == 0 ? 0 : (double) passed / totalSubmissions * 100.0;
			dataset.addValue(passRate, "Pass Rate", question.getQuestionId());
		}
		return dataset;
	}

	static DefaultCategoryDataset anomalyFrequencyDataset(Map<Anomaly.Type, Long> typeCounts) {
		DefaultCategoryDataset dataset = new DefaultCategoryDataset();
		typeCounts.entrySet().stream().sorted(Map.Entry.<Anomaly.Type, Long>comparingByValue().reversed()).limit(8)
				.forEach(entry -> dataset.addValue(entry.getValue(), "Occurrences",
						ChartGenerator.friendlyName(entry.getKey())));
		return dataset;
	}
}

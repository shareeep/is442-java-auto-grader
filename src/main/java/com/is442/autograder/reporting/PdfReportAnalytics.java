package com.is442.autograder.reporting;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.is442.autograder.model.Anomaly;
import com.is442.autograder.model.QuestionConfig;
import com.is442.autograder.model.QuestionResult;
import com.is442.autograder.model.StudentSubmission;

/**
 * Shared analytics used by PDF reporting sections.
 */
final class PdfReportAnalytics {

	private PdfReportAnalytics() {
	}

	static OverviewStats computeOverviewStats(List<StudentSubmission> submissions, double classMax) {
		int total = submissions.size();
		long withErrors = submissions.stream()
				.filter(s -> s.getAnomalies().stream().anyMatch(a -> a.getSeverity() == Anomaly.Severity.ERROR))
				.count();
		long successful = total - withErrors;
		long passing = classMax > 0 ? submissions.stream().filter(s -> s.getTotalScore() / classMax >= 0.5).count() : 0;
		long totalAnomalies = submissions.stream().mapToLong(s -> s.getAnomalies().size()).sum();
		double average = submissions.stream().mapToDouble(StudentSubmission::getTotalScore).average().orElse(0);
		double highest = submissions.stream().mapToDouble(StudentSubmission::getTotalScore).max().orElse(0);
		double lowest = submissions.stream().mapToDouble(StudentSubmission::getTotalScore).min().orElse(0);
		double median = computeMedian(submissions);
		return new OverviewStats(total, withErrors, successful, passing, totalAnomalies, average, highest, lowest,
				median);
	}

	static List<QuestionStats> computeQuestionStats(List<StudentSubmission> submissions,
			List<QuestionConfig> questions) {
		int total = submissions.size();
		return questions.stream().map(question -> {
			String questionId = question.getQuestionId();
			long attempted = submissions.stream().flatMap(s -> s.getResults().stream())
					.filter(r -> r.getQuestionId().equals(questionId) && (r.isCompiled() || r.isExecuted())).count();
			long fullPass = submissions.stream().flatMap(s -> s.getResults().stream())
					.filter(r -> r.getQuestionId().equals(questionId) && r.getScore() >= r.getMaxScore()).count();
			long partial = submissions.stream().flatMap(s -> s.getResults().stream()).filter(
					r -> r.getQuestionId().equals(questionId) && r.getScore() > 0 && r.getScore() < r.getMaxScore())
					.count();
			double average = submissions.stream().flatMap(s -> s.getResults().stream())
					.filter(r -> r.getQuestionId().equals(questionId)).mapToDouble(QuestionResult::getScore).average()
					.orElse(0);
			return new QuestionStats(question, total, attempted, fullPass, partial, average);
		}).collect(Collectors.toList());
	}

	static Map<Anomaly.Type, Long> countAnomaliesByType(List<StudentSubmission> submissions) {
		return submissions.stream().flatMap(s -> s.getAnomalies().stream()).collect(Collectors
				.groupingBy(Anomaly::getType, () -> new EnumMap<>(Anomaly.Type.class), Collectors.counting()));
	}

	static InsightStats computeInsightStats(List<StudentSubmission> submissions, List<QuestionConfig> questions,
			Set<Anomaly.Type> structuralTypes, Set<Anomaly.Type> metadataTypes) {
		int total = submissions.size();
		double classMax = questions.stream().mapToDouble(QuestionConfig::getMaxScore).sum();
		long compileErrors = submissions.stream()
				.filter(s -> s.getAnomalies().stream().anyMatch(a -> a.getType() == Anomaly.Type.COMPILATION_ERROR))
				.count();
		long timeouts = submissions.stream()
				.filter(s -> s.getAnomalies().stream().anyMatch(a -> a.getType() == Anomaly.Type.EXECUTION_TIMEOUT))
				.count();
		long structuralIssues = submissions.stream().filter(s -> !anomaliesInCategory(s, structuralTypes).isEmpty())
				.count();
		long metadataIssues = submissions.stream().filter(s -> !anomaliesInCategory(s, metadataTypes).isEmpty())
				.count();
		long zeroScore = submissions.stream().filter(s -> s.getTotalScore() == 0).count();
		long passing = classMax > 0 ? submissions.stream().filter(s -> s.getTotalScore() / classMax >= 0.5).count() : 0;
		QuestionConfig hardestQuestion = questions.stream()
				.min(Comparator.comparingDouble(question -> passRateForQuestion(submissions, question))).orElse(null);
		QuestionConfig easiestQuestion = questions.stream()
				.max(Comparator.comparingDouble(question -> passRateForQuestion(submissions, question))).orElse(null);
		return new InsightStats(total, classMax, compileErrors, timeouts, structuralIssues, metadataIssues, zeroScore,
				passing, hardestQuestion, easiestQuestion);
	}

	static double passRateForQuestion(List<StudentSubmission> submissions, QuestionConfig question) {
		if (submissions.isEmpty()) {
			return 0.0;
		}
		long passed = submissions.stream().flatMap(s -> s.getResults().stream())
				.filter(r -> r.getQuestionId().equals(question.getQuestionId()) && r.getScore() >= r.getMaxScore())
				.count();
		return (double) passed / submissions.size();
	}

	static List<Anomaly> anomaliesInCategory(StudentSubmission submission, Set<Anomaly.Type> category) {
		return submission.getAnomalies().stream().filter(a -> category.contains(a.getType()))
				.collect(Collectors.toList());
	}

	static double computeMedian(List<StudentSubmission> submissions) {
		List<Double> scores = submissions.stream().map(StudentSubmission::getTotalScore).sorted()
				.collect(Collectors.toList());
		if (scores.isEmpty()) {
			return 0;
		}
		int count = scores.size();
		return count % 2 == 0 ? (scores.get(count / 2 - 1) + scores.get(count / 2)) / 2.0 : scores.get(count / 2);
	}

	record OverviewStats(int total, long withErrors, long successful, long passing, long totalAnomalies, double average,
			double highest, double lowest, double median) {
	}

	record QuestionStats(QuestionConfig question, int totalSubmissions, long attempted, long fullPass, long partial,
			double averageScore) {
	}

	record InsightStats(int total, double classMax, long compileErrors, long timeouts, long structuralIssues,
			long metadataIssues, long zeroScore, long passing, QuestionConfig hardestQuestion,
			QuestionConfig easiestQuestion) {
	}
}

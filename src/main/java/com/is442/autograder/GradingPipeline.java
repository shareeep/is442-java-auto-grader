package com.is442.autograder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Stream;

import com.is442.autograder.config.AppConfig;
import com.is442.autograder.execution.GradingEngine;
import com.is442.autograder.execution.ProcessRunner;
import com.is442.autograder.extraction.IdentityResolver;
import com.is442.autograder.extraction.StructureNormalizer;
import com.is442.autograder.extraction.ZipExtractor;
import com.is442.autograder.model.Anomaly;
import com.is442.autograder.model.QuestionConfig;
import com.is442.autograder.model.StudentSubmission;
import com.is442.autograder.reporting.CSVExporter;
import com.is442.autograder.reporting.ConsoleLogCapture;
import com.is442.autograder.reporting.ConsoleReporter;
import com.is442.autograder.reporting.PdfReportGenerator;
import com.is442.autograder.reporting.QuestionLogWriter;
import com.is442.autograder.reporting.ScoresheetEnricher;
import com.is442.autograder.util.FileUtils;
import com.is442.autograder.validation.SubmissionValidator;

/**
 * Orchestrates the full grading pipeline: 1. Scan for ZIP files 2. Extract each
 * ZIP 3. Resolve identity + normalize structure 4. Validate submission 5. Grade
 * all questions 6. Enrich from scoresheet (if provided) 7. Export results
 */
public class GradingPipeline {

	private static final Logger LOGGER = Logger.getLogger(GradingPipeline.class.getName());
	private static final DateTimeFormatter RUN_ID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private final AppConfig config;
	private final ZipExtractor zipExtractor;
	private final IdentityResolver identityResolver;
	private final StructureNormalizer structureNormalizer;
	private final SubmissionValidator submissionValidator;
	private final CSVExporter csvExporter;
	private final ConsoleReporter consoleReporter;
	private final ScoresheetEnricher scoresheetEnricher;
	private List<QuestionConfig> inferredQuestionConfigs;

	public GradingPipeline(AppConfig config) {
		this.config = config;
		this.zipExtractor = new ZipExtractor();
		this.identityResolver = new IdentityResolver();
		this.structureNormalizer = new StructureNormalizer(identityResolver);
		this.submissionValidator = new SubmissionValidator(identityResolver);
		this.csvExporter = new CSVExporter();
		this.consoleReporter = new ConsoleReporter();
		this.scoresheetEnricher = new ScoresheetEnricher();
	}

	public void setInferredQuestionConfigs(List<QuestionConfig> configs) {
		this.inferredQuestionConfigs = configs;
	}

	/**
	 * Run the full grading pipeline.
	 *
	 * @param submissionsDir
	 *            directory containing student ZIP files
	 * @param testerFilesDir
	 *            directory containing tester .java files
	 * @param scoresheetPath
	 *            path to the template CSV scoresheet (optional)
	 * @param outputDir
	 *            directory for output files
	 * @return list of graded submissions
	 */
	public List<StudentSubmission> run(Path submissionsDir, Path testerFilesDir, Path scoresheetPath, Path outputDir)
			throws IOException {
		return run(submissionsDir, testerFilesDir, scoresheetPath, outputDir, null);
	}

	/**
	 * Run the full grading pipeline with a per-student callback for live streaming.
	 *
	 * @param onStudentGraded
	 *            called immediately after each student is graded (may be null)
	 */
	public List<StudentSubmission> run(Path submissionsDir, Path testerFilesDir, Path scoresheetPath, Path outputDir,
			Consumer<StudentSubmission> onStudentGraded) throws IOException {
		return run(submissionsDir, testerFilesDir, scoresheetPath, outputDir, onStudentGraded, null);
	}

	public List<StudentSubmission> run(Path submissionsDir, Path testerFilesDir, Path scoresheetPath, Path outputDir,
			Consumer<StudentSubmission> onStudentGraded, Consumer<String> onStudentStarted) throws IOException {

		RunContext runContext = initializeRun(outputDir);

		try {
			List<Path> zipFiles = findZipFiles(submissionsDir);
			if (zipFiles.isEmpty()) {
				System.out.println("No ZIP files found in " + submissionsDir);
				return List.of();
			}

			List<QuestionConfig> questionConfigs = resolveQuestionConfigs();
			List<StudentSubmission> submissions = gradeSubmissions(zipFiles, testerFilesDir, questionConfigs,
					onStudentGraded, onStudentStarted, runContext.gradingEngine());

			completeRun(submissions, questionConfigs, scoresheetPath, runContext.runOutputDir());
			return submissions;
		} finally {
			if (runContext.logCapture() != null) {
				runContext.logCapture().close();
			}
		}
	}

	private RunContext initializeRun(Path outputDir) throws IOException {
		Path runOutputDir = outputDir != null ? createRunOutputDir(outputDir) : null;
		ConsoleLogCapture logCapture = runOutputDir != null ? ConsoleLogCapture.start(runOutputDir) : null;
		QuestionLogWriter logWriter = runOutputDir != null ? new QuestionLogWriter(runOutputDir) : null;
		GradingEngine gradingEngine = new GradingEngine(new ProcessRunner(config.getTimeoutSeconds()), logWriter,
				consoleReporter, config.getTemplateFolder());
		return new RunContext(runOutputDir, logCapture, gradingEngine);
	}

	private List<QuestionConfig> resolveQuestionConfigs() {
		return inferredQuestionConfigs != null ? inferredQuestionConfigs : config.getQuestionConfigs();
	}

	private List<StudentSubmission> gradeSubmissions(List<Path> zipFiles, Path testerFilesDir,
			List<QuestionConfig> questionConfigs, Consumer<StudentSubmission> onStudentGraded,
			Consumer<String> onStudentStarted, GradingEngine gradingEngine) throws IOException {
		System.out.println("\nFound " + zipFiles.size() + " submission(s) to grade.\n");
		java.util.logging.LogManager.getLogManager().reset();
		consoleReporter.startProgress(zipFiles.size());

		List<StudentSubmission> submissions = new ArrayList<>();
		for (int i = 0; i < zipFiles.size(); i++) {
			if (consoleReporter.isStopRequested()) {
				break;
			}

			StudentSubmission submission = gradeSingleSubmission(zipFiles.get(i), testerFilesDir, questionConfigs,
					onStudentStarted, gradingEngine);
			finalizeSubmission(submission);

			consoleReporter.printProgress(i + 1, zipFiles.size(), submission.getDisplayName());
			submissions.add(submission);

			if (onStudentGraded != null) {
				onStudentGraded.accept(submission);
			}
		}

		consoleReporter.endProgress();
		System.out.println();
		return submissions;
	}

	private StudentSubmission gradeSingleSubmission(Path zipFile, Path testerFilesDir,
			List<QuestionConfig> questionConfigs, Consumer<String> onStudentStarted, GradingEngine gradingEngine)
			throws IOException {
		String zipName = zipFile.getFileName().toString();
		StudentSubmission submission = new StudentSubmission(zipName);

		consoleReporter.updateCurrentStudent(submission.getDisplayName());
		if (onStudentStarted != null) {
			onStudentStarted.accept(submission.getDisplayName());
		}

		try {
			Path tempDir = FileUtils.createTempDir("autograder-" + zipName.replace(".zip", ""));
			Path extractedRoot = zipExtractor.extract(zipFile, tempDir);
			Path submissionRoot = structureNormalizer.normalize(extractedRoot, submission);
			submission.setRootPath(submissionRoot);

			submissionValidator.validate(submissionRoot, questionConfigs, submission);
			logSubmissionAnomalies(submission);
			gradingEngine.grade(submissionRoot, testerFilesDir, questionConfigs, submission);
		} catch (SecurityException e) {
			LOGGER.warning("Security issue with " + zipName + ": " + e.getMessage());
			System.err.println("  Skipping " + zipName + " (security issue): " + e.getMessage());
		} catch (Exception e) {
			LOGGER.warning("Error processing " + zipName + ": " + e.getMessage());
			System.err.println("  Error processing " + zipName + ": " + e.getMessage());
		}

		return submission;
	}

	private void logSubmissionAnomalies(StudentSubmission submission) {
		consoleReporter.beginStudentLog(submission.getDisplayName());
		for (Anomaly anomaly : submission.getAnomalies()) {
			if (anomaly.getSeverity() == Anomaly.Severity.ERROR) {
				consoleReporter.logError(anomaly.getDescription());
			} else {
				consoleReporter.logWarning(anomaly.getDescription());
			}
		}
	}

	private void finalizeSubmission(StudentSubmission submission) {
		if ((submission.getName() == null || submission.getName().isEmpty()) && submission.getUsername() != null) {
			submission.setName(identityResolver.deriveNameFromUsername(submission.getUsername()));
		}
	}

	private void completeRun(List<StudentSubmission> submissions, List<QuestionConfig> questionConfigs,
			Path scoresheetPath, Path runOutputDir) throws IOException {
		if (consoleReporter.isStopRequested()) {
			System.out.println(
					"\u001B[33mGrading stopped early by user. Results below reflect only graded students.\u001B[0m");
		}

		if (scoresheetPath != null && Files.isRegularFile(scoresheetPath)) {
			scoresheetEnricher.enrich(scoresheetPath, submissions);
		}

		consoleReporter.printSummary(submissions, questionConfigs);

		if (!consoleReporter.isStopRequested() && scoresheetPath != null && Files.isRegularFile(scoresheetPath)
				&& runOutputDir != null) {
			Path outputCsv = runOutputDir.resolve("IS442-ScoreSheet-Graded.csv");
			csvExporter.export(scoresheetPath, outputCsv, submissions, questionConfigs);
			System.out.println("\nScoresheet exported to: " + outputCsv);
		}

		printRunArtifacts(runOutputDir);

		if (!consoleReporter.isStopRequested() && runOutputDir != null) {
			Path pdfReport = runOutputDir.resolve("instructor-report.pdf");
			new PdfReportGenerator(config.getAssessmentName()).generate(submissions, questionConfigs, pdfReport);
			System.out.println("PDF report exported to: " + pdfReport);
		}
	}

	private void printRunArtifacts(Path runOutputDir) {
		if (runOutputDir == null) {
			return;
		}

		System.out.println("Full logs exported to: " + runOutputDir.resolve("logs"));
		System.out.println("Run log exported to: " + runOutputDir.resolve("logs").resolve("run.log"));
	}

	private Path createRunOutputDir(Path baseOutputDir) throws IOException {
		if (baseOutputDir == null) {
			return null;
		}
		String runId = LocalDateTime.now().format(RUN_ID_FORMATTER);
		Path runDir = baseOutputDir.resolve(runId);
		Files.createDirectories(runDir);
		return runDir;
	}

	/**
	 * Find all .zip files in a directory (non-recursive).
	 */
	private List<Path> findZipFiles(Path dir) throws IOException {
		List<Path> zips = new ArrayList<>();
		try (Stream<Path> stream = Files.list(dir)) {
			stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".zip")).sorted().forEach(zips::add);
		}
		return zips;
	}

	private record RunContext(Path runOutputDir, ConsoleLogCapture logCapture, GradingEngine gradingEngine) {
	}
}

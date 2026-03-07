package com.is442.autograder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;

import com.is442.autograder.config.AppConfig;
import com.is442.autograder.execution.GradingEngine;
import com.is442.autograder.execution.ProcessRunner;
import com.is442.autograder.extraction.IdentityResolver;
import com.is442.autograder.extraction.StructureNormalizer;
import com.is442.autograder.extraction.ZipExtractor;
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

		ConsoleLogCapture logCapture = null;
		Path runOutputDir = outputDir;
		if (outputDir != null) {
			runOutputDir = createRunOutputDir(outputDir);
			logCapture = ConsoleLogCapture.start(runOutputDir);
		}

		QuestionLogWriter logWriter = runOutputDir != null ? new QuestionLogWriter(runOutputDir) : null;
		GradingEngine gradingEngine = new GradingEngine(new ProcessRunner(config.getTimeoutSeconds()), logWriter,
				consoleReporter, config.getTemplateFolder());

		try {
			// 1. Find all ZIP files
			List<Path> zipFiles = findZipFiles(submissionsDir);
			if (zipFiles.isEmpty()) {
				System.out.println("No ZIP files found in " + submissionsDir);
				return List.of();
			}

			System.out.println("\nFound " + zipFiles.size() + " submission(s) to grade.\n");
			java.util.logging.LogManager.getLogManager().reset();
			consoleReporter.startProgress(zipFiles.size());

			List<QuestionConfig> questionConfigs = config.getQuestionConfigs();
			List<StudentSubmission> submissions = new ArrayList<>();

			// 2. Process each ZIP
			for (int i = 0; i < zipFiles.size(); i++) {
				// Honour a stop request (user pressed 'q' during grading)
				if (consoleReporter.isStopRequested()) {
					break;
				}

				Path zipFile = zipFiles.get(i);
				String zipName = zipFile.getFileName().toString();

				StudentSubmission submission = new StudentSubmission(zipName);
				consoleReporter.updateCurrentStudent(submission.getDisplayName());

				try {
					// Extract to temp directory
					Path tempDir = FileUtils.createTempDir("autograder-" + zipName.replace(".zip", ""));
					Path extractedRoot = zipExtractor.extract(zipFile, tempDir);
					// Normalize structure + resolve identity
					Path submissionRoot = structureNormalizer.normalize(extractedRoot, submission);
					submission.setRootPath(submissionRoot);

					// Validate
					submissionValidator.validate(submissionRoot, questionConfigs, submission);

					// Log structural/validation anomalies immediately so the instructor
					// sees them live (compilation/runtime anomalies are logged by GradingEngine)
					consoleReporter.beginStudentLog(submission.getDisplayName());
					for (com.is442.autograder.model.Anomaly a : submission.getAnomalies()) {
						if (a.getSeverity() == com.is442.autograder.model.Anomaly.Severity.ERROR) {
							consoleReporter.logError(a.getDescription());
						} else {
							consoleReporter.logWarning(a.getDescription());
						}
					}

					// Grade
					gradingEngine.grade(submissionRoot, testerFilesDir, questionConfigs, submission);

				} catch (SecurityException e) {
					LOGGER.warning("Security issue with " + zipName + ": " + e.getMessage());
					System.err.println("  ⚠ Skipping " + zipName + " (security issue): " + e.getMessage());
				} catch (Exception e) {
					LOGGER.warning("Error processing " + zipName + ": " + e.getMessage());
					System.err.println("  ✖ Error processing " + zipName + ": " + e.getMessage());
				}

				consoleReporter.printProgress(i + 1, zipFiles.size(), submission.getDisplayName());
				submissions.add(submission);
			}

			consoleReporter.endProgress();
			System.out.println();

			if (consoleReporter.isStopRequested()) {
				System.out.println(
						"\u001B[33mGrading stopped early by user. Results below reflect only graded students.\u001B[0m");
			}

			// 3. Fill missing names using username-derived fallback
			for (StudentSubmission sub : submissions) {
				if ((sub.getName() == null || sub.getName().isEmpty()) && sub.getUsername() != null) {
					sub.setName(identityResolver.deriveNameFromUsername(sub.getUsername()));
				}
			}

			// 4. Enrich from scoresheet (official names + OrgDefinedId) if provided
			if (scoresheetPath != null && Files.isRegularFile(scoresheetPath)) {
				scoresheetEnricher.enrich(scoresheetPath, submissions);
			}

			// 5. Print summary
			consoleReporter.printSummary(submissions, questionConfigs);

			// 6. Export scoresheet (if template provided)
			if (!consoleReporter.isStopRequested() && scoresheetPath != null && Files.isRegularFile(scoresheetPath)) {
				Path outputCsv = runOutputDir.resolve("IS442-ScoreSheet-Graded.csv");
				csvExporter.export(scoresheetPath, outputCsv, submissions, questionConfigs);
				System.out.println("\nScoresheet exported to: " + outputCsv);
			}

			System.out.println("Full logs exported to: " + runOutputDir.resolve("logs"));
			System.out.println("Run log exported to: " + runOutputDir.resolve("logs").resolve("run.log"));

			// 7. Export PDF report
			if (!consoleReporter.isStopRequested()) {
				Path pdfReport = runOutputDir.resolve("instructor-report.pdf");
				new PdfReportGenerator(config.getAssessmentName()).generate(submissions, questionConfigs, pdfReport);
				System.out.println("PDF report exported to: " + pdfReport);
			}

			return submissions;
		} finally {
			if (logCapture != null) {
				logCapture.close();
			}
		}
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
}

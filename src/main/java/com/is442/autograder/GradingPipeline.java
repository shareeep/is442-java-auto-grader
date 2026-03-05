package com.is442.autograder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import com.is442.autograder.reporting.InstructorReportGenerator;
import com.is442.autograder.reporting.QuestionLogWriter;
import com.is442.autograder.util.FileUtils;
import com.is442.autograder.validation.SubmissionValidator;

/**
 * Orchestrates the full grading pipeline: 1. Scan for ZIP files 2. Extract each
 * ZIP 3. Resolve identity + normalize structure 4. Validate submission 5. Grade
 * all questions 6. Enrich from scoresheet (if provided) 7. Export results
 */
public class GradingPipeline {

	private static final Logger LOGGER = Logger.getLogger(GradingPipeline.class.getName());

	private final AppConfig config;
	private final ZipExtractor zipExtractor;
	private final IdentityResolver identityResolver;
	private final StructureNormalizer structureNormalizer;
	private final SubmissionValidator submissionValidator;
	private final CSVExporter csvExporter;
	private final ConsoleReporter consoleReporter;

	public GradingPipeline(AppConfig config) {
		this.config = config;
		this.zipExtractor = new ZipExtractor();
		this.identityResolver = new IdentityResolver();
		this.structureNormalizer = new StructureNormalizer(identityResolver);
		this.submissionValidator = new SubmissionValidator();
		this.csvExporter = new CSVExporter();
		this.consoleReporter = new ConsoleReporter();
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
				consoleReporter);

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

			// 3. Fill missing names using username-derived fallback
			for (StudentSubmission sub : submissions) {
				if ((sub.getName() == null || sub.getName().isEmpty()) && sub.getUsername() != null) {
					sub.setName(identityResolver.deriveNameFromUsername(sub.getUsername()));
				}
			}

			// 4. Enrich from scoresheet (official names + OrgDefinedId) if provided
			if (scoresheetPath != null && Files.isRegularFile(scoresheetPath)) {
				enrichFromScoresheet(scoresheetPath, submissions);
			}

			// 5. Print summary
			consoleReporter.printSummary(submissions, questionConfigs);

			// 6. Export scoresheet (if template provided)
			if (scoresheetPath != null && Files.isRegularFile(scoresheetPath)) {
				Path outputCsv = runOutputDir.resolve("IS442-ScoreSheet-Graded.csv");
				csvExporter.export(scoresheetPath, outputCsv, submissions);
				System.out.println("\nScoresheet exported to: " + outputCsv);
			}

			// 7. Export detailed report
			Path detailedCsv = runOutputDir.resolve("detailed-report.csv");
			csvExporter.exportDetailed(detailedCsv, submissions, questionConfigs);
			System.out.println("Detailed report exported to: " + detailedCsv);
			System.out.println("Full logs exported to: " + runOutputDir.resolve("logs"));
			System.out.println("Run log exported to: " + runOutputDir.resolve("logs").resolve("run.log"));

			// 8. Export instructor report
			Path instructorReport = runOutputDir.resolve("instructor-report.txt");
			new InstructorReportGenerator(config.getAssessmentName()).generate(submissions, questionConfigs,
					instructorReport);
			System.out.println("Instructor report exported to: " + instructorReport);

			return submissions;
		} finally {
			if (logCapture != null) {
				logCapture.close();
			}
		}
	}

	/**
	 * Parse the scoresheet CSV and enrich submissions with official names and
	 * OrgDefinedId. Scoresheet format: OrgDefinedId,Username,Last Name,First
	 * Name,Email,... Values may be prefixed with '#' (e.g. "#01400001",
	 * "#ping.lee.2023").
	 */
	private void enrichFromScoresheet(Path scoresheetPath, List<StudentSubmission> submissions) throws IOException {

		// Build lookup: username -> submission
		Map<String, StudentSubmission> subMap = new HashMap<>();
		for (StudentSubmission sub : submissions) {
			if (sub.getUsername() != null) {
				subMap.put(sub.getUsername().toLowerCase(), sub);
			}
		}

		List<String> lines = Files.readAllLines(scoresheetPath);
		for (int i = 1; i < lines.size(); i++) { // skip header
			String line = lines.get(i).trim();
			if (line.isEmpty()) {
				continue;
			}

			String[] parts = line.split(",", -1);
			if (parts.length < 5) {
				continue;
			}

			String orgId = parts[0].trim(); // e.g. "#01400001"
			String rawUsername = parts[1].trim(); // e.g. "#ping.lee.2023"
			String firstName = parts[3].trim(); // e.g. "PING LEE"

			// Strip '#' prefix from username
			String username = rawUsername.startsWith("#") ? rawUsername.substring(1) : rawUsername;

			StudentSubmission sub = subMap.get(username.toLowerCase());
			if (sub == null) {
				continue;
			}

			// Set OrgDefinedId (keep the '#' prefix as-is from the source)
			if (!orgId.isEmpty()) {
				sub.setOrgDefinedId(orgId);
			}

			// Official name from scoresheet takes priority (Title Case)
			if (!firstName.isEmpty()) {
				sub.setName(IdentityResolver.toTitleCase(firstName));
			}
		}
	}

	private Path createRunOutputDir(Path baseOutputDir) throws IOException {
		if (baseOutputDir == null) {
			return null;
		}
		String runId = java.time.LocalDateTime.now()
				.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
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

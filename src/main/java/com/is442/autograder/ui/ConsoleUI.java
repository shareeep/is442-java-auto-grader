package com.is442.autograder.ui;

import com.is442.autograder.GradingPipeline;
import com.is442.autograder.config.AppConfig;
import com.is442.autograder.generation.TestGenerationService;
import com.is442.autograder.generation.TesterFileWriter;
import com.is442.autograder.model.GeneratedTestCase;
import com.is442.autograder.model.GenerationResult;
import com.is442.autograder.model.QuestionConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Interactive console interface for the Auto Grading System.
 */
public class ConsoleUI {

	private static final String BANNER = """

			╔═══════════════════════════════════════════════════════╗
			║         IS442 Auto Grading System v1.0               ║
			╚═══════════════════════════════════════════════════════╝
			""";

	private final Scanner scanner;
	private final AppConfig config;

	public ConsoleUI(AppConfig config) {
		this.scanner = new Scanner(System.in);
		this.config = config;
	}

	/**
	 * Start the interactive console menu loop.
	 */
	public void start() {
		System.out.println(BANNER);

		boolean running = true;
		while (running) {
			printMenu();
			String choice = scanner.nextLine().trim();

			switch (choice) {
				case "1" -> gradeSubmissions();
				case "2" -> generateTestCasesFlow();
				case "3" -> viewConfiguration();
				case "4" -> {
					System.out.println("\nGoodbye!");
					running = false;
				}
				default -> System.out.println("\n  Invalid option. Please enter 1, 2, 3, or 4.\n");
			}
		}
	}

	private void printMenu() {
		System.out.println("  [1] Grade Submissions");
		System.out.println("  [2] Generate Test Cases");
		System.out.println("  [3] View Configuration");
		System.out.println("  [4] Exit");
		System.out.print("\n  Select option: ");
	}

	private static final String BACK_CMD = "back";

	/**
	 * Interactive grading flow with step-based navigation. Typing 'back' at any
	 * prompt returns to the previous step.
	 */
	private void gradeSubmissions() {
		System.out.println();
		System.out.println("  (Type 'back' at any prompt to go to the previous step)\n");

		Path submissionsDir = null;
		Path testerFilesDir = null;
		Path scoresheetPath = null;
		Path outputDir = null;

		int step = 1;
		while (step >= 1 && step <= 4) {
			switch (step) {
				case 1 -> {
					Path result = promptForPath("  Enter submissions folder path: ", true, true);
					if (result == null && lastInputWasBack) {
						return; // Can't go back from step 1, return to menu
					} else if (result != null) {
						submissionsDir = result;
						step++;
					}
					// else: invalid input, stay on same step
				}
				case 2 -> {
					Path result = promptForPath("  Enter tester files folder path: ", true, true);
					if (result == null && lastInputWasBack) {
						step--;
					} else if (result != null) {
						testerFilesDir = result;
						step++;
					}
				}
				case 3 -> {
					Path result = promptForPath("  Enter input scoresheet CSV template (or press Enter to skip): ",
							false, false);
					if (result == null && lastInputWasBack) {
						step--;
					} else {
						scoresheetPath = result;
						step++;
					}
				}
				case 4 -> {
					Path result = promptForPath("  Enter output directory path [./output]: ", false, true);
					if (result == null && lastInputWasBack) {
						step--;
					} else {
						outputDir = result != null ? result : Paths.get("output");
						step++;
					}
				}
			}
		}

		if (step < 1) {
			return; // user fully backed out
		}

		// Run grading
		try {
			System.out.println("\n  Processing...\n");
			GradingPipeline pipeline = new GradingPipeline(config);
			pipeline.run(submissionsDir, testerFilesDir, scoresheetPath, outputDir);
		} catch (IOException e) {
			System.err.println("\n  Error during grading: " + e.getMessage());
		}

		System.out.println();
	}

	/** Tracks whether the last promptForPath input was the 'back' command. */
	private boolean lastInputWasBack = false;

	/**
	 * Interactive flow for AI-assisted test case generation.
	 */
	private void generateTestCasesFlow() {
		System.out.println();
		System.out.println("  ── AI Test Case Generation ──────────────────────────────────");
		System.out.println("  (Type 'back' at any prompt to go to the previous step)\n");

		Path examPdf = null;
		Path testerFilesDir = null;
		List<QuestionConfig> selectedQuestions = null;
		int numCases = config.getAiDefaultCasesPerQuestion();
		Path outputDir = null;

		int step = 1;
		while (step >= 1 && step <= 5) {
			switch (step) {
				case 1 -> {
					// Detect a default PDF in project materials
					String defaultPdf = detectExamPdf();
					String prompt = defaultPdf != null ? "  Exam PDF path [" + defaultPdf + "]: " : "  Exam PDF path: ";
					Path result = promptForPath(prompt, defaultPdf == null, false);
					if (result == null && lastInputWasBack) {
						return;
					} else if (result == null && defaultPdf != null) {
						examPdf = Paths.get(defaultPdf);
						step++;
					} else if (result != null) {
						examPdf = result;
						step++;
					}
				}
				case 2 -> {
					Path result = promptForPath("  Tester files folder path: ", true, true);
					if (result == null && lastInputWasBack) {
						step--;
					} else if (result != null) {
						testerFilesDir = result;
						step++;
					}
				}
				case 3 -> {
					List<QuestionConfig> all = config.getQuestionConfigs();
					System.out.println("  Questions: [A] All  or  comma-list e.g. 1,2,3");
					System.out.print("  " + formatQuestionList(all) + "\n  Choice [A]: ");
					lastInputWasBack = false;
					String input = scanner.nextLine().trim();
					if (input.equalsIgnoreCase(BACK_CMD)) {
						step--;
					} else if (input.isEmpty() || input.equalsIgnoreCase("A")) {
						selectedQuestions = all;
						step++;
					} else {
						List<QuestionConfig> chosen = parseQuestionChoice(input, all);
						if (chosen.isEmpty()) {
							System.out.println("  ✖ Invalid selection.");
						} else {
							selectedQuestions = chosen;
							step++;
						}
					}
				}
				case 4 -> {
					System.out.print("  Cases per question [" + numCases + "]: ");
					lastInputWasBack = false;
					String input = scanner.nextLine().trim();
					if (input.equalsIgnoreCase(BACK_CMD)) {
						step--;
					} else if (input.isEmpty()) {
						step++;
					} else {
						try {
							int n = Integer.parseInt(input);
							if (n < 1 || n > 20) {
								System.out.println("  ✖ Enter a number between 1 and 20.");
							} else {
								numCases = n;
								step++;
							}
						} catch (NumberFormatException e) {
							System.out.println("  ✖ Enter a valid number.");
						}
					}
				}
				case 5 -> {
					Path result = promptForPath("  Output folder [./generated-testers]: ", false, false);
					if (result == null && lastInputWasBack) {
						step--;
					} else {
						outputDir = result != null ? result : Paths.get("generated-testers");
						step++;
					}
				}
			}
		}

		if (step < 1 || selectedQuestions == null) {
			return;
		}

		// Summary confirmation
		System.out.println("\n  ── Summary ──────────────────────────────────────────────────");
		System.out.println("  Exam PDF   : " + examPdf);
		System.out.println("  Testers    : " + testerFilesDir);
		System.out.println("  Questions  : " + formatSelected(selectedQuestions));
		System.out.println("  Cases each : " + numCases);
		System.out.println("  Output     : " + outputDir);
		System.out.print("\n  Proceed? [y/n]: ");
		String confirm = scanner.nextLine().trim();
		if (!confirm.equalsIgnoreCase("y")) {
			System.out.println("  Cancelled.\n");
			return;
		}

		// Run generation
		TestGenerationService service = new TestGenerationService(config);
		TesterFileWriter writer = new TesterFileWriter();

		for (QuestionConfig qc : selectedQuestions) {
			System.out.println("\n  Generating for " + qc.getQuestionId() + "...");
			Path existingTester = testerFilesDir.resolve(qc.getTesterClassName() + ".java");
			if (!Files.exists(existingTester)) {
				existingTester = null;
			}

			GenerationResult result;
			try {
				result = service.generateForQuestion(qc, examPdf, existingTester, numCases);
			} catch (Exception e) {
				System.out.println("  ✖ Error generating for " + qc.getQuestionId() + ": " + e.getMessage());
				continue;
			}

			// Review loop
			reviewAndSave(result, qc, testerFilesDir, outputDir, writer);
		}

		System.out.println("\n  Generation complete.\n");
	}

	private void reviewAndSave(GenerationResult result, QuestionConfig qc, Path testerFilesDir, Path outputDir,
			TesterFileWriter writer) {
		System.out.println("\n  ── Review: " + result.getQuestionId() + " ─────────────────────────────");
		System.out.println("  (Compile check skipped — requires student implementation files)");

		// Assign weights per case
		List<GeneratedTestCase> updatedCases = new ArrayList<>();
		List<GeneratedTestCase> originalCases = result.getCases();
		for (int i = 0; i < originalCases.size(); i++) {
			GeneratedTestCase tc = originalCases.get(i);
			System.out.printf("\n  Test %d: %s%n", i + 1, tc.description());
			System.out.printf("  Weight [%.1f]: ", tc.weight());
			String input = scanner.nextLine().trim();
			double weight = tc.weight();
			if (!input.isEmpty()) {
				try {
					weight = Double.parseDouble(input);
				} catch (NumberFormatException e) {
					System.out.println("  Invalid weight, using default " + tc.weight());
				}
			}
			updatedCases.add(new GeneratedTestCase(tc.description(), tc.inputArgs(), tc.expectedOutput(), weight));
		}

		double totalScore = updatedCases.stream().mapToDouble(GeneratedTestCase::weight).sum();
		System.out.printf("\n  New total max score: %.1f%n", totalScore);

		System.out.print("  Save " + qc.getTesterClassName() + "_generated.java? [y/n/skip]: ");
		String choice = scanner.nextLine().trim();
		if (choice.equalsIgnoreCase("y")) {
			try {
				String existingCode = null;
				Path existing = testerFilesDir.resolve(qc.getTesterClassName() + ".java");
				if (Files.exists(existing)) {
					existingCode = Files.readString(existing);
				}
				Path saved = writer.write(qc.getTesterClassName(), existingCode, result.getGeneratedCode(),
						updatedCases, testerFilesDir, outputDir);
				System.out.println("  Saved: " + saved);

				System.out.print("  Update config.properties max score for " + qc.getQuestionId() + "? [y/n]: ");
				String updateConfig = scanner.nextLine().trim();
				if (updateConfig.equalsIgnoreCase("y")) {
					Path configPath = Paths.get("src/main/resources/config.properties");
					writer.updateConfigMaxScore(configPath, qc.getQuestionId(), totalScore);
					System.out.println("  config.properties updated.");
				}
			} catch (IOException e) {
				System.out.println("  ✖ Failed to save: " + e.getMessage());
			}
		} else {
			System.out.println("  Skipped.");
		}
	}

	private String detectExamPdf() {
		// Check common locations
		String[] candidates = {"is442-project-materials/IS442-ExamSample.pdf", "is442-project-materials/IS442-Exam.pdf",
				"IS442-ExamSample.pdf"};
		for (String c : candidates) {
			if (Files.exists(Paths.get(c))) {
				return c;
			}
		}
		return null;
	}

	private String formatQuestionList(List<QuestionConfig> questions) {
		StringBuilder sb = new StringBuilder("Available: ");
		for (int i = 0; i < questions.size(); i++) {
			if (i > 0) {
				sb.append(", ");
			}
			sb.append("[").append(i + 1).append("] ").append(questions.get(i).getQuestionId());
		}
		return sb.toString();
	}

	private String formatSelected(List<QuestionConfig> questions) {
		StringBuilder sb = new StringBuilder();
		for (QuestionConfig q : questions) {
			if (sb.length() > 0) {
				sb.append(", ");
			}
			sb.append(q.getQuestionId());
		}
		return sb.toString();
	}

	private List<QuestionConfig> parseQuestionChoice(String input, List<QuestionConfig> all) {
		List<QuestionConfig> result = new ArrayList<>();
		String[] parts = input.split(",");
		for (String part : parts) {
			try {
				int idx = Integer.parseInt(part.trim()) - 1;
				if (idx >= 0 && idx < all.size()) {
					result.add(all.get(idx));
				}
			} catch (NumberFormatException e) {
				// Skip invalid entries
			}
		}
		return result;
	}

	/**
	 * Display current configuration settings.
	 */
	private void viewConfiguration() {
		System.out.println("\n  Current Configuration:");
		System.out.println("  ─────────────────────────────────────");
		System.out.println("  Timeout: " + config.getTimeoutSeconds() + " seconds");
		System.out.println("  Strict mode: " + config.isStrictMode());
		System.out.println();
		System.out.println("  Questions:");

		List<QuestionConfig> questions = config.getQuestionConfigs();
		for (QuestionConfig qc : questions) {
			System.out.printf("    %s → %s/%s (max %.0f)%n", qc.getQuestionId(), qc.getFolder(),
					qc.getTesterClassName(), qc.getMaxScore());
		}
		System.out.println();
	}

	/**
	 * Prompt for a file system path with validation. Supports 'back' command.
	 *
	 * @param prompt
	 *            message to display
	 * @param required
	 *            whether a non-empty input is required
	 * @param isDirectory
	 *            if true, validate as directory; if false, validate as file
	 * @return validated Path, or null if skipped/cancelled/back
	 */
	private Path promptForPath(String prompt, boolean required, boolean isDirectory) {
		lastInputWasBack = false;
		System.out.print(prompt);
		String input = scanner.nextLine().trim();

		// Handle 'back' command
		if (input.equalsIgnoreCase(BACK_CMD)) {
			lastInputWasBack = true;
			return null;
		}

		if (input.isEmpty()) {
			if (required) {
				System.out.println("  ✖ This field is required.");
			}
			return null;
		}

		// Basic input sanitization
		if (containsDangerousChars(input)) {
			System.out.println("  ✖ Path contains invalid characters.");
			return null;
		}

		Path path = Paths.get(input).normalize();

		if (required && isDirectory && !Files.isDirectory(path)) {
			System.out.println("  ✖ Directory does not exist: " + path);
			return null;
		}

		if (required && !isDirectory && !Files.isRegularFile(path)) {
			System.out.println("  ✖ File does not exist: " + path);
			return null;
		}

		return path;
	}

	private boolean containsDangerousChars(String input) {
		String[] dangerous = {";", "|", "&", "$", "`", "(", ")", "{", "}"};
		for (String c : dangerous) {
			if (input.contains(c)) {
				return true;
			}
		}
		return false;
	}
}

package com.is442.autograder.ui;

import com.is442.autograder.GradingPipeline;
import com.is442.autograder.config.AppConfig;
import com.is442.autograder.generation.ConfigInferenceService;
import com.is442.autograder.model.InferredConfig;
import com.is442.autograder.model.InferredQuestionConfig;
import com.is442.autograder.model.QuestionConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;

public class ConsoleUI {

	private static final String BANNER = """

			╔═══════════════════════════════════════════════════════╗
			║         IS442 Auto Grading System v1.0               ║
			╚═══════════════════════════════════════════════════════╝
			""";

	private final Scanner scanner;
	private final AppConfig config;
	private final ConfigInferenceService configInferenceService;

	public ConsoleUI(AppConfig config, ConfigInferenceService configInferenceService) {
		this.scanner = new Scanner(System.in);
		this.config = config;
		this.configInferenceService = configInferenceService;
	}

	public void start() {
		System.out.println(BANNER);

		if (!config.hasQuestionsConfigured()) {
			System.out.println("  ⚠  No question configuration found.");
			System.out.println("     Questions will be inferred from tester files and template folder.");
			System.out.println();
		}

		boolean running = true;
		while (running) {
			printMenu();
			String choice = scanner.nextLine().trim();

			switch (choice) {
				case "1" -> gradeSubmissions();
				case "2" -> {
					System.out.println("\nGoodbye!");
					running = false;
				}
				default -> System.out.println("\n  Invalid option. Please enter 1 or 2.\n");
			}
		}
	}

	private void printMenu() {
		System.out.println("  [1] Grade Submissions");
		System.out.println("  [2] Exit");
		System.out.print("\n  Select option: ");
	}

	private static final String BACK_CMD = "back";

	private void gradeSubmissions() {
		System.out.println();
		System.out.println("  (Type 'back' at any prompt to go to the previous step)\n");

		Path submissionsDir = null;
		Path testerFilesDir = null;
		Path scoresheetPath = null;
		Path outputDir = null;
		List<QuestionConfig> inferredConfigs = null;

		int step = 1;
		while (step >= 1 && step <= 5) {
			switch (step) {
				case 1 -> {
					Path result = promptForPath("  Enter submissions folder path: ", true, true);
					if (result == null && lastInputWasBack) {
						return;
					} else if (result != null) {
						submissionsDir = result;
						step++;
					}
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
					System.out.println("\n  ── Inferring Question Configuration ──────────────────────────");
					try {
						InferredConfig inferred = configInferenceService.inferConfig(null, config.getTemplateFolder(),
								testerFilesDir.toString());

						if (inferred.getQuestions().isEmpty()) {
							System.out.println("  ✖ No questions detected. Check tester files and template folder.");
							step = 2;
						} else {
							inferredConfigs = inferred.getQuestions().stream()
									.map(InferredQuestionConfig::toQuestionConfig).filter(qc -> qc.getMaxScore() > 0)
									.map(qc -> {
										if (qc.getFolder() == null || qc.getFolder().isEmpty()) {
											String parentFolder = inferFolderFromQuestionId(qc.getQuestionId());
											return new QuestionConfig(qc.getQuestionId(), parentFolder,
													qc.getTesterClassName(), qc.getMaxScore(), qc.getDependencyFolder(),
													qc.getDependencyFiles());
										}
										return qc;
									}).toList();
							displayInferredQuestions(inferredConfigs);
							System.out.print("\n  Proceed with these questions? [y/n]: ");
							String confirm = scanner.nextLine().trim();
							if (confirm.equalsIgnoreCase("y")) {
								step++;
							} else {
								System.out.println("  Cancelled.\n");
								return;
							}
						}
					} catch (Exception e) {
						System.err.println("  ✖ Inference failed: " + e.getMessage());
						step = 2;
					}
				}
				case 4 -> {
					Path result = promptForPath("  Enter input scoresheet CSV template (or press Enter to skip): ",
							false, false);
					if (result == null && lastInputWasBack) {
						step--;
					} else {
						scoresheetPath = result;
						step++;
					}
				}
				case 5 -> {
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

		if (step < 1 || inferredConfigs == null) {
			return;
		}

		try {
			System.out.println("\n  Processing...\n");
			GradingPipeline pipeline = new GradingPipeline(config);
			pipeline.setInferredQuestionConfigs(inferredConfigs);
			pipeline.run(submissionsDir, testerFilesDir, scoresheetPath, outputDir);
		} catch (IOException e) {
			System.err.println("\n  Error during grading: " + e.getMessage());
		}

		System.out.println();
	}

	private boolean lastInputWasBack = false;

	private void displayInferredQuestions(List<QuestionConfig> questions) {
		System.out.println("  Detected questions:");
		for (QuestionConfig q : questions) {
			String info = q.getTesterClassName() != null ? q.getTesterClassName() : q.getFolder();
			System.out.printf("    %s → %s (max %.0f)%n", q.getQuestionId(), info, q.getMaxScore());
		}
	}

	private String inferFolderFromQuestionId(String questionId) {
		if (questionId == null || questionId.length() < 2) {
			return questionId;
		}
		if (questionId.matches("Q\\d+[a-z]")) {
			return questionId.replaceFirst("([a-z])$", "");
		}
		return questionId;
	}

	private Path promptForPath(String prompt, boolean required, boolean isDirectory) {
		lastInputWasBack = false;
		System.out.print(prompt);
		String input = scanner.nextLine().trim();

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

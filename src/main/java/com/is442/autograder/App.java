package com.is442.autograder;

import com.is442.autograder.config.AppConfig;
import com.is442.autograder.config.EnvLoader;
import com.is442.autograder.generation.ConfigInferenceService;
import com.is442.autograder.model.InferredConfig;
import com.is442.autograder.model.QuestionConfig;
import com.is442.autograder.ui.ConsoleUI;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the IS442 Auto Grading System.
 *
 * Supports three modes: 1. Web UI mode (no args or --web) - launches Spring
 * Boot 2. Interactive mode (--cli) - launches console menu 3. CLI mode (with
 * args) - runs grading directly
 *
 * CLI Usage: java -jar autograder.jar --submissions ./student-submission \
 * --testers ./Tester-Files \ --scoresheet ./IS442-ScoreSheet.csv \ --output
 * ./output
 */
@SpringBootApplication
public class App {

	public static void main(String[] args) {
		EnvLoader.load();

		if (hasFlag(args, "--web")) {
			// Web UI mode — launch Spring Boot
			com.is442.autograder.web.WebApplication.main(args);
			return;
		}

		try {
			AppConfig config = new AppConfig();

			if (args.length == 0 || (args.length == 1 && args[0].equals("--web"))) {
				// Launch Spring Boot web server
				SpringApplication.run(App.class, args);
			} else if (args.length == 1 && args[0].equals("--cli")) {
				// Interactive mode
				ConfigInferenceService configInferenceService = new ConfigInferenceService();
				ConsoleUI ui = new ConsoleUI(config, configInferenceService);
				ui.start();
			} else {
				// CLI grading mode
				runCli(config, args);
			}
		} catch (IOException e) {
			System.err.println("Failed to load configuration: " + e.getMessage());
			System.exit(1);
		}
	}

	/**
	 * Parse CLI arguments and run the grading pipeline directly.
	 */
	private static void runCli(AppConfig config, String[] args) {
		Path submissionsDir = null;
		Path testersDir = null;
		Path scoresheetPath = null;
		Path outputDir = Paths.get("output");

		for (int i = 0; i < args.length - 1; i++) {
			switch (args[i]) {
				case "--submissions", "-s" -> submissionsDir = Paths.get(args[++i]);
				case "--testers", "-t" -> testersDir = Paths.get(args[++i]);
				case "--scoresheet", "-c" -> scoresheetPath = Paths.get(args[++i]);
				case "--output", "-o" -> outputDir = Paths.get(args[++i]);
				case "--help", "-h" -> {
					printUsage();
					return;
				}
			}
		}

		if (submissionsDir == null || testersDir == null) {
			System.err.println("Error: --submissions and --testers are required.\n");
			printUsage();
			System.exit(1);
		}

		try {
			ConfigInferenceService inferenceService = new ConfigInferenceService();
			InferredConfig inferred = inferenceService.inferConfig(null, config.getTemplateFolder(),
					testersDir.toString());

			if (inferred.getQuestions().isEmpty()) {
				System.err.println(
						"Error: No questions detected from testers dir. Check tester files and template folder.");
				System.exit(1);
			}

			List<QuestionConfig> inferredConfigs = inferenceService.toQuestionConfigs(inferred);

			System.out.println("Inferred " + inferredConfigs.size() + " question(s): "
					+ inferredConfigs.stream().map(QuestionConfig::getQuestionId).toList());

			GradingPipeline pipeline = new GradingPipeline(config);
			pipeline.setInferredQuestionConfigs(inferredConfigs);
			pipeline.run(submissionsDir, testersDir, scoresheetPath, outputDir);
		} catch (IOException e) {
			System.err.println("Error during grading: " + e.getMessage());
			System.exit(1);
		}
	}

	private static boolean hasFlag(String[] args, String flag) {
		for (String arg : args) {
			if (arg.equals(flag)) {
				return true;
			}
		}
		return false;
	}

	private static void printUsage() {
		System.out.println("IS442 Auto Grading System");
		System.out.println();
		System.out.println("Usage: java -jar autograder.jar [options]");
		System.out.println();
		System.out.println("Grading options:");
		System.out.println("  --submissions, -s <dir>    Directory containing student ZIP files (required)");
		System.out.println("  --testers, -t <dir>        Directory containing tester .java files (required)");
		System.out.println("  --scoresheet, -c <file>    Path to template CSV scoresheet (optional)");
		System.out.println("  --output, -o <dir>         Output directory (default: ./output)");
		System.out.println();
		System.out.println("  --help, -h                 Show this help message");
		System.out.println();
		System.out.println("Web UI:");
		System.out.println("  --web                      Launch web UI (Spring Boot) on port 8080");
		System.out.println();
		System.out.println("If no arguments are provided, interactive mode is launched.");
	}
}

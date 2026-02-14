package com.is442.autograder;

import com.is442.autograder.config.AppConfig;
import com.is442.autograder.ui.ConsoleUI;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Entry point for the IS442 Auto Grading System.
 *
 * Supports two modes: 1. Interactive mode (no args) - launches console menu 2.
 * CLI mode (with args) - runs grading directly
 *
 * CLI Usage: java -jar autograder.jar --submissions ./student-submission \
 * --testers ./Tester-Files \ --scoresheet ./IS442-ScoreSheet.csv \ --output
 * ./output
 */
public class App {

	public static void main(String[] args) {
		try {
			AppConfig config = new AppConfig();

			if (args.length == 0) {
				// Interactive mode
				ConsoleUI ui = new ConsoleUI(config);
				ui.start();
			} else {
				// CLI mode
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
			GradingPipeline pipeline = new GradingPipeline(config);
			pipeline.run(submissionsDir, testersDir, scoresheetPath, outputDir);
		} catch (IOException e) {
			System.err.println("Error during grading: " + e.getMessage());
			System.exit(1);
		}
	}

	private static void printUsage() {
		System.out.println("IS442 Auto Grading System");
		System.out.println();
		System.out.println("Usage: java -jar autograder.jar [options]");
		System.out.println();
		System.out.println("Options:");
		System.out.println("  --submissions, -s <dir>    Directory containing student ZIP files (required)");
		System.out.println("  --testers, -t <dir>        Directory containing tester .java files (required)");
		System.out.println("  --scoresheet, -c <file>    Path to template CSV scoresheet (optional)");
		System.out.println("  --output, -o <dir>         Output directory (default: ./output)");
		System.out.println("  --help, -h                 Show this help message");
		System.out.println();
		System.out.println("If no arguments are provided, interactive mode is launched.");
	}
}

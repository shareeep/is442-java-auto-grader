package com.is442.autograder.ui;

import com.is442.autograder.GradingPipeline;
import com.is442.autograder.config.AppConfig;
import com.is442.autograder.model.QuestionConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
                case "2" -> viewConfiguration();
                case "3" -> {
                    System.out.println("\nGoodbye!");
                    running = false;
                }
                default -> System.out.println("\n  Invalid option. Please enter 1, 2, or 3.\n");
            }
        }
    }

    private void printMenu() {
        System.out.println("  [1] Grade Submissions");
        System.out.println("  [2] View Configuration");
        System.out.println("  [3] Exit");
        System.out.print("\n  Select option: ");
    }

    /**
     * Interactive grading flow: prompt for paths and run pipeline.
     */
    private void gradeSubmissions() {
        System.out.println();

        // Get submissions folder
        Path submissionsDir = promptForPath(
                "  Enter submissions folder path: ",
                true, true);
        if (submissionsDir == null)
            return;

        // Get tester files folder
        Path testerFilesDir = promptForPath(
                "  Enter tester files folder path: ",
                true, true);
        if (testerFilesDir == null)
            return;

        // Get scoresheet CSV (optional)
        Path scoresheetPath = promptForPath(
                "  Enter scoresheet CSV path (or press Enter to skip): ",
                false, false);

        // Get output directory
        Path outputDir = promptForPath(
                "  Enter output directory path [./output]: ",
                false, true);
        if (outputDir == null) {
            outputDir = Paths.get("output");
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
            System.out.printf("    %s → %s/%s (max %.0f)%n",
                    qc.getQuestionId(), qc.getFolder(),
                    qc.getTesterClassName(), qc.getMaxScore());
        }
        System.out.println();
    }

    /**
     * Prompt for a file system path with validation.
     *
     * @param prompt      message to display
     * @param required    whether a non-empty input is required
     * @param isDirectory if true, validate as directory; if false, validate as file
     * @return validated Path, or null if skipped/cancelled
     */
    private Path promptForPath(String prompt, boolean required, boolean isDirectory) {
        System.out.print(prompt);
        String input = scanner.nextLine().trim();

        if (input.isEmpty()) {
            if (required) {
                System.out.println("  ✖ This field is required.\n");
                return null;
            }
            return null;
        }

        // Basic input sanitization
        if (containsDangerousChars(input)) {
            System.out.println("  ✖ Path contains invalid characters.\n");
            return null;
        }

        Path path = Paths.get(input).normalize();

        if (required && isDirectory && !Files.isDirectory(path)) {
            System.out.println("  ✖ Directory does not exist: " + path + "\n");
            return null;
        }

        if (required && !isDirectory && !Files.isRegularFile(path)) {
            System.out.println("  ✖ File does not exist: " + path + "\n");
            return null;
        }

        return path;
    }

    private boolean containsDangerousChars(String input) {
        String[] dangerous = { ";", "|", "&", "$", "`", "(", ")", "{", "}" };
        for (String c : dangerous) {
            if (input.contains(c))
                return true;
        }
        return false;
    }
}

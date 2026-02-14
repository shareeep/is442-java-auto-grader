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

    private static final String BACK_CMD = "back";

    /**
     * Interactive grading flow with step-based navigation.
     * Typing 'back' at any prompt returns to the previous step.
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
                    Path result = promptForPath(
                            "  Enter submissions folder path: ",
                            true, true);
                    if (result == null && lastInputWasBack) {
                        return; // Can't go back from step 1, return to menu
                    } else if (result != null) {
                        submissionsDir = result;
                        step++;
                    }
                    // else: invalid input, stay on same step
                }
                case 2 -> {
                    Path result = promptForPath(
                            "  Enter tester files folder path: ",
                            true, true);
                    if (result == null && lastInputWasBack) {
                        step--;
                    } else if (result != null) {
                        testerFilesDir = result;
                        step++;
                    }
                }
                case 3 -> {
                    Path result = promptForPath(
                            "  Enter input scoresheet CSV template (or press Enter to skip): ",
                            false, false);
                    if (result == null && lastInputWasBack) {
                        step--;
                    } else {
                        scoresheetPath = result;
                        step++;
                    }
                }
                case 4 -> {
                    Path result = promptForPath(
                            "  Enter output directory path [./output]: ",
                            false, true);
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
     * Prompt for a file system path with validation. Supports 'back' command.
     *
     * @param prompt      message to display
     * @param required    whether a non-empty input is required
     * @param isDirectory if true, validate as directory; if false, validate as file
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
        String[] dangerous = { ";", "|", "&", "$", "`", "(", ")", "{", "}" };
        for (String c : dangerous) {
            if (input.contains(c)) {
                return true;
            }
        }
        return false;
    }
}

package com.is442.autograder;

import com.is442.autograder.config.AppConfig;
import com.is442.autograder.config.EnvLoader;
import com.is442.autograder.generation.LangChainService;
import com.is442.autograder.generation.PdfParser;
import com.is442.autograder.generation.TestGenerationService;
import com.is442.autograder.generation.TesterFileWriter;
import com.is442.autograder.model.GeneratedTestCase;
import com.is442.autograder.model.GenerationResult;
import com.is442.autograder.model.QuestionConfig;
import com.is442.autograder.ui.ConsoleUI;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.service.AiServices;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
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
				ConsoleUI ui = new ConsoleUI(config);
				ui.start();
			} else if (hasFlag(args, "--generate-tests")) {
				// CLI generation mode
				runGenerateCli(config, args);
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
	 * Create a LangChainService instance for CLI use (outside Spring context). Uses
	 * LangChain4j's AiServices factory with OpenRouter-compatible model.
	 */
	public static LangChainService createLangChainService(AppConfig config) {
		String apiKey = EnvLoader.get("OPENROUTER_API_KEY");
		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalStateException("OPENROUTER_API_KEY is not set (env var or .env file).");
		}

		OpenAiChatModel model = OpenAiChatModel.builder().apiKey(apiKey)
				.modelName(config.getAiModel() != null ? config.getAiModel() : "minimax/minimax-m2.7")
				.baseUrl("https://openrouter.ai/api/v1").maxTokens(4096).timeout(Duration.ofSeconds(180)).maxRetries(2)
				.defaultRequestParameters(OpenAiChatRequestParameters.builder().reasoningEffort("low").build())
				.logRequests(true).logResponses(true).build();

		return AiServices.create(LangChainService.class, model);
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

	/**
	 * Non-interactive CLI mode for test case generation. Generates with equal
	 * weights and auto-saves.
	 */
	private static void runGenerateCli(AppConfig config, String[] args) {
		Path examPdf = null;
		Path testersDir = null;
		Path outputDir = Paths.get("generated-testers");
		int numCases = config.getAiDefaultCasesPerQuestion();

		for (int i = 0; i < args.length - 1; i++) {
			switch (args[i]) {
				case "--exam" -> examPdf = Paths.get(args[++i]);
				case "--testers", "-t" -> testersDir = Paths.get(args[++i]);
				case "--num-cases" -> numCases = Integer.parseInt(args[++i]);
				case "--output", "-o" -> outputDir = Paths.get(args[++i]);
			}
		}

		if (examPdf == null) {
			System.err.println("Error: --exam <path> is required for --generate-tests.");
			System.exit(1);
		}

		if (EnvLoader.get("OPENROUTER_API_KEY") == null) {
			System.err.println("Error: OPENROUTER_API_KEY is not set (env var or .env file).");
			System.exit(1);
		}

		if (testersDir == null) {
			testersDir = Paths.get("is442-project-materials/Tester-Files");
		}

		LangChainService aiService = createLangChainService(config);
		TesterFileWriter writer = new TesterFileWriter();
		PdfParser pdfParser = new PdfParser(config.getDoclingServeUrl());
		TestGenerationService service = new TestGenerationService(aiService, writer);
		List<QuestionConfig> questions = config.getQuestionConfigs();

		for (QuestionConfig qc : questions) {
			System.out.println("Generating for " + qc.getQuestionId() + "...");
			Path existingTester = testersDir.resolve(qc.getTesterClassName() + ".java");
			if (!Files.exists(existingTester)) {
				existingTester = null;
			}

			try {
				// CLI mode: parse PDF on-the-fly (no DB cache)
				String examContext = pdfParser.extractQuestionSection(examPdf, qc.getQuestionId());
				GenerationResult result = service.generateForQuestion(qc, examContext, existingTester, numCases, null);

				// Auto-save with equal weights (1.0 each)
				List<GeneratedTestCase> cases = result.getCases();
				String existingCode = existingTester != null ? Files.readString(existingTester) : null;
				Path saved = writer.write(qc.getTesterClassName(), existingCode, result.getGeneratedCode(), cases,
						testersDir, outputDir);

				System.out
						.println("  Saved: " + saved + " (compile: " + (result.isCompiledOk() ? "OK" : "FAILED") + ")");
				if (!result.isCompiledOk()) {
					System.err.println("  Compile errors:\n" + result.getCompileErrors());
				}
			} catch (Exception e) {
				System.err.println("  Error for " + qc.getQuestionId() + ": " + e.getMessage());
			}
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
		System.out.println("Generation options:");
		System.out.println("  --generate-tests           Run AI test case generation (non-interactive)");
		System.out.println("  --exam <path>              Path to exam PDF (required with --generate-tests)");
		System.out.println(
				"  --testers, -t <dir>        Tester files directory (default: is442-project-materials/Tester-Files)");
		System.out.println("  --num-cases <n>            Cases per question (default from config)");
		System.out.println("  --output, -o <dir>         Output directory (default: ./generated-testers)");
		System.out.println("  API key read from OPENROUTER_API_KEY environment variable (required).");
		System.out.println();
		System.out.println("  --help, -h                 Show this help message");
		System.out.println();
		System.out.println("Web UI:");
		System.out.println("  --web                      Launch web UI (Spring Boot) on port 8080");
		System.out.println();
		System.out.println("If no arguments are provided, interactive mode is launched.");
	}
}

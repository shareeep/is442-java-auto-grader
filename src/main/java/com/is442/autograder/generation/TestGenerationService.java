package com.is442.autograder.generation;

import com.is442.autograder.model.GeneratedTestCase;
import com.is442.autograder.model.GenerationResult;
import com.is442.autograder.model.QuestionConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UI-agnostic orchestrator for AI-assisted test case generation. Coordinates
 * PDF parsing, Claude API calls, and optional compile validation.
 */
public class TestGenerationService {

	private final ExamPdfParser pdfParser;
	private final ClaudeApiClient claudeClient;

	public TestGenerationService(com.is442.autograder.config.AppConfig config) {
		this.pdfParser = new ExamPdfParser();
		this.claudeClient = new ClaudeApiClient(config);
	}

	/**
	 * Generate test cases for a single question.
	 *
	 * @param question
	 *            question configuration
	 * @param examPdf
	 *            path to the exam PDF
	 * @param existingTesterFile
	 *            path to existing tester file (null = from scratch)
	 * @param numCases
	 *            number of new test cases to generate
	 * @return generation result containing code and compile status
	 */
	public GenerationResult generateForQuestion(QuestionConfig question, Path examPdf, Path existingTesterFile,
			int numCases) throws IOException, InterruptedException {
		return generateForQuestion(question, examPdf, existingTesterFile, numCases, null);
	}

	/**
	 * Generate test cases for a single question with optional template directory
	 * containing original student code and data files.
	 *
	 * @param question
	 *            question configuration
	 * @param examPdf
	 *            path to the exam PDF
	 * @param existingTesterFile
	 *            path to existing tester file (null = from scratch)
	 * @param numCases
	 *            number of new test cases to generate
	 * @param templateDir
	 *            path to template directory with original student code (e.g.
	 *            RenameToYourUsername), or null
	 * @return generation result containing code and compile status
	 */
	public GenerationResult generateForQuestion(QuestionConfig question, Path examPdf, Path existingTesterFile,
			int numCases, Path templateDir) throws IOException, InterruptedException {

		// 1. Extract exam context
		String examContext = pdfParser.extractQuestionSection(examPdf, question.getQuestionId());

		// 2. Read existing tester if provided
		String existingCode = null;
		if (existingTesterFile != null && Files.exists(existingTesterFile)) {
			existingCode = Files.readString(existingTesterFile);
		}

		// 3. Build additional context from template dir and data files
		String additionalContext = buildAdditionalContext(question, existingCode, existingTesterFile, templateDir);

		// 4. Call LLM via OpenRouter
		String generatedCode;
		try {
			generatedCode = claudeClient.generateTesterCode(question.getQuestionId(), question.getTesterClassName(),
					examContext, existingCode, numCases, additionalContext);
		} catch (IOException e) {
			return new GenerationResult(question.getQuestionId(), List.of(), false,
					"API call failed: " + e.getMessage(), "");
		}

		// 5. Build placeholder cases (weights default to 1.0; UI will update them)
		List<GeneratedTestCase> cases = buildPlaceholderCases(generatedCode, numCases);

		return new GenerationResult(question.getQuestionId(), cases, true, "", generatedCode);
	}

	/**
	 * Build additional context for the AI prompt by reading: 1. Original student
	 * code from the template directory 2. Data files (.txt) referenced in the
	 * tester code 3. Data files from the question's subfolder in the template dir
	 */
	private String buildAdditionalContext(QuestionConfig question, String existingCode, Path existingTesterFile,
			Path templateDir) {
		StringBuilder ctx = new StringBuilder();

		// Read original student code from template dir
		if (templateDir != null) {
			Path questionFolder = templateDir.resolve(question.getFolder());
			if (Files.isDirectory(questionFolder)) {
				ctx.append("Original student code (the method students must implement):\n\n");
				try (var files = Files.list(questionFolder)) {
					for (Path f : files.toList()) {
						String name = f.getFileName().toString();
						if (name.endsWith(".java")) {
							ctx.append("--- ").append(name).append(" ---\n");
							ctx.append(Files.readString(f)).append("\n\n");
						}
					}
				} catch (IOException e) {
					// skip
				}

				// Read .txt data files from the question folder
				try (var files = Files.list(questionFolder)) {
					List<Path> dataFiles = files.filter(f -> f.getFileName().toString().endsWith(".txt")).toList();
					if (!dataFiles.isEmpty()) {
						ctx.append("Data files available in the question folder:\n\n");
						for (Path f : dataFiles) {
							ctx.append("File: \"").append(f.getFileName()).append("\" — contents:\n");
							ctx.append(Files.readString(f)).append("\n\n");
						}
					}
				} catch (IOException e) {
					// skip
				}
			}
		}

		// Extract referenced .txt filenames from the existing tester code
		if (existingCode != null) {
			Set<String> filenames = new LinkedHashSet<>();
			Pattern p = Pattern.compile("\"([^\"]+\\.txt)\"");
			Matcher m = p.matcher(existingCode);
			while (m.find()) {
				filenames.add(m.group(1));
			}

			if (!filenames.isEmpty()) {
				ctx.append("IMPORTANT — Data files referenced by the existing tester:\n");
				ctx.append("You may ONLY use these filenames. Do NOT invent new filenames.\n\n");

				// Try to find data files from multiple locations
				List<Path> searchDirs = new ArrayList<>();
				if (existingTesterFile != null && existingTesterFile.getParent() != null) {
					searchDirs.add(existingTesterFile.getParent());
				}
				if (templateDir != null) {
					searchDirs.add(templateDir.resolve(question.getFolder()));
				}

				for (String filename : filenames) {
					ctx.append("File: \"").append(filename).append("\"");
					boolean found = false;
					for (Path dir : searchDirs) {
						Path dataFile = dir.resolve(filename);
						if (Files.exists(dataFile)) {
							try {
								ctx.append(" — contents:\n").append(Files.readString(dataFile)).append("\n");
								found = true;
								break;
							} catch (IOException e) {
								// continue searching
							}
						}
					}
					if (!found) {
						ctx.append(" — file does NOT exist (used for error/exception testing)\n");
					}
					ctx.append("\n");
				}
			}
		}

		return ctx.toString();
	}

	private List<GeneratedTestCase> buildPlaceholderCases(String generatedCode, int numCases) {
		List<GeneratedTestCase> cases = new ArrayList<>();
		// Count WEIGHT_N placeholders to determine actual number of cases generated
		int count = 0;
		int idx = 0;
		while ((idx = generatedCode.indexOf("WEIGHT_", idx)) >= 0) {
			count++;
			idx += 7;
		}
		int actual = Math.max(count, numCases);
		for (int i = 1; i <= actual; i++) {
			cases.add(new GeneratedTestCase("Generated test " + i, "", "", 1.0));
		}
		return cases;
	}

}

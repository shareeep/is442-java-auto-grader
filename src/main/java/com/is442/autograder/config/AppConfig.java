package com.is442.autograder.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.is442.autograder.model.InferredConfig;
import com.is442.autograder.model.InferredQuestionConfig;
import com.is442.autograder.model.QuestionConfig;

/**
 * Loads and exposes externalized configuration from config.properties.
 *
 * Configuration priority: 1. ./config.properties (external, user-managed) —
 * loaded first 2. config.properties on classpath (bundled defaults) — fallback
 * only
 */
public class AppConfig {

	private static final Path EXTERNAL_CONFIG_PATH = Paths.get("config.properties");
	private static final String CLASSPATH_CONFIG = "config.properties";
	private static final String DOCLING_SERVE_URL_ENV = "DOCLING_SERVE_URL";
	private static final String COMMA = ",";
	private static final String SEMICOLON = ";";

	private static final String QUESTIONS_LIST = "questions.list";
	private static final String QUESTIONS_FOLDERS = "questions.folders";
	private static final String QUESTIONS_TESTERS = "questions.testers";
	private static final String QUESTIONS_MAX_SCORES = "questions.max.scores";
	private static final String QUESTIONS_DEPENDENCY_FOLDERS = "questions.dependency.folders";
	private static final String QUESTIONS_DEPENDENCY_FILES = "questions.dependency.files";

	private final Properties properties;

	public AppConfig() throws IOException {
		this.properties = new Properties();
		loadConfig();
	}

	private void loadConfig() throws IOException {
		properties.clear();

		if (Files.exists(EXTERNAL_CONFIG_PATH)) {
			try (InputStream is = Files.newInputStream(EXTERNAL_CONFIG_PATH)) {
				properties.load(is);
			}
		} else {
			try (InputStream is = getClass().getClassLoader().getResourceAsStream(CLASSPATH_CONFIG)) {
				if (is == null) {
					throw new IOException(CLASSPATH_CONFIG + " not found on classpath");
				}
				properties.load(is);
			}
		}
	}

	public void reload() throws IOException {
		loadConfig();
	}

	public boolean hasQuestionsConfigured() {
		String list = properties.getProperty("questions.list", "");
		return !list.isEmpty();
	}

	/** Execution timeout in seconds (default 10). */
	public int getTimeoutSeconds() {
		return Integer.parseInt(properties.getProperty("execution.timeout.seconds", "10"));
	}

	/** Whether strict validation mode is enabled. */
	public boolean isStrictMode() {
		return Boolean.parseBoolean(properties.getProperty("validation.strict.mode", "false"));
	}

	/** Whether extra files (beyond required) are allowed. */
	public boolean allowExtraFiles() {
		return Boolean.parseBoolean(properties.getProperty("validation.allow.extra.files", "true"));
	}

	/** Whether to include anomalies in output reports. */
	public boolean includeAnomalies() {
		return Boolean.parseBoolean(properties.getProperty("output.include.anomalies", "true"));
	}

	/** The assessment name shown in the instructor report header. */
	public String getAssessmentName() {
		return properties.getProperty("assessment.name", "Assessment");
	}

	/** The URL of the local Docling Serve container for PDF parsing. */
	public String getDoclingServeUrl() {
		String envUrl = System.getenv(DOCLING_SERVE_URL_ENV);
		if (envUrl != null && !envUrl.isBlank()) {
			return envUrl;
		}
		return properties.getProperty("docling.serve.url", "http://localhost:5001");
	}

	/**
	 * Parse comma-separated question configuration lists into QuestionConfig
	 * objects. Config format:
	 *
	 * <pre>
	 * questions.list=Q1a,Q1b,Q2a,Q2b,Q3
	 * questions.folders=Q1,Q1,Q2,Q2,Q3
	 * questions.testers=Q1aTester,Q1bTester,Q2aTester,Q2bTester,Q3Tester
	 * questions.max.scores=3,3,5,5,4
	 * questions.dependency.folders=,,Q2,Q2,Q3
	 * questions.dependency.files=,,DataException.class;persons.txt,DataException.class;persons.txt,Shape.class
	 * </pre>
	 *
	 * The {@code dependency.folders} and {@code dependency.files} arrays are
	 * optional; empty entries mean "no dependencies for that question". Files
	 * within a single entry are delimited by {@code ;}.
	 */
	public List<QuestionConfig> getQuestionConfigs() {
		String[] ids = getArray(QUESTIONS_LIST);
		String[] folders = getArray(QUESTIONS_FOLDERS);
		String[] testers = getArray(QUESTIONS_TESTERS);
		String[] maxScores = getArray(QUESTIONS_MAX_SCORES);
		String[] depFolders = getOptionalArray(QUESTIONS_DEPENDENCY_FOLDERS, ids.length);
		String[] depFiles = getOptionalArray(QUESTIONS_DEPENDENCY_FILES, ids.length);

		if (ids.length != folders.length || ids.length != testers.length || ids.length != maxScores.length) {
			throw new IllegalStateException("Question configuration arrays must all be the same length. " + "Got: list="
					+ ids.length + ", folders=" + folders.length + ", testers=" + testers.length + ", maxScores="
					+ maxScores.length);
		}

		List<QuestionConfig> configs = new ArrayList<>();
		for (int i = 0; i < ids.length; i++) {
			String depFolder = toNullableValue(depFolders[i]);
			List<String> depFileList = toDependencyFileList(depFiles[i]);
			configs.add(new QuestionConfig(ids[i].trim(), folders[i].trim(), testers[i].trim(),
					Double.parseDouble(maxScores[i].trim()), depFolder, depFileList));
		}
		return configs;
	}

	/**
	 * The template folder name within the project-materials directory that holds
	 * pre-compiled dependency class files (default:
	 * {@code "RenameToYourUsername"}).
	 */
	public String getTemplateFolder() {
		return properties.getProperty("assessment.template.folder", "RenameToYourUsername");
	}

	/** AI model name (default: claude-sonnet-4-6). */
	public String getAiModel() {
		return properties.getProperty("ai.model", "claude-sonnet-4-6");
	}

	/**
	 * Text-only AI model name used when no images are present (default:
	 * minimax/minimax-m2.7).
	 */
	public String getAiTextModel() {
		return properties.getProperty("ai.text-model", "minimax/minimax-m2.7");
	}

	/** Max tokens for AI generation (default: 4096). */
	public int getAiMaxTokens() {
		return Integer.parseInt(properties.getProperty("ai.max.tokens", "4096"));
	}

	/** Default number of test cases to generate per question (default: 3). */
	public int getAiDefaultCasesPerQuestion() {
		return Integer.parseInt(properties.getProperty("ai.default.cases.per.question", "3"));
	}

	/**
	 * Persist an InferredConfig to config.properties on disk and update the
	 * in-memory properties so that subsequent calls to
	 * {@link #getQuestionConfigs()} return the new values without a server restart.
	 *
	 * Writes to ./config.properties (external file in working directory). If the
	 * file doesn't exist, it bootstraps from the classpath defaults.
	 */
	public void writeQuestionConfigs(InferredConfig inferredConfig) throws IOException {
		List<String> ids = new ArrayList<>();
		List<String> folders = new ArrayList<>();
		List<String> testers = new ArrayList<>();
		List<String> scores = new ArrayList<>();
		List<String> depFolders = new ArrayList<>();
		List<String> depFiles = new ArrayList<>();

		for (InferredQuestionConfig q : inferredConfig.getQuestions()) {
			ids.add(q.getQuestionId());
			folders.add(q.getFolder() != null ? q.getFolder() : "");
			testers.add(q.getTester() != null ? q.getTester() : "");
			scores.add(String.valueOf(q.getMaxScore()));
			depFolders.add(q.getDependencyFolder() != null ? q.getDependencyFolder() : "");
			depFiles.add(q.getDependencyFiles() != null && !q.getDependencyFiles().isEmpty()
					? String.join(";", q.getDependencyFiles())
					: "");
		}

		String idsStr = String.join(COMMA, ids);
		String foldersStr = String.join(COMMA, folders);
		String testersStr = String.join(COMMA, testers);
		String scoresStr = String.join(COMMA, scores);
		String depFoldersStr = String.join(COMMA, depFolders);
		String depFilesStr = String.join(COMMA, depFiles);

		// Update in-memory properties
		properties.setProperty(QUESTIONS_LIST, idsStr);
		properties.setProperty(QUESTIONS_FOLDERS, foldersStr);
		properties.setProperty(QUESTIONS_TESTERS, testersStr);
		properties.setProperty(QUESTIONS_MAX_SCORES, scoresStr);
		properties.setProperty(QUESTIONS_DEPENDENCY_FOLDERS, depFoldersStr);
		properties.setProperty(QUESTIONS_DEPENDENCY_FILES, depFilesStr);

		// Persist to external config file
		if (!Files.exists(EXTERNAL_CONFIG_PATH)) {
			try (InputStream is = getClass().getClassLoader().getResourceAsStream(CLASSPATH_CONFIG)) {
				if (is != null) {
					Files.copy(is, EXTERNAL_CONFIG_PATH);
				}
			}
		}

		// Read existing content or create from scratch
		String content;
		if (Files.exists(EXTERNAL_CONFIG_PATH)) {
			content = Files.readString(EXTERNAL_CONFIG_PATH);
		} else {
			content = "";
		}

		// Update only the question config lines
		content = updatePropertyLine(content, QUESTIONS_LIST, idsStr);
		content = updatePropertyLine(content, QUESTIONS_FOLDERS, foldersStr);
		content = updatePropertyLine(content, QUESTIONS_TESTERS, testersStr);
		content = updatePropertyLine(content, QUESTIONS_MAX_SCORES, scoresStr);
		content = updatePropertyLine(content, QUESTIONS_DEPENDENCY_FOLDERS, depFoldersStr);
		content = updatePropertyLine(content, QUESTIONS_DEPENDENCY_FILES, depFilesStr);

		Files.writeString(EXTERNAL_CONFIG_PATH, content);
	}

	private String updatePropertyLine(String content, String key, String value) {
		if (content.contains(key + "=")) {
			return content.replaceFirst("(?m)^" + java.util.regex.Pattern.quote(key) + "=.*$", key + "=" + value);
		} else {
			return content + "\n" + key + "=" + value;
		}
	}

	private String[] getArray(String key) {
		String value = properties.getProperty(key, "");
		if (value.isEmpty()) {
			return new String[0];
		}
		return value.split(COMMA, -1);
	}

	/**
	 * Return a string array for {@code key}, padded with empty strings to
	 * {@code expectedLength} if the property is missing or shorter.
	 */
	private String[] getOptionalArray(String key, int expectedLength) {
		String value = properties.getProperty(key, "");
		if (value.isEmpty()) {
			return new String[expectedLength];
		}
		String[] parts = value.split(COMMA, -1);
		if (parts.length == expectedLength) {
			return parts;
		}
		// Pad or truncate to match expected length
		String[] result = new String[expectedLength];
		for (int i = 0; i < expectedLength; i++) {
			result[i] = i < parts.length ? parts[i] : "";
		}
		return result;
	}

	private String toNullableValue(String value) {
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private List<String> toDependencyFileList(String depFilesValue) {
		String trimmed = depFilesValue.trim();
		if (trimmed.isEmpty()) {
			return java.util.Collections.emptyList();
		}
		return java.util.Arrays.asList(trimmed.split(SEMICOLON));
	}
}

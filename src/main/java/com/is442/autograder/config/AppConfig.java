package com.is442.autograder.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.is442.autograder.model.QuestionConfig;

/**
 * Loads and exposes externalized configuration from config.properties.
 */
public class AppConfig {

	private final Properties properties;

	public AppConfig() throws IOException {
		this.properties = new Properties();
		try (InputStream is = getClass().getClassLoader().getResourceAsStream("config.properties")) {
			if (is == null) {
				throw new IOException("config.properties not found on classpath");
			}
			properties.load(is);
		}
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
		String[] ids = getArray("questions.list");
		String[] folders = getArray("questions.folders");
		String[] testers = getArray("questions.testers");
		String[] maxScores = getArray("questions.max.scores");
		String[] depFolders = getOptionalArray("questions.dependency.folders", ids.length);
		String[] depFiles = getOptionalArray("questions.dependency.files", ids.length);

		if (ids.length != folders.length || ids.length != testers.length || ids.length != maxScores.length) {
			throw new IllegalStateException("Question configuration arrays must all be the same length. " + "Got: list="
					+ ids.length + ", folders=" + folders.length + ", testers=" + testers.length + ", maxScores="
					+ maxScores.length);
		}

		List<QuestionConfig> configs = new ArrayList<>();
		for (int i = 0; i < ids.length; i++) {
			String depFolder = depFolders[i].trim().isEmpty() ? null : depFolders[i].trim();
			List<String> depFileList = depFiles[i].trim().isEmpty()
					? java.util.Collections.emptyList()
					: java.util.Arrays.asList(depFiles[i].trim().split(";"));
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

	private String[] getArray(String key) {
		String value = properties.getProperty(key, "");
		if (value.isEmpty()) {
			return new String[0];
		}
		return value.split(",", -1);
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
		String[] parts = value.split(",", -1);
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
}

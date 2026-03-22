package com.is442.autograder.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads key=value pairs from a .env file in the working directory. Values are
 * stored internally and accessible via {@link #get(String)}, which falls back
 * to real environment variables when a key is not found in the file.
 *
 * <p>
 * Call {@link #load()} once at startup before reading any keys.
 */
public class EnvLoader {

	private static final Map<String, String> values = new HashMap<>();

	/**
	 * Load .env from the current working directory. Silently skips if the file does
	 * not exist. Lines starting with # are comments; blank lines are ignored.
	 */
	public static void load() {
		load(Paths.get(".env"));
	}

	/**
	 * Load .env from an explicit path (useful for testing).
	 */
	public static void load(Path envFile) {
		if (!Files.exists(envFile)) {
			return;
		}
		try {
			for (String line : Files.readAllLines(envFile)) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}
				int eq = line.indexOf('=');
				if (eq < 1) {
					continue;
				}
				String key = line.substring(0, eq).trim();
				String value = line.substring(eq + 1).trim();
				// Strip optional surrounding quotes
				if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
						|| (value.startsWith("'") && value.endsWith("'")))) {
					value = value.substring(1, value.length() - 1);
				}
				values.put(key, value);
			}
		} catch (IOException e) {
			System.err.println("Warning: could not read .env file: " + e.getMessage());
		}
	}

	/**
	 * Get a value by key. Checks the .env file first, then falls back to the real
	 * environment variable.
	 *
	 * @return the value, or null if not found in either source
	 */
	public static String get(String key) {
		String v = values.get(key);
		return v != null ? v : System.getenv(key);
	}
}

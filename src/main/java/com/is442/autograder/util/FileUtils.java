package com.is442.autograder.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * File operation utilities used across the grading pipeline.
 */
public class FileUtils {

	private FileUtils() {
	} // utility class

	/**
	 * Create a temp directory for grading work.
	 */
	public static Path createTempDir(String prefix) throws IOException {
		return Files.createTempDirectory(prefix);
	}
}

package com.is442.autograder.extraction;

import com.is442.autograder.model.StudentIdentity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Resolves student identity by parsing Java file header comments.
 *
 * Expected header format:
 *
 * <pre>
 * /*
 *  * Name: Ping Lee
 *  * Email ID: ping.lee.2023
 *  * /
 * </pre>
 */
public class IdentityResolver {
	private static final int HEADER_SCAN_LENGTH = 500;

	private static final Pattern NAME_PATTERN = Pattern.compile("\\*\\s*Name\\s*:\\s*([^*\\n]+)",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("\\*\\s*Email\\s*ID\\s*:\\s*([\\w.]+)",
			Pattern.CASE_INSENSITIVE);

	/**
	 * Attempt to resolve student identity from Java file headers. Scans all .java
	 * files in the submission until a valid identity is found.
	 *
	 * @param submissionRoot
	 *            root directory of the extracted submission
	 * @return resolved identity, or empty if none found
	 */
	public Optional<StudentIdentity> resolve(Path submissionRoot) throws IOException {
		try (Stream<Path> stream = Files.walk(submissionRoot)) {
			var javaFiles = stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
					.filter(p -> !p.getFileName().toString().contains("Tester")) // skip tester files
					.toList();

			for (Path javaFile : javaFiles) {
				Optional<StudentIdentity> identity = resolveFromFile(javaFile);
				if (identity.isPresent()) {
					return identity;
				}
			}
		}
		return Optional.empty();
	}

	/**
	 * Parse a single Java file for Name and Email ID in the header comment.
	 */
	public Optional<StudentIdentity> resolveFromFile(Path javaFile) throws IOException {
		HeaderParseResult parsed = parseHeader(javaFile);
		if (parsed.isComplete()) {
			return Optional.of(new StudentIdentity(parsed.name(), parsed.emailId()));
		}

		return Optional.empty();
	}

	/**
	 * Structured result of parsing the Name/Email header from a Java file.
	 */
	public record HeaderParseResult(String name, String emailId) {

		/** True if at least one field is non-empty. */
		public boolean hasAnyField() {
			return !name.isEmpty() || !emailId.isEmpty();
		}

		/** True if both fields are non-empty. */
		public boolean isComplete() {
			return !name.isEmpty() && !emailId.isEmpty();
		}
	}

	/**
	 * Parse the Name and Email ID header of a Java file without requiring both
	 * fields to be present. Useful for validation that distinguishes "header
	 * missing entirely" from "header present but incomplete".
	 *
	 * @param javaFile
	 *            path to the .java file to inspect
	 * @return a {@link HeaderParseResult} whose fields are empty strings when not
	 *         found
	 */
	public HeaderParseResult parseHeader(Path javaFile) throws IOException {
		String header = readHeader(javaFile);

		Matcher nameMatcher = NAME_PATTERN.matcher(header);
		Matcher emailMatcher = EMAIL_PATTERN.matcher(header);

		String name = nameMatcher.find() ? toTitleCase(nameMatcher.group(1).trim()) : "";
		String emailId = emailMatcher.find() ? emailMatcher.group(1).trim() : "";

		return new HeaderParseResult(name, emailId);
	}

	private String readHeader(Path javaFile) throws IOException {
		String content = Files.readString(javaFile);
		return content.substring(0, Math.min(content.length(), HEADER_SCAN_LENGTH));
	}

	/**
	 * Derive a display name from a username like "ping.lee.2023". Convention:
	 * firstname.lastname.year → "Firstname Lastname" (Title Case).
	 *
	 * @param username
	 *            e.g. "ping.lee.2023"
	 * @return derived name, or the username itself if it can't be parsed
	 */
	public String deriveNameFromUsername(String username) {
		if (username == null || username.isEmpty()) {
			return "";
		}

		String[] parts = username.split("\\.");
		if (parts.length < 2) {
			return toTitleCase(username);
		}

		// Remove the year suffix if the last part is all digits
		int namePartCount = parts.length;
		if (parts[namePartCount - 1].matches("\\d+")) {
			namePartCount--;
		}

		if (namePartCount == 0) {
			return username;
		}

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < namePartCount; i++) {
			if (i > 0) {
				sb.append(" ");
			}
			sb.append(toTitleCase(parts[i]));
		}
		return sb.toString();
	}

	/**
	 * Convert a string to Title Case (first letter uppercase, rest lowercase).
	 */
	public static String toTitleCase(String input) {
		if (input == null || input.isEmpty()) {
			return input;
		}
		String[] words = input.trim().split("\\s+");
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < words.length; i++) {
			if (i > 0) {
				sb.append(" ");
			}
			String word = words[i];
			if (word.length() == 1) {
				sb.append(word.toUpperCase());
			} else {
				sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase());
			}
		}
		return sb.toString();
	}
}

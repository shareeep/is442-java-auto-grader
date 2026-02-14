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
		String content = Files.readString(javaFile);

		// Only look in the first 500 chars (header area)
		String header = content.substring(0, Math.min(content.length(), 500));

		Matcher nameMatcher = NAME_PATTERN.matcher(header);
		Matcher emailMatcher = EMAIL_PATTERN.matcher(header);

		if (nameMatcher.find() && emailMatcher.find()) {
			String name = toTitleCase(nameMatcher.group(1).trim());
			String email = emailMatcher.group(1).trim();

			// Only return if both fields are non-empty
			if (!name.isEmpty() && !email.isEmpty()) {
				return Optional.of(new StudentIdentity(name, email));
			}
		}

		return Optional.empty();
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

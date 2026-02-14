package com.is442.autograder.extraction;

import com.is442.autograder.model.StudentIdentity;

import java.io.IOException;
import java.nio.file.*;
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

    private static final Pattern NAME_PATTERN = Pattern.compile(
            "\\*\\s*Name\\s*:\\s*([^*\\n]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\*\\s*Email\\s*ID\\s*:\\s*([\\w.]+)", Pattern.CASE_INSENSITIVE);

    /**
     * Attempt to resolve student identity from Java file headers.
     * Scans all .java files in the submission until a valid identity is found.
     *
     * @param submissionRoot root directory of the extracted submission
     * @return resolved identity, or empty if none found
     */
    public Optional<StudentIdentity> resolve(Path submissionRoot) throws IOException {
        try (Stream<Path> stream = Files.walk(submissionRoot)) {
            var javaFiles = stream
                    .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
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
            String name = nameMatcher.group(1).trim();
            String email = emailMatcher.group(1).trim();

            // Only return if both fields are non-empty
            if (!name.isEmpty() && !email.isEmpty()) {
                return Optional.of(new StudentIdentity(name, email));
            }
        }

        return Optional.empty();
    }
}

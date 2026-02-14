package com.is442.autograder.extraction;

import com.is442.autograder.model.Anomaly;
import com.is442.autograder.model.StudentIdentity;
import com.is442.autograder.model.StudentSubmission;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Normalizes extracted submission folder structures to the expected format:
 * {emailId}/
 * ├── Q1/
 * ├── Q2/
 * └── Q3/
 *
 * Handles known issues:
 * - Folder still named "RenameToYourStudentID" or "RenameToYourUsername"
 * - Q1/Q2/Q3 archived directly (no parent folder)
 * - Student ID used instead of email ID (e.g. "01400003")
 * - Extra nesting levels
 */
public class StructureNormalizer {

    private static final Set<String> PLACEHOLDER_NAMES = Set.of(
            "RenameToYourStudentID",
            "RenameToYourUsername",
            "renametoyourstudentid",
            "renametoyourusername");

    private static final Set<String> QUESTION_FOLDERS = Set.of("Q1", "Q2", "Q3");

    private final IdentityResolver identityResolver;

    public StructureNormalizer(IdentityResolver identityResolver) {
        this.identityResolver = identityResolver;
    }

    /**
     * Normalize the extracted submission structure.
     * After normalization, the returned path will contain Q1/, Q2/, Q3/ directly.
     *
     * @param extractedRoot the root where the ZIP was extracted
     * @param submission    the submission object (anomalies will be added here)
     * @return the normalized submission root containing Q1/Q2/Q3
     */
    public Path normalize(Path extractedRoot, StudentSubmission submission) throws IOException {
        // First, find where Q1/Q2/Q3 actually are
        Path questionParent = findQuestionParent(extractedRoot);

        if (questionParent == null) {
            // Q folders not found at all — this is a serious issue
            submission.addAnomaly(new Anomaly(
                    Anomaly.Type.MISSING_QUESTION_FOLDER,
                    "No Q1/Q2/Q3 folders found in submission",
                    Anomaly.Severity.ERROR));
            return extractedRoot;
        }

        // Resolve identity from Java file headers
        Optional<StudentIdentity> identity = identityResolver.resolve(questionParent);
        if (identity.isPresent()) {
            submission.setName(identity.get().getName());
            submission.setUsername(identity.get().getEmailId());
        }

        // Check if the immediate parent of Q1/Q2/Q3 has a proper name
        String parentName = questionParent.getFileName().toString();

        if (isPlaceholderName(parentName)) {
            submission.addAnomaly(new Anomaly(
                    Anomaly.Type.FOLDER_NOT_RENAMED,
                    "Folder was '" + parentName + "' (not renamed to student ID)",
                    Anomaly.Severity.WARNING));
        } else if (parentName.equals(extractedRoot.getFileName().toString())) {
            // Q1/Q2/Q3 are directly in the extraction root — no parent folder
            submission.addAnomaly(new Anomaly(
                    Anomaly.Type.NO_PARENT_FOLDER,
                    "Q1/Q2/Q3 archived without parent folder",
                    Anomaly.Severity.WARNING));
        } else if (parentName.matches("^\\d+$")) {
            // Numeric folder name — likely OrgDefinedId instead of email
            submission.addAnomaly(new Anomaly(
                    Anomaly.Type.STUDENT_ID_AS_FOLDER,
                    "Folder '" + parentName + "' appears to be a student ID, not email",
                    Anomaly.Severity.WARNING));
        }

        // If username wasn't resolved from headers, try to extract from folder/zip name
        if (submission.getUsername() == null || submission.getUsername().isEmpty()) {
            String fallback = extractUsernameFromZipName(submission.getZipFileName());
            if (fallback != null) {
                submission.setUsername(fallback);
            } else {
                submission.setUsername(parentName);
            }
        }

        return questionParent;
    }

    /**
     * Recursively search for the directory that directly contains Q1, Q2, or Q3.
     */
    private Path findQuestionParent(Path root) throws IOException {
        // Check if Q folders are directly under root
        if (hasQuestionFolders(root)) {
            return root;
        }

        // Check one level down
        try (Stream<Path> stream = Files.list(root)) {
            var dirs = stream.filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().startsWith("__MACOSX"))
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .toList();

            for (Path dir : dirs) {
                if (hasQuestionFolders(dir)) {
                    return dir;
                }
            }

            // Check two levels down (extra nesting)
            for (Path dir : dirs) {
                try (Stream<Path> subStream = Files.list(dir)) {
                    var subDirs = subStream.filter(Files::isDirectory).toList();
                    for (Path subDir : subDirs) {
                        if (hasQuestionFolders(subDir)) {
                            return subDir;
                        }
                    }
                }
            }
        }

        return null;
    }

    private boolean hasQuestionFolders(Path dir) {
        for (String qFolder : QUESTION_FOLDERS) {
            if (Files.isDirectory(dir.resolve(qFolder))) {
                return true; // at least one Q folder found
            }
        }
        return false;
    }

    private boolean isPlaceholderName(String name) {
        return PLACEHOLDER_NAMES.contains(name) || PLACEHOLDER_NAMES.contains(name.toLowerCase());
    }

    /**
     * Extract username from ZIP filename like "2023-2024-ping.lee.2023.zip"
     * Strategy: remove year prefix and .zip suffix.
     */
    private String extractUsernameFromZipName(String zipFileName) {
        if (zipFileName == null) {
            return null;
        }

        String name = zipFileName;
        if (name.endsWith(".zip")) {
            name = name.substring(0, name.length() - 4);
        }

        // Remove year prefix pattern like "2023-2024-"
        name = name.replaceFirst("^\\d{4}-\\d{4}-", "");

        return name.isEmpty() ? null : name;
    }
}

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
 * {emailId}/ ├── Q1/ ├── Q2/ └── Q3/
 *
 * Handles known issues: - Folder still named "RenameToYourStudentID" or
 * "RenameToYourUsername" - Q1/Q2/Q3 archived directly (no parent folder) -
 * Student ID used instead of email ID (e.g. "01400003") - Extra nesting levels
 */
public class StructureNormalizer {
	private static final String MACOSX_PREFIX = "__MACOSX";
	private static final String HIDDEN_DIR_PREFIX = ".";
	private static final String STUDENT_ID_REGEX = "^\\d+$";
	private static final String ZIP_SUFFIX = ".zip";
	private static final String YEAR_PREFIX_REGEX = "^\\d{4}-\\d{4}-";

	private static final Set<String> PLACEHOLDER_NAMES = Set.of("RenameToYourStudentID", "RenameToYourUsername",
			"renametoyourstudentid", "renametoyourusername");

	private static final Set<String> QUESTION_FOLDERS = Set.of("Q1", "Q2", "Q3");

	private final IdentityResolver identityResolver;

	public StructureNormalizer(IdentityResolver identityResolver) {
		this.identityResolver = identityResolver;
	}

	/**
	 * Normalize the extracted submission structure. After normalization, the
	 * returned path will contain Q1/, Q2/, Q3/ directly.
	 *
	 * @param extractedRoot
	 *            the root where the ZIP was extracted
	 * @param submission
	 *            the submission object (anomalies will be added here)
	 * @return the normalized submission root containing Q1/Q2/Q3
	 */
	public Path normalize(Path extractedRoot, StudentSubmission submission) throws IOException {
		// First, find where Q1/Q2/Q3 actually are
		Path questionParent = findQuestionParent(extractedRoot);

		if (questionParent == null) {
			// Q folders not found at all — this is a serious issue
			submission.addAnomaly(new Anomaly(Anomaly.Type.MISSING_QUESTION_FOLDER,
					"No Q1/Q2/Q3 folders found in submission", Anomaly.Severity.ERROR));
			return extractedRoot;
		}

		// Check for extra nesting (more than 1 level deep)
		reportExtraNesting(extractedRoot, questionParent, submission);

		// Resolve identity from Java file headers
		applyResolvedIdentity(questionParent, submission);

		// Check if the immediate parent of Q1/Q2/Q3 has a proper name
		String parentName = questionParent.getFileName().toString();

		reportParentFolderAnomaly(extractedRoot, parentName, submission);

		// If username wasn't resolved from headers, try to extract from folder/zip name
		if (submission.getUsername() == null || submission.getUsername().isEmpty()) {
			setFallbackUsername(submission, parentName);
		}

		return questionParent;
	}

	/**
	 * Recursively search for the directory that directly contains Q1, Q2, or Q3.
	 * Searches at unlimited depth to handle deeply nested submissions.
	 */
	private Path findQuestionParent(Path root) throws IOException {
		// Check if Q folders are directly under root
		if (hasQuestionFolders(root)) {
			return root;
		}

		// Recursively search subdirectories
		try (Stream<Path> stream = Files.list(root)) {
			var dirs = stream.filter(Files::isDirectory).filter(this::isSearchableDirectory).toList();

			for (Path dir : dirs) {
				// Recursively search this directory
				Path found = findQuestionParent(dir);
				if (found != null) {
					return found;
				}
			}
		}

		return null;
	}

	private void reportExtraNesting(Path extractedRoot, Path questionParent, StudentSubmission submission) {
		int depth = extractedRoot.relativize(questionParent).getNameCount();
		if (depth > 1) {
			String relativePath = extractedRoot.relativize(questionParent).toString();
			submission.addAnomaly(new Anomaly(Anomaly.Type.EXTRA_NESTING,
					"Student folder found at depth " + depth + ": " + relativePath, Anomaly.Severity.WARNING));
		}
	}

	private void applyResolvedIdentity(Path questionParent, StudentSubmission submission) throws IOException {
		Optional<StudentIdentity> identity = identityResolver.resolve(questionParent);
		if (identity.isPresent()) {
			submission.setName(identity.get().getName());
			submission.setUsername(identity.get().getEmailId());
		}
	}

	private void reportParentFolderAnomaly(Path extractedRoot, String parentName, StudentSubmission submission) {
		if (isPlaceholderName(parentName)) {
			submission.addAnomaly(new Anomaly(Anomaly.Type.FOLDER_NOT_RENAMED,
					"Folder was '" + parentName + "' (not renamed to student ID)", Anomaly.Severity.WARNING));
			return;
		}

		if (parentName.equals(extractedRoot.getFileName().toString())) {
			// Q1/Q2/Q3 are directly in the extraction root — no parent folder
			submission.addAnomaly(new Anomaly(Anomaly.Type.NO_PARENT_FOLDER, "Q1/Q2/Q3 archived without parent folder",
					Anomaly.Severity.WARNING));
			return;
		}

		if (parentName.matches(STUDENT_ID_REGEX)) {
			// Numeric folder name — likely OrgDefinedId instead of email
			submission.addAnomaly(new Anomaly(Anomaly.Type.STUDENT_ID_AS_FOLDER,
					"Folder '" + parentName + "' appears to be a student ID, not email", Anomaly.Severity.WARNING));
		}
	}

	private void setFallbackUsername(StudentSubmission submission, String parentName) {
		String fallback = extractUsernameFromZipName(submission.getZipFileName());
		if (fallback != null) {
			submission.setUsername(fallback);
		} else {
			submission.setUsername(parentName);
		}
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

	private boolean isSearchableDirectory(Path dir) {
		String name = dir.getFileName().toString();
		return !name.startsWith(MACOSX_PREFIX) && !name.startsWith(HIDDEN_DIR_PREFIX);
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
		if (name.endsWith(ZIP_SUFFIX)) {
			name = name.substring(0, name.length() - ZIP_SUFFIX.length());
		}

		// Remove year prefix pattern like "2023-2024-"
		name = name.replaceFirst(YEAR_PREFIX_REGEX, "");

		return name.isEmpty() ? null : name;
	}
}

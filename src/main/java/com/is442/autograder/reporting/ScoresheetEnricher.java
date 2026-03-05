package com.is442.autograder.reporting;

import com.is442.autograder.extraction.IdentityResolver;
import com.is442.autograder.model.StudentSubmission;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enriches {@link StudentSubmission} objects with official names and
 * OrgDefinedIds read from an IS442 scoresheet CSV.
 *
 * <p>
 * Scoresheet column layout (0-based index):
 *
 * <pre>
 *   [0] OrgDefinedId   e.g. "#01400001"
 *   [1] Username        e.g. "#ping.lee.2023"
 *   [3] First Name      e.g. "PING LEE"
 * </pre>
 *
 * Values may be prefixed with {@code '#'} which is stripped before matching.
 */
public class ScoresheetEnricher {

	private static final int COL_ORG_ID = 0;
	private static final int COL_USERNAME = 1;
	private static final int COL_FIRST_NAME = 3;
	private static final int MIN_COLUMNS = 5;

	/**
	 * Parse the scoresheet CSV and update each matched submission's
	 * {@code orgDefinedId} and official {@code name}.
	 *
	 * @param scoresheetPath
	 *            path to the template IS442-ScoreSheet.csv
	 * @param submissions
	 *            graded submissions to enrich (mutated in place)
	 */
	public void enrich(Path scoresheetPath, List<StudentSubmission> submissions) throws IOException {
		Map<String, StudentSubmission> byUsername = buildUsernameIndex(submissions);
		List<String> lines = Files.readAllLines(scoresheetPath);

		for (int i = 1; i < lines.size(); i++) { // row 0 is the header
			String line = lines.get(i).trim();
			if (line.isEmpty()) {
				continue;
			}

			String[] parts = line.split(",", -1);
			if (parts.length < MIN_COLUMNS) {
				continue;
			}

			String orgId = parts[COL_ORG_ID].trim();
			String rawUsername = parts[COL_USERNAME].trim();
			String firstName = parts[COL_FIRST_NAME].trim();

			// Strip '#' prefix that Brightspace adds to username and orgId columns
			String username = rawUsername.startsWith("#") ? rawUsername.substring(1) : rawUsername;
			StudentSubmission sub = byUsername.get(username.toLowerCase());
			if (sub == null) {
				continue;
			}

			if (!orgId.isEmpty()) {
				sub.setOrgDefinedId(orgId);
			}

			// Official name from scoresheet takes priority; convert to Title Case
			if (!firstName.isEmpty()) {
				sub.setName(IdentityResolver.toTitleCase(firstName));
			}
		}
	}

	/**
	 * Build a lookup map from lower-cased username to submission.
	 */
	private Map<String, StudentSubmission> buildUsernameIndex(List<StudentSubmission> submissions) {
		Map<String, StudentSubmission> index = new HashMap<>();
		for (StudentSubmission sub : submissions) {
			if (sub.getUsername() != null) {
				index.put(sub.getUsername().toLowerCase(), sub);
			}
		}
		return index;
	}
}

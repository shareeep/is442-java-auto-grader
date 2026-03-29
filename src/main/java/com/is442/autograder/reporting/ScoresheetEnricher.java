package com.is442.autograder.reporting;

import com.is442.autograder.extraction.IdentityResolver;
import com.is442.autograder.model.StudentSubmission;

import java.io.IOException;
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
		for (ScoresheetParser.ScoresheetRow row : ScoresheetParser.parse(scoresheetPath)) {
			StudentSubmission sub = byUsername.get(row.username().toLowerCase());
			if (sub == null) {
				continue;
			}

			if (!row.orgDefinedId().isEmpty()) {
				sub.setOrgDefinedId(row.orgDefinedId());
			}

			// Official name from scoresheet takes priority; convert to Title Case
			if (!row.firstName().isEmpty()) {
				sub.setName(IdentityResolver.toTitleCase(row.firstName()));
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

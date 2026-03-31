package com.is442.autograder.reporting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the LMS scoresheet CSV into a normalized row model shared by reporting
 * components.
 */
final class ScoresheetParser {

	private static final int MIN_COLUMNS = 5;
	private static final int COL_ORG_ID = 0;
	private static final int COL_USERNAME = 1;
	private static final int COL_LAST_NAME = 2;
	private static final int COL_FIRST_NAME = 3;

	private ScoresheetParser() {
	}

	static List<ScoresheetRow> parse(Path scoresheetPath) throws IOException {
		List<String> lines = Files.readAllLines(scoresheetPath);
		List<ScoresheetRow> rows = new ArrayList<>();

		for (int i = 1; i < lines.size(); i++) {
			String line = lines.get(i);
			if (line.trim().isEmpty()) {
				continue;
			}

			String[] parts = line.split(",", -1);
			if (parts.length < MIN_COLUMNS) {
				continue;
			}

			String orgId = parts[COL_ORG_ID].trim();
			String rawUsername = parts[COL_USERNAME].trim();
			String username = rawUsername.startsWith("#") ? rawUsername.substring(1) : rawUsername;
			String lastName = parts[COL_LAST_NAME].trim().equals("_") ? "" : parts[COL_LAST_NAME].trim();
			String firstName = parts[COL_FIRST_NAME].trim();
			String displayName = (firstName + (lastName.isEmpty() ? "" : " " + lastName)).trim();

			rows.add(new ScoresheetRow(orgId, rawUsername, username, firstName, displayName));
		}

		return rows;
	}
}

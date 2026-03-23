package com.is442.autograder.reporting;

/**
 * Immutable parsed row from the scoresheet CSV.
 */
record ScoresheetRow(String orgDefinedId, String rawUsername, String username, String firstName,
		String displayName) {
}

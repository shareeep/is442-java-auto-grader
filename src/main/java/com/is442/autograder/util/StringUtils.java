package com.is442.autograder.util;

/**
 * Shared string utility methods used across the grading pipeline.
 *
 * <p>
 * All methods are stateless and thread-safe.
 */
public final class StringUtils {

	private StringUtils() {
	} // utility class — non-instantiable

	/**
	 * Strip non-alphanumeric characters from an OrgDefinedId for consistent
	 * sorting. Returns {@code "zzz"} for null or all-punctuation identifiers so
	 * that students without an OrgDefinedId sort last.
	 *
	 * @param orgId
	 *            raw OrgDefinedId, e.g. {@code "#01400001"} or {@code null}
	 * @return normalised, sortable string
	 */
	public static String normalizeOrgId(String orgId) {
		if (orgId == null) {
			return "zzz";
		}
		String stripped = orgId.replaceAll("[^0-9a-zA-Z]", "");
		return stripped.isEmpty() ? "zzz" : stripped;
	}

	/**
	 * Truncate a string to at most {@code max} characters. If the string is longer,
	 * the last character position is replaced with the ellipsis character
	 * {@code …}.
	 *
	 * @param s
	 *            the string to truncate (may be {@code null})
	 * @param max
	 *            maximum number of characters to keep
	 * @return the (possibly truncated) string, never {@code null}
	 */
	public static String truncate(String s, int max) {
		if (s == null) {
			return "";
		}
		return s.length() <= max ? s : s.substring(0, max - 1) + "…";
	}
}

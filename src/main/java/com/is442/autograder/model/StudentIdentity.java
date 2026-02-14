package com.is442.autograder.model;

/**
 * Identity resolved from Java file header comments. Parsed from patterns like:
 * /* Name: Ping Lee * Email ID: ping.lee.2023
 */
public class StudentIdentity {

	private final String name;
	private final String emailId;

	public StudentIdentity(String name, String emailId) {
		this.name = name;
		this.emailId = emailId;
	}

	public String getName() {
		return name;
	}

	public String getEmailId() {
		return emailId;
	}

	@Override
	public String toString() {
		return name + " (" + emailId + ")";
	}
}

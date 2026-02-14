package com.is442.autograder.model;

/**
 * Result of compiling or executing an external process.
 */
public class ProcessResult {

	public enum Status {
		SUCCESS, COMPILE_ERROR, RUNTIME_ERROR, TIMEOUT
	}

	private final Status status;
	private final int exitCode;
	private final String stdout;
	private final String stderr;
	private final boolean timedOut;

	public ProcessResult(Status status, int exitCode, String stdout, String stderr, boolean timedOut) {
		this.status = status;
		this.exitCode = exitCode;
		this.stdout = stdout;
		this.stderr = stderr;
		this.timedOut = timedOut;
	}

	public static ProcessResult success(String stdout, String stderr) {
		return new ProcessResult(Status.SUCCESS, 0, stdout, stderr, false);
	}

	public static ProcessResult compileError(String stdout, String stderr) {
		return new ProcessResult(Status.COMPILE_ERROR, 1, stdout, stderr, false);
	}

	public static ProcessResult runtimeError(int exitCode, String stdout, String stderr) {
		return new ProcessResult(Status.RUNTIME_ERROR, exitCode, stdout, stderr, false);
	}

	public static ProcessResult timeout(String stdout) {
		return new ProcessResult(Status.TIMEOUT, -1, stdout, "", true);
	}

	public Status getStatus() {
		return status;
	}

	public int getExitCode() {
		return exitCode;
	}

	public String getStdout() {
		return stdout;
	}

	public String getStderr() {
		return stderr;
	}

	public boolean isTimedOut() {
		return timedOut;
	}

	public boolean isSuccess() {
		return status == Status.SUCCESS;
	}

	@Override
	public String toString() {
		return "ProcessResult{status=" + status + ", exitCode=" + exitCode + ", timedOut=" + timedOut + "}";
	}
}

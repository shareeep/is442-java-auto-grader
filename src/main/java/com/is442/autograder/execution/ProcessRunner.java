package com.is442.autograder.execution;

import com.is442.autograder.model.ProcessResult;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Executes external processes (javac, java) with timeout and output capture.
 * Uses separate threads for stdout/stderr to prevent deadlocks.
 */
public class ProcessRunner {

    private final int timeoutSeconds;

    public ProcessRunner(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Compile all Java files in a directory.
     *
     * @param workDir directory containing .java files
     * @return ProcessResult with compilation output
     */
    public ProcessResult compile(Path workDir) {
        // Find all .java files in the directory
        File[] javaFiles = workDir.toFile().listFiles((dir, name) -> name.endsWith(".java"));
        if (javaFiles == null || javaFiles.length == 0) {
            return ProcessResult.compileError("", "No .java files found in " + workDir);
        }

        // Build javac command: javac *.java
        List<String> command = new java.util.ArrayList<>();
        command.add("javac");
        for (File f : javaFiles) {
            command.add(f.getName());
        }

        return execute(workDir, command);
    }

    /**
     * Run a Java class in the given directory.
     *
     * @param workDir   directory containing compiled .class files
     * @param className the class to run (must have main method)
     * @return ProcessResult with execution output
     */
    public ProcessResult run(Path workDir, String className) {
        List<String> command = Arrays.asList("java", "-cp", ".", className);
        return execute(workDir, command);
    }

    /**
     * Execute a command with timeout and output capture.
     */
    private ProcessResult execute(Path workDir, List<String> command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workDir.toFile());
            // Don't redirect error stream — capture separately
            pb.redirectErrorStream(false);

            Process process = pb.start();

            // Capture stdout and stderr in separate threads to avoid deadlocks
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            Thread stdoutReader = new Thread(() -> readStream(process.getInputStream(), stdout));
            Thread stderrReader = new Thread(() -> readStream(process.getErrorStream(), stderr));

            stdoutReader.start();
            stderrReader.start();

            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                stdoutReader.interrupt();
                stderrReader.interrupt();
                return ProcessResult.timeout(stdout.toString());
            }

            // Wait for readers to finish
            stdoutReader.join(2000);
            stderrReader.join(2000);

            int exitCode = process.exitValue();

            if (exitCode == 0) {
                return ProcessResult.success(stdout.toString(), stderr.toString());
            } else {
                // Determine if this was a compile error or runtime error
                String cmd = command.get(0);
                if (cmd.equals("javac")) {
                    return ProcessResult.compileError(stdout.toString(), stderr.toString());
                } else {
                    return ProcessResult.runtimeError(exitCode, stdout.toString(), stderr.toString());
                }
            }
        } catch (IOException e) {
            return ProcessResult.compileError("", "Failed to start process: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ProcessResult.timeout("Process interrupted");
        }
    }

    private void readStream(InputStream is, StringBuilder sb) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (IOException e) {
            sb.append("[Error reading stream: ").append(e.getMessage()).append("]");
        }
    }
}

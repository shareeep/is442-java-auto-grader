package com.is442.autograder.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a student's submission bundle.
 * Holds the resolved identity, extracted path, per-question results, and
 * anomalies.
 */
public class StudentSubmission {

    private String username; // resolved email ID (e.g. "ping.lee.2023")
    private String name; // resolved name (e.g. "Ping Lee")
    private Path rootPath; // path to the extracted submission root
    private String zipFileName; // original ZIP file name
    private final List<QuestionResult> results = new ArrayList<>();
    private final List<Anomaly> anomalies = new ArrayList<>();

    public StudentSubmission(String zipFileName) {
        this.zipFileName = zipFileName;
    }

    // --- Identity ---

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    // --- Paths ---

    public Path getRootPath() {
        return rootPath;
    }

    public void setRootPath(Path rootPath) {
        this.rootPath = rootPath;
    }

    public String getZipFileName() {
        return zipFileName;
    }

    public void setZipFileName(String zipFileName) {
        this.zipFileName = zipFileName;
    }

    // --- Results ---

    public List<QuestionResult> getResults() {
        return results;
    }

    public void addResult(QuestionResult result) {
        results.add(result);
    }

    public double getTotalScore() {
        return results.stream().mapToDouble(QuestionResult::getScore).sum();
    }

    public double getMaxPossibleScore() {
        return results.stream().mapToDouble(QuestionResult::getMaxScore).sum();
    }

    // --- Anomalies ---

    public List<Anomaly> getAnomalies() {
        return anomalies;
    }

    public void addAnomaly(Anomaly anomaly) {
        anomalies.add(anomaly);
    }

    public boolean hasAnomalies() {
        return !anomalies.isEmpty();
    }

    // --- Display ---

    public String getDisplayName() {
        if (username != null && !username.isEmpty()) {
            return username;
        }
        return zipFileName != null ? zipFileName.replace(".zip", "") : "(unknown)";
    }

    @Override
    public String toString() {
        return "StudentSubmission{" + getDisplayName() + ", score=" + getTotalScore() + "}";
    }
}

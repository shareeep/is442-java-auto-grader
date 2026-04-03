package com.is442.autograder.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.is442.autograder.config.AppConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

/**
 * Browse past grading runs stored in the output/ directory.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportsController {

	private static final Logger logger = LoggerFactory.getLogger(ReportsController.class);
	private static final Path OUTPUT_DIR = Paths.get("output");
	private static final DateTimeFormatter RUN_ID_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final DateTimeFormatter PDF_NAME_FMT = DateTimeFormatter.ofPattern("dd-MM_HH-mm");

	private final AppConfig appConfig;

	public ReportsController(AppConfig appConfig) {
		this.appConfig = appConfig;
	}

	private String buildPdfFilename(String id) {
		try {
			String sgtTime = LocalDateTime.parse(id, RUN_ID_FMT).atZone(ZoneOffset.UTC)
					.withZoneSameInstant(ZoneId.of("Asia/Singapore")).format(PDF_NAME_FMT);
			String name = appConfig.getAssessmentName().toLowerCase().replaceAll("\\s+", "-");
			return name + "-results-" + sgtTime + ".pdf";
		} catch (Exception e) {
			return "grading-report-" + id + ".pdf";
		}
	}

	private boolean isUnsafePathSegment(String segment) {
		return segment.contains("..") || segment.contains("/") || segment.contains("\\");
	}

	@GetMapping("/list")
	public ResponseEntity<?> listRuns() {
		if (!Files.isDirectory(OUTPUT_DIR)) {
			return ResponseEntity.ok(List.of());
		}

		try (Stream<Path> stream = Files.list(OUTPUT_DIR)) {
			List<Map<String, Object>> runs = stream.filter(Files::isDirectory).sorted(Comparator.reverseOrder())
					.map(dir -> {
						Map<String, Object> run = new LinkedHashMap<>();
						String id = dir.getFileName().toString();
						run.put("id", id);
						run.put("timestamp", id); // format: yyyyMMdd-HHmmss
						boolean hasPdf = Files.exists(dir.resolve("instructor-report.pdf"));
						run.put("hasPdf", hasPdf);
						if (hasPdf)
							run.put("pdfFilename", buildPdfFilename(id));
						run.put("hasCsv", Files.exists(dir.resolve("IS442-ScoreSheet-Graded.csv"))
								|| Files.exists(dir.resolve("detailed-report.csv")));
						run.put("hasPlagiarism", Files.exists(dir.resolve("plagiarism-report.jplag")));

						// Count students: prefer results.json (written after every run and
						// accurate), fall back to logs/ subdirectories which may undercount if
						// a student's log dir was never created due to an early failure.
						Path resultsJson = dir.resolve("results.json");
						if (Files.exists(resultsJson)) {
							try {
								List<?> results = new ObjectMapper().readValue(resultsJson.toFile(), List.class);
								run.put("studentCount", results.size());
							} catch (Exception e) {
								run.put("studentCount", countLogDirs(dir));
							}
						} else {
							run.put("studentCount", countLogDirs(dir));
						}
						return run;
					}).toList();
			return ResponseEntity.ok(runs);
		} catch (IOException e) {
			return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
		}
	}

	private long countLogDirs(Path runDir) {
		Path logsDir = runDir.resolve("logs");
		if (!Files.isDirectory(logsDir))
			return 0;
		try (Stream<Path> s = Files.list(logsDir)) {
			return s.filter(Files::isDirectory).count();
		} catch (IOException e) {
			return 0;
		}
	}

	@GetMapping("/{id}/pdf")
	public ResponseEntity<Resource> getPdf(@PathVariable String id) {
		if (isUnsafePathSegment(id))
			return ResponseEntity.badRequest().build();
		Path pdf = OUTPUT_DIR.resolve(id).resolve("instructor-report.pdf");
		if (!Files.exists(pdf)) {
			return ResponseEntity.notFound().build();
		}
		Resource resource = new FileSystemResource(pdf);
		String displayFilename = buildPdfFilename(id);
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + displayFilename + "\"").body(resource);
	}

	@GetMapping("/{id}/pdf/{filename:.+}")
	public ResponseEntity<Resource> getPdfWithName(@PathVariable String id, @PathVariable String filename) {
		return getPdf(id);
	}

	@GetMapping("/{id}/csv")
	public ResponseEntity<Resource> getCsv(@PathVariable String id) {
		if (isUnsafePathSegment(id))
			return ResponseEntity.badRequest().build();
		Path runDir = OUTPUT_DIR.resolve(id);
		Path csv = runDir.resolve("IS442-ScoreSheet-Graded.csv");
		if (!Files.exists(csv)) {
			csv = runDir.resolve("detailed-report.csv");
		}
		if (!Files.exists(csv)) {
			return ResponseEntity.notFound().build();
		}
		Resource resource = new FileSystemResource(csv);
		return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv"))
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + csv.getFileName() + "\"")
				.body(resource);
	}

	@GetMapping("/{id}/plagiarism")
	public ResponseEntity<Resource> getPlagiarismReport(@PathVariable String id) {
		if (isUnsafePathSegment(id))
			return ResponseEntity.badRequest().build();
		Path report = OUTPUT_DIR.resolve(id).resolve("plagiarism-report.jplag");
		if (!Files.exists(report)) {
			return ResponseEntity.notFound().build();
		}
		Resource resource = new FileSystemResource(report);
		return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"plagiarism-report-" + id + ".jplag\"")
				.body(resource);
	}

	@GetMapping("/{id}/results")
	public ResponseEntity<?> getResults(@PathVariable String id) {
		if (isUnsafePathSegment(id))
			return ResponseEntity.badRequest().build();
		Path results = OUTPUT_DIR.resolve(id).resolve("results.json");
		if (!Files.exists(results)) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(new FileSystemResource(results));
	}

	@GetMapping("/{id}/code/{username}")
	public ResponseEntity<?> getStudentCode(@PathVariable String id, @PathVariable String username) {
		if (isUnsafePathSegment(id) || isUnsafePathSegment(username))
			return ResponseEntity.badRequest().build();
		Path codeDir = OUTPUT_DIR.resolve(id).resolve("code").resolve(username);
		if (!Files.isDirectory(codeDir)) {
			return ResponseEntity.notFound().build();
		}
		try {
			Map<String, String> files = new LinkedHashMap<>();
			try (Stream<Path> walk = Files.walk(codeDir)) {
				walk.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java")).sorted().forEach(p -> {
					try {
						files.put(codeDir.relativize(p).toString(), Files.readString(p));
					} catch (IOException e) {
						logger.warn("Could not read {}", p, e);
					}
				});
			}
			return ResponseEntity.ok(files);
		} catch (IOException e) {
			return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
		}
	}

	@GetMapping("/{id}/logs")
	public ResponseEntity<?> getLogs(@PathVariable String id) {
		if (isUnsafePathSegment(id))
			return ResponseEntity.badRequest().build();
		Path logsDir = OUTPUT_DIR.resolve(id).resolve("logs");
		if (!Files.isDirectory(logsDir)) {
			return ResponseEntity.ok(Map.of("runLog", "", "students", List.of()));
		}

		try {
			Map<String, Object> result = new LinkedHashMap<>();

			// Read main run log
			Path runLog = logsDir.resolve("run.log");
			result.put("runLog", Files.exists(runLog) ? Files.readString(runLog) : "");

			// List student log folders
			try (Stream<Path> stream = Files.list(logsDir)) {
				List<Map<String, Object>> students = stream.filter(Files::isDirectory).sorted().map(studentDir -> {
					Map<String, Object> student = new LinkedHashMap<>();
					student.put("username", studentDir.getFileName().toString());
					try (Stream<Path> logFiles = Files.list(studentDir)) {
						List<Map<String, String>> logs = logFiles.filter(f -> f.toString().endsWith(".log")).sorted()
								.map(f -> {
									try {
										return Map.of("name", f.getFileName().toString(), "content",
												Files.readString(f));
									} catch (IOException e) {
										return Map.of("name", f.getFileName().toString(), "content",
												"Error reading: " + e.getMessage());
									}
								}).toList();
						student.put("logs", logs);
					} catch (IOException e) {
						student.put("logs", List.of());
					}
					return student;
				}).toList();
				result.put("students", students);
			}

			return ResponseEntity.ok(result);
		} catch (IOException e) {
			return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
		}
	}
}

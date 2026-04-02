package com.is442.autograder.web;

import com.is442.autograder.GradingPipeline;
import com.is442.autograder.config.AppConfig;
import com.is442.autograder.generation.ConfigInferenceService;
import com.is442.autograder.model.InferredConfig;
import com.is442.autograder.model.QuestionConfig;
import com.is442.autograder.model.StudentSubmission;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * SSE endpoint that streams grading progress in real time.
 */
@RestController
@RequestMapping("/api/grade")
public class GradingStreamController {

	private static final Logger logger = LoggerFactory.getLogger(GradingStreamController.class);
	private final ExecutorService executor = Executors.newFixedThreadPool(4);
	private final AppConfig appConfig;

	// Stores emitters for active grading sessions
	private static final Map<String, SseEmitter> ACTIVE_SESSIONS = new java.util.concurrent.ConcurrentHashMap<>();

	public GradingStreamController(AppConfig appConfig) {
		this.appConfig = appConfig;
	}

	@PostMapping("/stream")
	public SseEmitter streamGrading(HttpServletResponse response,
			@RequestParam("submissions") MultipartFile[] submissionsFiles,
			@RequestParam("testers") MultipartFile[] testersFiles,
			@RequestParam(value = "scoresheet", required = false) MultipartFile scoresheetFile) throws IOException {

		// Disable Tomcat's response buffer so each SSE event is flushed to the
		// client immediately rather than being held until the buffer is full.
		response.setBufferSize(0);
		response.setHeader("X-Accel-Buffering", "no"); // also disable nginx buffering if present

		SseEmitter emitter = new SseEmitter(600_000L); // 10 min timeout
		String sessionId = UUID.randomUUID().toString();
		ACTIVE_SESSIONS.put(sessionId, emitter);

		emitter.onCompletion(() -> ACTIVE_SESSIONS.remove(sessionId));
		emitter.onTimeout(() -> ACTIVE_SESSIONS.remove(sessionId));

		// Save uploaded files to temp dirs
		Path submissionsDir = Files.createTempDirectory("autograder-stream-submissions-");
		for (MultipartFile file : submissionsFiles) {
			if (!file.isEmpty() && file.getOriginalFilename() != null
					&& file.getOriginalFilename().toLowerCase().endsWith(".zip")) {
				Path dest = submissionsDir.resolve(Paths.get(file.getOriginalFilename()).getFileName());
				file.transferTo(dest.toFile());
			}
		}

		Path testersDir = Files.createTempDirectory("autograder-stream-testers-");
		for (MultipartFile file : testersFiles) {
			if (!file.isEmpty() && file.getOriginalFilename() != null
					&& !file.getOriginalFilename().contains(".DS_Store")) {
				Path dest = testersDir.resolve(Paths.get(file.getOriginalFilename()).getFileName());
				file.transferTo(dest.toFile());
			}
		}

		Path scoresheetPath = null;
		if (scoresheetFile != null && !scoresheetFile.isEmpty()) {
			scoresheetPath = Files.createTempFile("autograder-stream-scoresheet-", ".csv");
			scoresheetFile.transferTo(scoresheetPath.toFile());
		}

		Path outputDir = Paths.get("output");
		final Path finalScoresheet = scoresheetPath;

		executor.submit(() -> {
			try {
				emitter.send(SseEmitter.event().name("session").data(Map.of("sessionId", sessionId)));
				emitter.send(SseEmitter.event().name("status")
						.data(Map.of("phase", "inference", "message", "Inferring question configuration...")));

				ConfigInferenceService inferenceService = new ConfigInferenceService();
				InferredConfig inferred = inferenceService.inferConfig(null, appConfig.getTemplateFolder(),
						testersDir.toString());
				List<QuestionConfig> inferredConfigs = inferenceService.toQuestionConfigs(inferred);

				emitter.send(SseEmitter.event().name("status").data(
						Map.of("phase", "inference", "message", "Inferred " + inferredConfigs.size() + " question(s): "
								+ inferredConfigs.stream().map(QuestionConfig::getQuestionId).toList())));

				GradingPipeline pipeline = new GradingPipeline(appConfig);
				pipeline.setInferredQuestionConfigs(inferredConfigs);

				emitter.send(SseEmitter.event().name("status")
						.data(Map.of("phase", "grading", "message", "Starting grading...")));

				List<StudentSubmission> submissions = pipeline.run(submissionsDir, testersDir, finalScoresheet,
						outputDir, sub -> {
							try {
								emitter.send(SseEmitter.event().name("student").data(buildStudentPayload(sub)));
							} catch (IOException e) {
								logger.warn("Failed to stream student event for {}", sub.getUsername(), e);
							}
						}, displayName -> {
							try {
								emitter.send(SseEmitter.event().name("status")
										.data(Map.of("phase", "grading", "message", "Grading " + displayName + "...")));
							} catch (IOException e) {
								logger.warn("Failed to stream start event for {}", displayName, e);
							}
						});

				// RunResults page can load them automatically because they are written by
				// GradingPipeline
				String runId = null;
				if (Files.isDirectory(outputDir)) {
					try (Stream<Path> ls = Files.list(outputDir)) {
						Path runDir = ls.filter(Files::isDirectory).max(Comparator.naturalOrder()).orElse(null);
						if (runDir != null) {
							runId = runDir.getFileName().toString();
						}
					} catch (IOException e) {
						logger.warn("Could not locate run output directory", e);
					}
				}

				Map<String, Object> completePayload = new LinkedHashMap<>();
				completePayload.put("totalStudents", submissions.size());
				completePayload.put("message", "Grading complete.");
				if (runId != null)
					completePayload.put("runId", runId);
				emitter.send(SseEmitter.event().name("complete").data(completePayload));
				emitter.complete();

			} catch (Exception e) {
				logger.error("SSE grading stream error", e);
				try {
					emitter.send(SseEmitter.event().name("error").data(Map.of("message", e.getMessage())));
					emitter.complete();
				} catch (IOException ex) {
					emitter.completeWithError(ex);
				}
			}
		});

		return emitter;
	}

	private Map<String, Object> buildStudentPayload(StudentSubmission sub) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("username", sub.getUsername());
		data.put("name", sub.getName());
		data.put("displayName", sub.getDisplayName());
		data.put("totalScore", sub.getTotalScore());
		data.put("maxPossibleScore", sub.getMaxPossibleScore());

		List<Map<String, Object>> results = new ArrayList<>();
		for (var res : sub.getResults()) {
			results.add(
					Map.of("questionId", res.getQuestionId(), "score", res.getScore(), "maxScore", res.getMaxScore()));
		}
		data.put("results", results);

		List<Map<String, Object>> anomalies = new ArrayList<>();
		for (var ano : sub.getAnomalies()) {
			anomalies.add(Map.of("severity", ano.getSeverity().name(), "description", ano.getDescription()));
		}
		data.put("anomalies", anomalies);

		return data;
	}
}

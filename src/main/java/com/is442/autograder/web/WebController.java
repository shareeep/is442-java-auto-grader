package com.is442.autograder.web;

import com.is442.autograder.GradingPipeline;
import com.is442.autograder.config.AppConfig;
import com.is442.autograder.model.Anomaly;
import com.is442.autograder.model.StudentSubmission;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/grade")
public class WebController {

    @PostMapping(consumes = {"multipart/form-data"})
    public Map<String, Object> runGrading(
            @RequestParam("submissions") MultipartFile[] submissionsFiles,
            @RequestParam("testers") MultipartFile[] testersFiles,
            @RequestParam(value = "scoresheet", required = false) MultipartFile scoresheetFile
    ) {
        Map<String, Object> response = new HashMap<>();

        try {
            // Setup pipeline
            AppConfig config = new AppConfig();
            GradingPipeline pipeline = new GradingPipeline(config);

            // Create temp directories
            Path submissionsDir = Files.createTempDirectory("autograder-submissions-");
            for (MultipartFile file : submissionsFiles) {
                if (!file.isEmpty() && file.getOriginalFilename() != null) {
                    if (file.getOriginalFilename().toLowerCase().endsWith(".zip")) {
                        Path dest = submissionsDir.resolve(Paths.get(file.getOriginalFilename()).getFileName());
                        file.transferTo(dest.toFile());
                    }
                }
            }

            Path testersDir = Files.createTempDirectory("autograder-testers-");
            for (MultipartFile file : testersFiles) {
                if (!file.isEmpty() && file.getOriginalFilename() != null) {
                    if (!file.getOriginalFilename().contains(".DS_Store")) {
                        Path dest = testersDir.resolve(Paths.get(file.getOriginalFilename()).getFileName());
                        file.transferTo(dest.toFile());
                    }
                }
            }

            Path scoresheetPath = null;
            if (scoresheetFile != null && !scoresheetFile.isEmpty()) {
                scoresheetPath = Files.createTempFile("autograder-scoresheet-", ".csv");
                scoresheetFile.transferTo(scoresheetPath.toFile());
            }

            Path outputDir = Paths.get("output");

            // Run pipeline
            List<StudentSubmission> submissions = pipeline.run(submissionsDir, testersDir, scoresheetPath, outputDir);

            // Map results to avoid Jackson serialization issues with java.nio.file.Path
            List<Map<String, Object>> mappedSubmissions = submissions.stream().map(sub -> {
                Map<String, Object> map = new HashMap<>();
                map.put("username", sub.getUsername());
                map.put("name", sub.getName());
                map.put("displayName", sub.getDisplayName());
                map.put("orgDefinedId", sub.getOrgDefinedId());
                map.put("zipFileName", sub.getZipFileName());
                map.put("totalScore", sub.getTotalScore());
                map.put("maxPossibleScore", sub.getMaxPossibleScore());
                
                List<Map<String, Object>> results = sub.getResults().stream().map(res -> {
                    Map<String, Object> resMap = new HashMap<>();
                    resMap.put("questionId", res.getQuestionId());
                    resMap.put("score", res.getScore());
                    resMap.put("maxScore", res.getMaxScore());
                    return resMap;
                }).collect(Collectors.toList());
                map.put("results", results);
                
                List<Map<String, Object>> anomalies = sub.getAnomalies().stream().map(ano -> {
                    Map<String, Object> anoMap = new HashMap<>();
                    anoMap.put("severity", ano.getSeverity().name());
                    anoMap.put("description", ano.getDescription());
                    return anoMap;
                }).collect(Collectors.toList());
                map.put("anomalies", anomalies);

                return map;
            }).collect(Collectors.toList());

            response.put("status", "success");
            response.put("submissions", mappedSubmissions);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }

        return response;
    }
}

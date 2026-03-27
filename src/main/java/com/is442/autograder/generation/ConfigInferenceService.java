package com.is442.autograder.generation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.is442.autograder.model.ConfigConflict;
import com.is442.autograder.model.ConfigConflict.ConflictType;
import com.is442.autograder.model.InferredConfig;
import com.is442.autograder.model.InferredQuestionConfig;

public class ConfigInferenceService {

	private static final Logger logger = LoggerFactory.getLogger(ConfigInferenceService.class);

	// Pattern to find all ## Question X headers in markdown
	private static final Pattern QUESTION_HEADER_FINDER = Pattern
			.compile("(?mi)^##\\s*Question\\s+(\\d+)(?:([a-z]))?\\b", Pattern.MULTILINE);

	public InferredConfig inferConfig(String markdownContent, String templateDirStr, String testerDirStr) {
		InferredConfig config = new InferredConfig();
		config.setAssessmentName("Auto-Inferred Assessment");

		Path templateDir = templateDirStr != null ? Paths.get(templateDirStr) : null;
		Path testerDir = testerDirStr != null ? Paths.get(testerDirStr) : null;

		if (templateDir != null && Files.exists(templateDir)) {
			config.setTemplateFolder(templateDir.getFileName().toString());
		}

		// 1. Scan PDF Markdown for Question IDs
		List<String> pdfQuestionIds = extractQuestionsFromMarkdown(markdownContent);
		logger.info("[INFER] Step 1 — PDF scan: detected {} question(s): {}", pdfQuestionIds.size(), pdfQuestionIds);
		Map<String, InferredQuestionConfig> qMap = new HashMap<>();

		for (String qid : pdfQuestionIds) {
			InferredQuestionConfig qc = new InferredQuestionConfig();
			qc.setQuestionId(qid);
			qc.setInferredFromPdf(true);
			qMap.put(qid, qc);
		}

		// Note: Implicit parent detection happens in Step 3b (after testers are
		// scanned)

		// 2. Scan Template Directory
		List<Path> allFolders = new ArrayList<>();
		if (templateDir == null) {
			logger.info("[INFER] Step 2 — Template dir: not provided, skipping folder matching");
		} else if (!Files.exists(templateDir)) {
			logger.warn("[INFER] Step 2 — Template dir: path does not exist  path={}", templateDir.toAbsolutePath());
		} else {
			logger.info("[INFER] Step 2 — Template dir: scanning  path={}", templateDir.toAbsolutePath());

			// First, collect all directories recursively
			collectAllFolders(templateDir, allFolders);

			// Filter to unique folder names only (dedupe - don't process same folder
			// multiple times)
			List<String> uniqueFolderNames = allFolders.stream().map(p -> p.getFileName().toString())
					.filter(name -> name.matches("(?i)Q\\d+[a-z]?")).distinct().collect(Collectors.toList());

			logger.info("[INFER] Step 2 — Unique question folders: {}", uniqueFolderNames);

			// Process each unique folder only once
			for (String folderName : uniqueFolderNames) {
				// Skip if question already has a folder assigned
				boolean alreadyAssigned = qMap.values().stream()
						.anyMatch(qc -> folderName.equalsIgnoreCase(qc.getFolder()));
				if (alreadyAssigned)
					continue;

				String folderLower = folderName.toLowerCase();

				// Find matching questions
				for (InferredQuestionConfig qc : qMap.values()) {
					String qIdLower = qc.getQuestionId().toLowerCase();
					if (qIdLower.startsWith(folderLower) && (qc.getFolder() == null || qc.getFolder().isEmpty())) {
						qc.setFolder(folderName);
						qc.setDependencyFolder(folderName);
						logger.info("[INFER] Step 2 — Matched folder '{}' → {}", folderName, qc.getQuestionId());

						// Find and collect deps for this specific folder
						Path folderPath = allFolders.stream()
								.filter(p -> p.getFileName().toString().equalsIgnoreCase(folderName)).findFirst()
								.orElse(null);
						if (folderPath != null) {
							List<String> deps = getDependencies(folderPath);
							qc.getDependencyFiles().addAll(deps);
						}
					}
				}
			}
		}

		// 2b. Smart folder assignment: if no folder matched, try to infer
		for (InferredQuestionConfig qc : qMap.values()) {
			if (qc.getFolder() == null || qc.getFolder().isEmpty()) {
				String qIdPrefix = extractQuestionPrefix(qc.getQuestionId());

				// Look for folder matching prefix (e.g., Q1a -> look for Q1)
				for (Path folder : allFolders) {
					String fname = folder.getFileName().toString().toLowerCase();
					if (fname.equals(qIdPrefix) || fname.startsWith(qIdPrefix.toLowerCase())) {
						qc.setFolder(folder.getFileName().toString());
						qc.setDependencyFolder(folder.getFileName().toString());
						logger.info("[INFER] Step 2b — Inferred folder '{}' for {}", qc.getFolder(),
								qc.getQuestionId());
						break;
					}
				}
			}
		}

		// 3. Scan Tester Directory
		if (testerDir == null) {
			logger.info("[INFER] Step 3 — Tester dir: not provided, skipping tester matching");
		} else if (!Files.exists(testerDir)) {
			logger.warn("[INFER] Step 3 — Tester dir: path does not exist  path={}", testerDir.toAbsolutePath());
		} else {
			logger.info("[INFER] Step 3 — Tester dir: scanning  path={}", testerDir.toAbsolutePath());
			try (Stream<Path> stream = Files.list(testerDir)) {
				List<Path> testerFiles = stream.filter(p -> p.toString().endsWith("Tester.java"))
						.collect(Collectors.toList());
				logger.info("[INFER] Step 3 — Found {} Tester.java file(s): {}", testerFiles.size(),
						testerFiles.stream().map(p -> p.getFileName().toString()).collect(Collectors.toList()));

				for (Path tFile : testerFiles) {
					String filename = tFile.getFileName().toString();
					String testerClassName = filename.replace(".java", "");
					String inferredQid = testerClassName.replace("Tester", "");

					boolean isNew = !qMap.containsKey(inferredQid);
					InferredQuestionConfig qc = qMap.computeIfAbsent(inferredQid, k -> {
						InferredQuestionConfig newQc = new InferredQuestionConfig();
						newQc.setQuestionId(k);
						return newQc;
					});

					qc.setTester(testerClassName);
					qc.setMaxScore(computeMaxScoreFromTester(tFile));
					if (isNew) {
						logger.info("[INFER] Step 3 — Tester '{}' → new question {} (not in PDF)", filename,
								inferredQid);
					} else {
						logger.info("[INFER] Step 3 — Tester '{}' → matched {} (maxScore={})", filename, inferredQid,
								qc.getMaxScore());
					}
				}
			} catch (IOException e) {
				logger.error("[INFER] Step 3 — Error reading tester directory  path={}", testerDir, e);
			}
		}

		// 3b. Smart sub-question handling - create implicit parents and validate
		// sub-parts
		logger.info("[INFER] Step 3b — Analyzing sub-question structure");
		Map<String, List<String>> parentToSubMap = new HashMap<>();

		// First, identify all sub-questions and create implicit parents if needed
		for (String qid : qMap.keySet()) {
			if (qid.length() > 2) {
				String parent = extractQuestionPrefix(qid); // handles Q10a -> Q10
				parentToSubMap.computeIfAbsent(parent, k -> new ArrayList<>()).add(qid);

				// Create implicit parent if it doesn't exist
				if (!qMap.containsKey(parent)) {
					logger.info("[INFER] Step 3b — Creating implicit parent {} for sub-question {}", parent, qid);
					InferredQuestionConfig parentQc = new InferredQuestionConfig();
					parentQc.setQuestionId(parent);
					parentQc.setImplicitParent(true);
					qMap.put(parent, parentQc);
				} else {
					// Parent already exists (from PDF) - mark it as implicit if it has
					// sub-questions
					InferredQuestionConfig parentQc = qMap.get(parent);
					if (!parentQc.isImplicitParent()) {
						parentQc.setImplicitParent(true);
						logger.info("[INFER] Step 3b — Marked existing parent {} as implicit (has sub-questions)",
								parent);
					}
				}
			}
		}

		// Now process each parent and its sub-parts
		for (Map.Entry<String, List<String>> entry : parentToSubMap.entrySet()) {
			String parent = entry.getKey();
			List<String> subs = entry.getValue();
			logger.debug("[INFER] Step 3b — Parent {} has sub-parts: {}", parent, subs);

			// If parent exists, mark sub-parts as children
			if (qMap.containsKey(parent)) {
				InferredQuestionConfig parentQc = qMap.get(parent);

				for (String sub : subs) {
					InferredQuestionConfig subQc = qMap.get(sub);
					// Inherit folder from parent if sub has no folder
					if ((subQc.getFolder() == null || subQc.getFolder().isEmpty()) && parentQc.getFolder() != null) {
						subQc.setFolder(parentQc.getFolder());
						subQc.setDependencyFolder(parentQc.getDependencyFolder());
						logger.info("[INFER] Step 3b — Sub-question {} inherited folder from parent {}", sub, parent);
						subQc.setDependencyFolder(qMap.get(parent).getDependencyFolder());
						logger.info("[INFER] Step 3b — Sub-question {} inherited folder '{}' from parent {}", sub,
								subQc.getFolder(), parent);
					}
				}
			}
		}

		// 4. Cross-Reference & Build Conflicts
		logger.info("[INFER] Step 4 — Cross-referencing {} question(s)", qMap.size());
		for (InferredQuestionConfig qc : qMap.values()) {
			// LIKELY_FALSE check: has folder but no PDF presence AND no tester - skip
			// entirely
			boolean hasPdfPresence = qc.isInferredFromPdf() || pdfQuestionIds.contains(qc.getQuestionId());
			boolean hasTester = qc.getTester() != null && !qc.getTester().isEmpty();
			boolean hasFolder = qc.getFolder() != null && !qc.getFolder().isEmpty();

			if (hasFolder && !hasPdfPresence && !hasTester && !qc.isImplicitParent()) {
				logger.warn("[INFER] Step 4 — LIKELY_FALSE: {} has folder but no PDF/tester - skipping",
						qc.getQuestionId());
				continue; // Don't add to config
			}

			config.getQuestions().add(qc);

			// Determine if this is a sub-question
			String parentId = qc.getQuestionId().length() > 2 ? extractQuestionPrefix(qc.getQuestionId()) : null;
			boolean hasParent = parentId != null && qMap.containsKey(parentId);

			// MISSING_FOLDER: only flag if no folder AND no parent with folder, AND not
			// implicit parent
			if ((qc.getFolder() == null || qc.getFolder().isEmpty())
					&& !(hasParent && qMap.get(parentId).getFolder() != null) && !qc.isImplicitParent()) {
				logger.warn("[INFER] Step 4 — {} MISSING_FOLDER (no template subfolder matched)", qc.getQuestionId());
				config.addConflict(new ConfigConflict(qc.getQuestionId(), ConflictType.MISSING_FOLDER,
						"No template folder found for question " + qc.getQuestionId(),
						"Create a named subfolder in the template directory."));
			} else if (qc.isImplicitParent()) {
				logger.info("[INFER] Step 4 — Skipping MISSING_FOLDER for implicit parent {}", qc.getQuestionId());
			}

			// MISSING_TESTER: flag if from PDF and has NO sub-questions with testers
			if (qc.getTester() == null || qc.getTester().isEmpty()) {
				// Skip if implicit parent
				if (qc.isImplicitParent()) {
					logger.info("[INFER] Step 4 — Skipping MISSING_TESTER for implicit parent {}", qc.getQuestionId());
				}
				// Skip if parent has sub-questions with testers (e.g., Q1 has Q1a/Q1b testers)
				else if (hasSubQuestionTesters(qc.getQuestionId(), qMap)) {
					logger.info("[INFER] Step 4 — Skipping MISSING_TESTER for {} (has sub-question testers)",
							qc.getQuestionId());
				} else if (qc.isInferredFromPdf() || hasSubQuestionsInPdf(qc.getQuestionId(), qMap, pdfQuestionIds)) {
					logger.warn("[INFER] Step 4 — {} MISSING_TESTER (no Tester.java matched)", qc.getQuestionId());
					config.addConflict(new ConfigConflict(qc.getQuestionId(), ConflictType.MISSING_TESTER,
							"No tester file found for question " + qc.getQuestionId(),
							"Provide a tester file ending with 'Tester.java', or auto-generate one."));
				}
			}
			// ORPHAN_TESTER: only flag if no parent exists AND not implicit parent
			else if (!qc.isInferredFromPdf() && !hasParent
					&& !hasSubQuestionsInPdf(qc.getQuestionId(), qMap, pdfQuestionIds) && !qc.isImplicitParent()) {
				logger.warn("[INFER] Step 4 — {} ORPHAN_TESTER (tester exists but question not in PDF)",
						qc.getQuestionId());
				config.addConflict(new ConfigConflict(qc.getQuestionId(), ConflictType.ORPHAN_TESTER,
						"Tester file " + qc.getTester() + ".java found, but no such question was detected in the PDF.",
						"Verify if this question belongs to the exam or remove the tester."));
			}

			if (qc.getDependencyFiles() != null && qc.getDependencyFiles().contains("TODO_MISSING")) {
				config.addConflict(new ConfigConflict(qc.getQuestionId(), ConflictType.INCOMPLETE_DEPENDENCIES,
						"Dependencies may be incomplete.", "Check your dependency files."));
			}
		}

		// Sort by question ID naturally
		config.getQuestions().sort((a, b) -> a.getQuestionId().compareTo(b.getQuestionId()));

		logger.info("[INFER] Complete — {} question(s), {} conflict(s)", config.getQuestions().size(),
				config.getConflicts().size());
		return config;
	}

	private List<String> extractQuestionsFromMarkdown(String markdown) {
		List<String> questions = new ArrayList<>();
		if (markdown == null)
			return questions;

		logger.debug("[INFER] Sample markdown (first 500 chars): {}",
				markdown.substring(0, Math.min(500, markdown.length())));

		// Find all ## Question X headers
		for (Matcher m = QUESTION_HEADER_FINDER.matcher(markdown); m.find();) {
			String mainNum = m.group(1);
			String subLetter = m.group(2);
			String qId = subLetter != null ? "Q" + mainNum + subLetter.toLowerCase() : "Q" + mainNum;
			if (!questions.contains(qId)) {
				questions.add(qId);
				logger.debug("[INFER] Detected question: {}", qId);
			}
		}

		logger.info("[INFER] PDF questions found: {}", questions);
		return questions;
	}

	private List<String> getDependencies(Path folderPath) {
		List<String> deps = new ArrayList<>();
		try (Stream<Path> stream = Files.list(folderPath)) {
			stream.forEach(p -> {
				String name = p.getFileName().toString();
				if (name.endsWith(".class") || name.endsWith(".txt")) {
					deps.add(name);
				}
			});
		} catch (IOException e) {
			logger.error("Could not read dependencies", e);
		}
		return deps;
	}

	private static final Pattern TCNUM_INCREMENT = Pattern.compile("tcNum\\s*\\+\\+");

	private double computeMaxScoreFromTester(Path testerPath) {
		try {
			String content = Files.readString(testerPath);
			Matcher tcMatcher = TCNUM_INCREMENT.matcher(content);
			int count = 0;
			while (tcMatcher.find()) count++;
			if (count > 0)
				return count;

			// Fallback: look for generic @Test test cases and count them roughly
			Matcher m = Pattern.compile("@Test").matcher(content);
			int altCount = 0;
			while (m.find())
				altCount++;
			if (altCount > 0)
				return altCount;

		} catch (IOException e) {
			logger.warn("Could not read tester file {}", testerPath);
		}
		return 0; // Unknown
	}

	private boolean hasSubQuestionsInPdf(String questionId, Map<String, InferredQuestionConfig> qMap,
			List<String> pdfQuestionIds) {
		String prefix = questionId.toLowerCase();
		for (String qid : pdfQuestionIds) {
			if (qid.length() > prefix.length() && qid.toLowerCase().startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	private boolean hasSubQuestionTesters(String questionId, Map<String, InferredQuestionConfig> qMap) {
		String prefix = extractQuestionPrefix(questionId).toLowerCase();
		for (InferredQuestionConfig subQc : qMap.values()) {
			String subPrefix = extractQuestionPrefix(subQc.getQuestionId()).toLowerCase();
			if (subQc.getTester() != null && !subQc.getTester().isEmpty() && subPrefix.equals(prefix)) {
				return true;
			}
		}
		return false;
	}

	private void collectAllFolders(Path dir, List<Path> folders) {
		try (Stream<Path> stream = Files.list(dir)) {
			for (Path p : stream.toList()) {
				if (Files.isDirectory(p)) {
					folders.add(p);
					collectAllFolders(p, folders);
				}
			}
		} catch (IOException e) {
			logger.warn("Could not list directory: {}", dir);
		}
	}

	private String extractQuestionPrefix(String questionId) {
		// Q1a -> Q1, Q10a -> Q10, Q3 -> Q3
		// Extract digits until non-digit: Q10a -> "10", then prepend Q
		if (questionId.length() > 1) {
			String rest = questionId.substring(1); // skip 'Q'
			int i = 0;
			while (i < rest.length() && Character.isDigit(rest.charAt(i))) {
				i++;
			}
			if (i > 0) {
				return "Q" + rest.substring(0, i);
			}
		}
		return questionId;
	}
}

package com.is442.autograder.web;

import com.is442.autograder.config.AppConfig;
import com.is442.autograder.model.QuestionConfig;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Exposes read-only configuration data to the frontend.
 */
@RestController
@RequestMapping("/api/generation/config")
public class ConfigController {

	private final AppConfig appConfig;

	public ConfigController(AppConfig appConfig) {
		this.appConfig = appConfig;
	}

	@GetMapping("/questions")
	public List<QuestionConfig> getQuestions() {
		return appConfig.getQuestionConfigs();
	}

	@GetMapping("/ai")
	public Map<String, Object> getAiSettings() {
		return Map.of("model", appConfig.getAiModel(), "maxTokens", appConfig.getAiMaxTokens(),
				"defaultCasesPerQuestion", appConfig.getAiDefaultCasesPerQuestion());
	}
}

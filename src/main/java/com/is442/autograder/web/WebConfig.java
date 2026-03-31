package com.is442.autograder.web;

import com.is442.autograder.config.AppConfig;
import com.is442.autograder.data.SessionDatabase;
import com.is442.autograder.generation.ConfigInferenceService;
import com.is442.autograder.generation.LangChainService;
import com.is442.autograder.generation.PdfParser;
import com.is442.autograder.generation.TestGenerationService;
import com.is442.autograder.generation.TesterFileWriter;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Paths;

/**
 * Registers service classes as Spring beans. The LangChainService is created
 * manually using AiServices.create() with the auto-configured ChatModel.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**").allowedOrigins("http://localhost:5173", "http://127.0.0.1:5173")
				.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
	}

	@Bean
	public AppConfig appConfig() throws IOException {
		return new AppConfig();
	}

	@Bean
	public SessionDatabase sessionDatabase() {
		return new SessionDatabase(Paths.get("data"));
	}

	@Bean
	public LangChainService langChainService(ChatModel chatModel) {
		return AiServices.create(LangChainService.class, chatModel);
	}

	@Bean
	public PdfParser pdfParser(AppConfig config) {
		return new PdfParser(config.getDoclingServeUrl());
	}

	@Bean
	public TestGenerationService testGenerationService(LangChainService langChainService,
			TesterFileWriter testerFileWriter) {
		return new TestGenerationService(langChainService, testerFileWriter);
	}

	@Bean
	public TesterFileWriter testerFileWriter() {
		return new TesterFileWriter();
	}

	@Bean
	public ConfigInferenceService configInferenceService() {
		return new ConfigInferenceService();
	}
}

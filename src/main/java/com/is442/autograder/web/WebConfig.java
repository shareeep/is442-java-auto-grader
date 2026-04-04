package com.is442.autograder.web;

import com.is442.autograder.config.AppConfig;
import com.is442.autograder.data.SessionDatabase;
import com.is442.autograder.generation.ConfigInferenceService;
import com.is442.autograder.generation.LangChainService;
import com.is442.autograder.generation.PdfParser;
import com.is442.autograder.generation.TestGenerationService;
import com.is442.autograder.generation.TesterFileWriter;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;

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

	/**
	 * Vision model service — backed by the auto-configured ChatModel
	 * (xiaomi/mimo-v2-omni).
	 */
	@Bean
	@Qualifier("vision")
	public LangChainService langChainServiceVision(ChatModel chatModel) {
		return AiServices.create(LangChainService.class, chatModel);
	}

	/**
	 * Text-only model service — backed by MiniMax, used when no images are present.
	 */
	@Bean
	@Qualifier("text")
	public LangChainService langChainServiceText(AppConfig appConfig) {
		String apiKey = System.getenv("OPENROUTER_API_KEY");
		ChatModel textModel = OpenAiChatModel.builder().apiKey(apiKey != null ? apiKey : "no-key")
				.baseUrl("https://openrouter.ai/api/v1").modelName(appConfig.getAiTextModel())
				.maxTokens(appConfig.getAiMaxTokens()).timeout(Duration.ofSeconds(180)).build();
		return AiServices.create(LangChainService.class, textModel);
	}

	@Bean
	public PdfParser pdfParser(AppConfig config) {
		return new PdfParser(config.getDoclingServeUrl());
	}

	@Bean
	public TestGenerationService testGenerationService(@Qualifier("vision") LangChainService langChainServiceVision,
			@Qualifier("text") LangChainService langChainServiceText, TesterFileWriter testerFileWriter) {
		return new TestGenerationService(langChainServiceVision, langChainServiceText, testerFileWriter);
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

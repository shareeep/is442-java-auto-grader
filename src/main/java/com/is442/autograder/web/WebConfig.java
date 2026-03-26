package com.is442.autograder.web;

import com.is442.autograder.config.AppConfig;
import com.is442.autograder.generation.TestGenerationService;
import com.is442.autograder.generation.TesterFileWriter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;

/**
 * Registers existing service classes as Spring beans without modifying their
 * source code (no @Component / @Service annotations added).
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
	public TestGenerationService testGenerationService(AppConfig config) {
		return new TestGenerationService(config);
	}

	@Bean
	public TesterFileWriter testerFileWriter() {
		return new TesterFileWriter();
	}
}

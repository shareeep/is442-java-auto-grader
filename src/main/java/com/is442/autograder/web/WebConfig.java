package com.is442.autograder.web;

import com.is442.autograder.config.AppConfig;
import com.is442.autograder.generation.TestGenerationService;
import com.is442.autograder.generation.TesterFileWriter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Registers existing service classes as Spring beans without modifying their
 * source code (no @Component / @Service annotations added).
 */
@Configuration
public class WebConfig {

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

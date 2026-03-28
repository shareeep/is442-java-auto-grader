package com.is442.autograder.web;

import com.is442.autograder.config.EnvLoader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the web UI mode. Started only when the user
 * passes {@code --web} to the main app, or via {@code ./gradlew bootRun}.
 */
@SpringBootApplication
public class WebApplication {

	public static void main(String[] args) {
		EnvLoader.load();
		SpringApplication.run(WebApplication.class, args);
	}
}

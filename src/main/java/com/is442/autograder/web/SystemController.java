package com.is442.autograder.web;

import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {

	public static final String BOOT_ID = UUID.randomUUID().toString();

	@GetMapping("/boot-id")
	public Map<String, String> getBootId() {
		return Map.of("bootId", BOOT_ID);
	}
}

package com.is442.autograder.generation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.api.convert.request.options.ConvertDocumentOptions;
import ai.docling.serve.api.convert.request.ConvertDocumentRequest;
import ai.docling.serve.api.convert.request.options.ImageRefMode;
import ai.docling.serve.api.convert.request.options.OutputFormat;
import ai.docling.serve.api.convert.request.source.FileSource;
import ai.docling.serve.api.convert.request.target.InBodyTarget;
import ai.docling.serve.api.convert.response.InBodyConvertDocumentResponse;

/**
 * Extracts structured markdown from exam PDF files using Docling Serve.
 * Preserves tables, code blocks, and images dynamically.
 */
public class PdfParser {

	private static final Logger logger = LoggerFactory.getLogger(PdfParser.class);
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(120);
	private static final String DYNAMIC_FILENAME_PREFIX = "exam_";
	private static final String PDF_EXTENSION = ".pdf";
	private static final DateTimeFormatter DYNAMIC_FILENAME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
	private final DoclingServeApi api;
	private final String doclingServeUrl;

	public PdfParser(String doclingServeUrl) {
		this.doclingServeUrl = doclingServeUrl;
		this.api = DoclingServeApi.builder().baseUrl(doclingServeUrl).connectTimeout(CONNECT_TIMEOUT)
				.readTimeout(READ_TIMEOUT).build();
	}

	/**
	 * Extract all text from the PDF as structured Markdown using Docling Serve.
	 *
	 * @param pdfPath
	 *            path to the PDF file
	 * @return markdown representation of the entire PDF
	 */
	public String extractAllText(Path pdfPath) throws IOException {
		byte[] pdfBytes = Files.readAllBytes(pdfPath);
		logger.info("[PDF] Sending PDF to Docling  file={} bytes={} url={}", pdfPath.getFileName(), pdfBytes.length,
				doclingServeUrl);
		String dynamicFilename = buildDynamicFilename();

		ConvertDocumentRequest request = buildConvertRequest(pdfBytes, dynamicFilename);

		Object rawResponse = api.convertSource(request);
		String markdown = extractMarkdown(rawResponse);
		long embeddedImageCount = extractBase64Images(markdown).size();
		logger.info("[PDF] Docling conversion complete  file={} markdownChars={} embeddedImages={}",
				pdfPath.getFileName(), markdown.length(), embeddedImageCount);
		if (embeddedImageCount == 0 && markdown.contains("![")) {
			// EMBEDDED mode may not produce data URIs — log a sample of image refs found
			Matcher sampleMatcher = Pattern.compile("!\\[[^\\]]*\\]\\([^)]+\\)").matcher(markdown);
			if (sampleMatcher.find()) {
				logger.info("[PDF] Image ref sample (not base64): {}", sampleMatcher.group().substring(0, Math.min(120, sampleMatcher.group().length())));
			}
		}
		return markdown;
	}

	/**
	 * Extracts a question-specific section from the PDF using Header matching.
	 *
	 * @param pdfPath
	 *            path to the PDF file
	 * @param questionId
	 *            e.g. "Q1a"
	 * @return extracted section markdown, or full markdown if section not found
	 */
	public String extractQuestionSection(Path pdfPath, String questionId) throws IOException {
		String allMd = extractAllText(pdfPath);
		return extractQuestionSectionFromMarkdown(allMd, questionId);
	}

	/**
	 * Extract question section from already-parsed markdown (avoids re-parsing).
	 */
	public String extractQuestionSectionFromMarkdown(String fullMarkdown, String questionId) {
		// Strip Q prefix: Q2a → 2a, Q10b → 10b
		String fullId = questionId.replace("Q", "");
		// Parent-only id: 2a → 2, 10b → 10
		String parentId = fullId.replaceAll("[a-z]$", "");
		boolean hasSub = !fullId.equals(parentId);

		// 1. Try exact match first (e.g. ## Question 2a — with word boundary to avoid
		// 2a matching 2ab)
		Pattern exactPattern = Pattern.compile(
				String.format("(?i)(##\\s*Question\\s*%s)\\b(.*?)(?=\\n##\\s*Question|\\Z)", Pattern.quote(fullId)),
				Pattern.DOTALL);
		Matcher exactMatcher = exactPattern.matcher(fullMarkdown);
		if (exactMatcher.find()) {
			String section = exactMatcher.group(1) + exactMatcher.group(2).trim();
			logger.debug("[PDF] Extracted exact section  questionId={} chars={}", questionId, section.length());
			return section;
		}

		// 2. Fall back to parent section (e.g. ## Question 2) only if this is a
		// sub-question
		if (hasSub) {
			Pattern parentPattern = Pattern.compile(String
					.format("(?i)(##\\s*Question\\s*%s)\\b(.*?)(?=\\n##\\s*Question|\\Z)", Pattern.quote(parentId)),
					Pattern.DOTALL);
			Matcher parentMatcher = parentPattern.matcher(fullMarkdown);
			if (parentMatcher.find()) {
				String section = parentMatcher.group(1) + parentMatcher.group(2).trim();
				logger.debug("[PDF] Extracted parent section for {}  chars={}", questionId, section.length());
				return section;
			}
		}

		logger.warn("[PDF] Section header not found for {}  falling back to full markdown ({} chars)", questionId,
				fullMarkdown.length());
		return fullMarkdown;
	}

	private String buildDynamicFilename() {
		return DYNAMIC_FILENAME_PREFIX + LocalDateTime.now().format(DYNAMIC_FILENAME_FORMAT) + PDF_EXTENSION;
	}

	private ConvertDocumentRequest buildConvertRequest(byte[] pdfBytes, String dynamicFilename) {
		return ConvertDocumentRequest.builder()
				.source(FileSource.builder().base64String(Base64.getEncoder().encodeToString(pdfBytes))
						.filename(dynamicFilename).build())
				.options(ConvertDocumentOptions.builder().toFormat(OutputFormat.MARKDOWN)
						.imageExportMode(ImageRefMode.EMBEDDED).includeImages(true).doOcr(true).build())
				.target(InBodyTarget.builder().build()).build();
	}

	// ── Static image helpers (used by TestGenerationService) ────────────────

	private static final Pattern IMAGE_DATA_URI_PATTERN = Pattern
			.compile("!\\[[^\\]]*\\]\\((data:image/[^;]+;base64,[A-Za-z0-9+/=]+)\\)");

	/**
	 * Extract all base64 image data URIs embedded in the markdown (from EMBEDDED
	 * mode). Returns a list of "data:image/png;base64,..." strings.
	 */
	public static List<String> extractBase64Images(String markdown) {
		List<String> images = new ArrayList<>();
		Matcher m = IMAGE_DATA_URI_PATTERN.matcher(markdown);
		while (m.find()) {
			images.add(m.group(1));
		}
		return images;
	}

	/**
	 * Strip embedded base64 data URIs from markdown, replacing each with a
	 * [diagram N] placeholder. Keeps the markdown readable for the text portion of
	 * the LLM prompt.
	 */
	public static String stripInlineImages(String markdown) {
		int[] counter = { 1 };
		return IMAGE_DATA_URI_PATTERN.matcher(markdown).replaceAll(mr -> {
			int n = counter[0]++;
			return "![diagram " + n + "](diagram_" + n + ")";
		});
	}

	private String extractMarkdown(Object rawResponse) throws IOException {
		if (rawResponse == null) {
			throw new IOException(
					"Docling Serve returned no response — check that the service is running at " + doclingServeUrl);
		}
		if (!(rawResponse instanceof InBodyConvertDocumentResponse response)) {
			throw new IOException(
					"Unexpected response type from Docling Serve: " + rawResponse.getClass().getSimpleName());
		}
		if (response.getDocument() == null || response.getDocument().getMarkdownContent() == null) {
			throw new IOException("Docling Serve returned a response but no markdown content was present.");
		}
		return response.getDocument().getMarkdownContent();
	}
}

package com.is442.autograder.generation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
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
	private static final Pattern SECTION_PATTERN_TEMPLATE = Pattern
			.compile("(?i)(##\\s*Question\\s*%s)(.*?)(?=\\n##\\s*Question|\\Z)", Pattern.DOTALL);

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
		logger.info("[PDF] Docling conversion complete  file={} markdownChars={}", pdfPath.getFileName(),
				markdown.length());
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
		String normalizedId = questionId.replace("Q", "").replaceAll("[a-z]$", "");
		Pattern p = Pattern.compile(String.format(SECTION_PATTERN_TEMPLATE.pattern(), normalizedId), Pattern.DOTALL);
		Matcher m = p.matcher(fullMarkdown);

		if (m.find()) {
			String section = m.group(1) + m.group(2).trim();
			logger.debug("[PDF] Extracted section  questionId={} chars={}", questionId, section.length());
			return section;
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
						.imageExportMode(ImageRefMode.REFERENCED).includeImages(true).doOcr(true).build())
				.target(InBodyTarget.builder().build()).build();
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

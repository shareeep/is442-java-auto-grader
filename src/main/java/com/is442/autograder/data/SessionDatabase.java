package com.is442.autograder.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SessionDatabase {

	private static final Logger logger = LoggerFactory.getLogger(SessionDatabase.class);
	private static final String INSERT_EXAM_SQL = "INSERT INTO exams (id, original_filename, parsed_markdown, created_at) VALUES (?, ?, ?, ?)";
	private static final String UPSERT_EXAM_SQL = "INSERT OR REPLACE INTO exams (id, original_filename, parsed_markdown, created_at) VALUES (?, ?, ?, ?)";
	private static final String UPSERT_QUESTION_SQL = """
			INSERT OR REPLACE INTO questions (exam_id, question_id, markdown, folder, tester, max_score, inferred_from_pdf)
			VALUES (?, ?, ?, ?, ?, ?, ?)
			""";
	private static final String SELECT_QUESTIONS_SQL = """
			SELECT question_id, markdown, folder, tester, max_score, inferred_from_pdf
			FROM questions WHERE exam_id = ?
			""";
	private static final String SELECT_QUESTION_SQL = """
			SELECT question_id, markdown, folder, tester, max_score, inferred_from_pdf
			FROM questions WHERE exam_id = ? AND question_id = ?
			""";
	private static final String UPSERT_CONFIG_SQL = """
			INSERT OR REPLACE INTO exam_configs (exam_id, config_json, updated_at)
			VALUES (?, ?, ?)
			""";
	private static final String SELECT_CONFIG_SQL = """
			SELECT config_json FROM exam_configs WHERE exam_id = ? ORDER BY updated_at DESC LIMIT 1
			""";

	private final String dbPath;

	public SessionDatabase(Path dataDir) {
		try {
			Files.createDirectories(dataDir);
		} catch (IOException e) {
			logger.warn("[DB] Could not create data dir, using fallback: {}", e.getMessage());
		}
		this.dbPath = dataDir.resolve("session.db").toString();
		initialize();
	}

	private Connection getConnection() throws SQLException {
		return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
	}

	private void initialize() {
		try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS exams (
					    id TEXT PRIMARY KEY,
					    original_filename TEXT,
					    parsed_markdown TEXT,
					    created_at INTEGER
					)
					""");

			stmt.execute("""
					CREATE TABLE IF NOT EXISTS questions (
					    id INTEGER PRIMARY KEY AUTOINCREMENT,
					    exam_id TEXT NOT NULL,
					    question_id TEXT NOT NULL,
					    markdown TEXT,
					    folder TEXT,
					    tester TEXT,
					    max_score REAL,
					    inferred_from_pdf INTEGER DEFAULT 0,
					    FOREIGN KEY (exam_id) REFERENCES exams(id)
					)
					""");

			stmt.execute("""
					CREATE TABLE IF NOT EXISTS exam_configs (
					    id INTEGER PRIMARY KEY AUTOINCREMENT,
					    exam_id TEXT NOT NULL,
					    config_json TEXT,
					    updated_at INTEGER,
					    FOREIGN KEY (exam_id) REFERENCES exams(id)
					)
					""");

			logger.info("[DB] Session database initialized at {}", dbPath);
		} catch (SQLException e) {
			logger.error("[DB] Failed to initialize database", e);
			throw new RuntimeException("DB init failed", e);
		}
	}

	public String createExam(String originalFilename, String parsedMarkdown) {
		String examId = UUID.randomUUID().toString();
		try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(INSERT_EXAM_SQL)) {
			ps.setString(1, examId);
			ps.setString(2, originalFilename);
			ps.setString(3, parsedMarkdown);
			ps.setLong(4, System.currentTimeMillis());
			ps.executeUpdate();
			logger.info("[DB] Created exam {} with filename {}", examId, originalFilename);
		} catch (SQLException e) {
			logger.error("[DB] Failed to create exam", e);
		}
		return examId;
	}

	public void saveParsedExam(String examId, String originalFilename, String parsedMarkdown) {
		try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(UPSERT_EXAM_SQL)) {
			ps.setString(1, examId);
			ps.setString(2, originalFilename);
			ps.setString(3, parsedMarkdown);
			ps.setLong(4, System.currentTimeMillis());
			ps.executeUpdate();
			logger.info("[DB] Saved parsed exam {}", examId);
		} catch (SQLException e) {
			logger.error("[DB] Failed to save parsed exam", e);
		}
	}

	public void saveQuestion(String examId, String questionId, String markdown, String folder, String tester,
			double maxScore, boolean inferredFromPdf) {
		try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(UPSERT_QUESTION_SQL)) {
			ps.setString(1, examId);
			ps.setString(2, questionId);
			ps.setString(3, markdown);
			ps.setString(4, folder);
			ps.setString(5, tester);
			ps.setDouble(6, maxScore);
			ps.setInt(7, inferredFromPdf ? 1 : 0);
			ps.executeUpdate();
			logger.info("[DB] Saved question {} for exam {}", questionId, examId);
		} catch (SQLException e) {
			logger.error("[DB] Failed to save question", e);
		}
	}

	public String getParsedMarkdown(String examId) {
		try (Connection conn = getConnection();
				PreparedStatement ps = conn.prepareStatement("SELECT parsed_markdown FROM exams WHERE id = ?")) {
			ps.setString(1, examId);
			ResultSet rs = ps.executeQuery();
			if (rs.next()) {
				return rs.getString("parsed_markdown");
			}
		} catch (SQLException e) {
			logger.error("[DB] Failed to get parsed markdown", e);
		}
		return null;
	}

	public List<QuestionEntry> getQuestions(String examId) {
		List<QuestionEntry> results = new ArrayList<>();
		try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(SELECT_QUESTIONS_SQL)) {
			ps.setString(1, examId);
			ResultSet rs = ps.executeQuery();
			while (rs.next()) {
				results.add(toQuestionEntry(rs));
			}
		} catch (SQLException e) {
			logger.error("[DB] Failed to get questions", e);
		}
		return results;
	}

	public QuestionEntry getQuestion(String examId, String questionId) {
		try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(SELECT_QUESTION_SQL)) {
			ps.setString(1, examId);
			ps.setString(2, questionId);
			ResultSet rs = ps.executeQuery();
			if (rs.next()) {
				return toQuestionEntry(rs);
			}
		} catch (SQLException e) {
			logger.error("[DB] Failed to get question", e);
		}
		return null;
	}

	public void saveConfig(String examId, String configJson) {
		try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(UPSERT_CONFIG_SQL)) {
			ps.setString(1, examId);
			ps.setString(2, configJson);
			ps.setLong(3, System.currentTimeMillis());
			ps.executeUpdate();
			logger.info("[DB] Saved config for exam {}", examId);
		} catch (SQLException e) {
			logger.error("[DB] Failed to save config", e);
		}
	}

	public String getConfig(String examId) {
		try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(SELECT_CONFIG_SQL)) {
			ps.setString(1, examId);
			ResultSet rs = ps.executeQuery();
			if (rs.next()) {
				return rs.getString("config_json");
			}
		} catch (SQLException e) {
			logger.error("[DB] Failed to get config", e);
		}
		return null;
	}

	public void deleteExam(String examId) {
		try (Connection conn = getConnection()) {
			conn.prepareStatement("DELETE FROM questions WHERE exam_id = ?").executeUpdate();
			conn.prepareStatement("DELETE FROM exam_configs WHERE exam_id = ?").executeUpdate();
			conn.prepareStatement("DELETE FROM exams WHERE id = ?").executeUpdate();
			logger.info("[DB] Deleted exam {}", examId);
		} catch (SQLException e) {
			logger.error("[DB] Failed to delete exam", e);
		}
	}

	private QuestionEntry toQuestionEntry(ResultSet rs) throws SQLException {
		return new QuestionEntry(rs.getString("question_id"), rs.getString("markdown"), rs.getString("folder"),
				rs.getString("tester"), rs.getDouble("max_score"), rs.getInt("inferred_from_pdf") == 1);
	}

	public record QuestionEntry(String questionId, String markdown, String folder, String tester, double maxScore,
			boolean inferredFromPdf) {
	}
}

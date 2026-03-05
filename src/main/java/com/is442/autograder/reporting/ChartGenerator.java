package com.is442.autograder.reporting;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.data.category.DefaultCategoryDataset;

import com.is442.autograder.model.Anomaly;
import com.is442.autograder.model.QuestionConfig;
import com.is442.autograder.model.StudentSubmission;

/**
 * Generates chart images (as PNG byte arrays) for embedding in the PDF report.
 * All public methods return raw PNG bytes so callers do not need temporary
 * files.
 */
public final class ChartGenerator {

	private ChartGenerator() {
	}

	// ── colours ─────────────────────────────────────────────────────────────
	private static final Color BLUE = new Color(52, 120, 200);
	private static final Color GREEN = new Color(46, 160, 90);
	private static final Color RED = new Color(210, 80, 60);
	private static final Color GRID = new Color(220, 220, 220);

	// ── public chart builders ────────────────────────────────────────────────

	/** Score distribution bar chart (vertical). */
	public static byte[] scoreDistributionChart(List<StudentSubmission> subs, double maxPossible) {
		DefaultCategoryDataset ds = new DefaultCategoryDataset();
		int binWidth = Math.max(1, (int) Math.ceil(maxPossible / 5.0));
		for (int lo = 0; lo < (int) maxPossible; lo += binWidth) {
			int hi = (int) Math.min(lo + binWidth - 1, (int) maxPossible - 1);
			final double flo = lo;
			final double fhi = hi;
			long cnt = subs.stream().filter(s -> s.getTotalScore() >= flo && s.getTotalScore() <= fhi).count();
			String label = (lo == hi) ? String.valueOf(lo) : lo + "–" + hi;
			ds.addValue(cnt, "Students", label);
		}
		long perfect = subs.stream().filter(s -> s.getTotalScore() >= maxPossible).count();
		ds.addValue(perfect, "Students", (int) maxPossible + " (full)");

		JFreeChart chart = ChartFactory.createBarChart("Score Distribution", "Score Range", "Students", ds,
				PlotOrientation.VERTICAL, false, false, false);
		applyStyle(chart, BLUE);
		return toPng(chart, 560, 300);
	}

	/** Pass rate per question horizontal bar chart (%). */
	public static byte[] passRateChart(List<StudentSubmission> subs, List<QuestionConfig> qcs) {
		DefaultCategoryDataset ds = new DefaultCategoryDataset();
		int total = subs.size();
		for (QuestionConfig qc : qcs) {
			long passed = subs.stream().flatMap(s -> s.getResults().stream())
					.filter(r -> r.getQuestionId().equals(qc.getQuestionId()) && r.getScore() >= r.getMaxScore())
					.count();
			double pct = total == 0 ? 0 : (double) passed / total * 100.0;
			ds.addValue(pct, "Pass Rate", qc.getQuestionId());
		}
		JFreeChart chart = ChartFactory.createBarChart("Full-Pass Rate by Question", "Question", "Pass Rate (%)", ds,
				PlotOrientation.HORIZONTAL, false, true, false);
		applyStyle(chart, GREEN);
		CategoryPlot greenPlot = (CategoryPlot) chart.getPlot();
		greenPlot.getRangeAxis().setRange(0, 100);
		greenPlot.getDomainAxis().setMaximumCategoryLabelLines(2);
		return toPng(chart, 600, 280);
	}

	/** Anomaly frequency horizontal bar chart (top 8 types). */
	public static byte[] anomalyFrequencyChart(Map<Anomaly.Type, Long> typeCounts) {
		DefaultCategoryDataset ds = new DefaultCategoryDataset();
		typeCounts.entrySet().stream().sorted(Map.Entry.<Anomaly.Type, Long>comparingByValue().reversed()).limit(8)
				.forEach(e -> ds.addValue(e.getValue(), "Occurrences", friendlyName(e.getKey())));
		JFreeChart chart = ChartFactory.createBarChart("Anomaly Frequency", "Issue Type", "Occurrences", ds,
				PlotOrientation.HORIZONTAL, false, true, false);
		applyStyle(chart, RED);
		CategoryPlot redPlot = (CategoryPlot) chart.getPlot();
		redPlot.getDomainAxis().setMaximumCategoryLabelLines(2);
		return toPng(chart, 600, 340);
	}

	// ── helpers ──────────────────────────────────────────────────────────────

	private static void applyStyle(JFreeChart chart, Color barColor) {
		chart.setBackgroundPaint(Color.WHITE);
		chart.setBorderVisible(false);

		CategoryPlot plot = (CategoryPlot) chart.getPlot();
		plot.setBackgroundPaint(Color.WHITE);
		plot.setDomainGridlinesVisible(false);
		plot.setRangeGridlinePaint(GRID);
		plot.setRangeGridlineStroke(new BasicStroke(0.8f));
		plot.setOutlineVisible(false);

		BarRenderer renderer = (BarRenderer) plot.getRenderer();
		renderer.setBarPainter(new StandardBarPainter());
		renderer.setSeriesPaint(0, barColor);
		renderer.setDrawBarOutline(false);
		renderer.setShadowVisible(false);
		renderer.setMaximumBarWidth(0.5);

		NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
		rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
	}

	private static byte[] toPng(JFreeChart chart, int width, int height) {
		BufferedImage img = chart.createBufferedImage(width, height);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			ImageIO.write(img, "PNG", out);
		} catch (IOException e) {
			throw new RuntimeException("Chart PNG generation failed", e);
		}
		return out.toByteArray();
	}

	static String friendlyName(Anomaly.Type type) {
		return switch (type) {
			case FOLDER_NOT_RENAMED -> "Folder Not Renamed";
			case STUDENT_ID_AS_FOLDER -> "Student ID as Folder";
			case NO_PARENT_FOLDER -> "Missing Parent Folder";
			case EXTRA_NESTING -> "Extra Folder Nesting";
			case MISSING_QUESTION_FOLDER -> "Missing Question Folder";
			case MISSING_JAVA_FILE -> "Missing Java File";
			case IDENTITY_MISMATCH -> "Name/Identity Mismatch";
			case MISSING_HEADER -> "Missing Name/Email Header";
			case INCOMPLETE_HEADER -> "Incomplete Name/Email Header";
			case COMPILATION_ERROR -> "Compilation Error";
			case RUNTIME_ERROR -> "Runtime Error";
			case EXECUTION_TIMEOUT -> "Execution Timeout";
		};
	}
}

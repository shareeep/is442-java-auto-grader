package com.is442.autograder.reporting;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Captures console output to a run-level log file.
 */
public class ConsoleLogCapture implements AutoCloseable {

	private final PrintStream originalOut;
	private final PrintStream originalErr;
	private final PrintStream teeOut;
	private final PrintStream teeErr;
	private final PrintStream fileStream;

	private ConsoleLogCapture(PrintStream originalOut, PrintStream originalErr, PrintStream teeOut, PrintStream teeErr,
			PrintStream fileStream) {
		this.originalOut = originalOut;
		this.originalErr = originalErr;
		this.teeOut = teeOut;
		this.teeErr = teeErr;
		this.fileStream = fileStream;
	}

	public static ConsoleLogCapture start(Path outputDir) throws IOException {
		Files.createDirectories(outputDir);
		Path logsDir = outputDir.resolve("logs");
		Files.createDirectories(logsDir);
		Path logFile = logsDir.resolve("run.log");
		OutputStream fileOut = Files.newOutputStream(logFile);
		PrintStream filePrint = new PrintStream(fileOut, true);

		PrintStream originalOut = System.out;
		PrintStream originalErr = System.err;

		PrintStream teeOut = new PrintStream(new TeeOutputStream(originalOut, filePrint), true);
		PrintStream teeErr = new PrintStream(new TeeOutputStream(originalErr, filePrint), true);

		System.setOut(teeOut);
		System.setErr(teeErr);

		filePrint.println("Run started: " + ReportLogSupport.timestampNow());
		filePrint.println();

		return new ConsoleLogCapture(originalOut, originalErr, teeOut, teeErr, filePrint);
	}

	@Override
	public void close() {
		try {
			fileStream.println();
			fileStream.println("Run ended: " + ReportLogSupport.timestampNow());
			fileStream.flush();
		} finally {
			System.setOut(originalOut);
			System.setErr(originalErr);
			teeOut.flush();
			teeErr.flush();
			fileStream.close();
		}
	}

	private static class TeeOutputStream extends OutputStream {
		private final OutputStream left;
		private final OutputStream right;

		private TeeOutputStream(OutputStream left, OutputStream right) {
			this.left = left;
			this.right = right;
		}

		@Override
		public void write(int b) throws IOException {
			left.write(b);
			right.write(b);
		}

		@Override
		public void write(byte[] b) throws IOException {
			left.write(b);
			right.write(b);
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			left.write(b, off, len);
			right.write(b, off, len);
		}

		@Override
		public void flush() throws IOException {
			left.flush();
			right.flush();
		}

		@Override
		public void close() throws IOException {
			left.close();
			right.close();
		}
	}
}

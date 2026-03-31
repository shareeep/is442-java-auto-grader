package com.is442.autograder.extraction;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Safely extracts ZIP archives with protection against: - Zip bombs (max total
 * size, max entries) - Path traversal attacks (.. in entry names) - Symbolic
 * links
 */
public class ZipExtractor {

	private static final long MAX_TOTAL_SIZE = 100 * 1024 * 1024; // 100 MB
	private static final int MAX_ENTRIES = 1000;
	private static final int MAX_PATH_LENGTH = 255;
	private static final int BUFFER_SIZE = 8192;
	private static final String MAC_METADATA_PREFIX = "__MACOSX";
	private static final String DS_STORE = ".DS_Store";

	/**
	 * Extract a ZIP file to the given target directory.
	 *
	 * @param zipFile
	 *            path to the .zip file
	 * @param targetDir
	 *            base directory to extract into
	 * @return path to the extracted content root
	 * @throws IOException
	 *             if an I/O error occurs
	 * @throws SecurityException
	 *             if a security issue is detected
	 */
	public Path extract(Path zipFile, Path targetDir) throws IOException, SecurityException {
		Files.createDirectories(targetDir);

		long totalSize = 0;
		int entryCount = 0;

		try (InputStream fis = Files.newInputStream(zipFile); ZipInputStream zis = new ZipInputStream(fis)) {

			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				entryCount++;

				// 1. Entry count limit (zip bomb protection)
				if (entryCount > MAX_ENTRIES) {
					throw new SecurityException("Too many entries in archive (>" + MAX_ENTRIES + ")");
				}

				String entryName = entry.getName();

				// Skip macOS metadata
				if (isMacMetadata(entryName)) {
					zis.closeEntry();
					continue;
				}

				// 2. Validate entry name
				if (!isSafeEntryName(entryName)) {
					zis.closeEntry();
					continue;
				}

				// 3. Resolve and verify target path
				Path destPath = targetDir.resolve(entryName).normalize();
				if (!isWithinTargetDir(destPath, targetDir)) {
					zis.closeEntry();
					continue;
				}

				// 4. Extract, counting actual bytes written for zip bomb protection
				totalSize = extractEntry(zis, entry, destPath, totalSize);

				zis.closeEntry();
			}
		}

		return targetDir;
	}

	private boolean isMacMetadata(String entryName) {
		return entryName.startsWith(MAC_METADATA_PREFIX) || entryName.contains(DS_STORE);
	}

	private boolean isWithinTargetDir(Path destination, Path targetDir) {
		return destination.startsWith(targetDir);
	}

	private long extractEntry(ZipInputStream zis, ZipEntry entry, Path destPath, long currentSize) throws IOException {
		if (entry.isDirectory()) {
			Files.createDirectories(destPath);
			return currentSize;
		}

		Files.createDirectories(destPath.getParent());
		byte[] buffer = new byte[BUFFER_SIZE];
		int read;
		long totalSize = currentSize;
		try (java.io.OutputStream out = Files.newOutputStream(destPath)) {
			while ((read = zis.read(buffer)) != -1) {
				totalSize += read;
				if (totalSize > MAX_TOTAL_SIZE) {
					throw new SecurityException(
							"Archive too large (exceeds " + (MAX_TOTAL_SIZE / 1024 / 1024) + " MB)");
				}
				out.write(buffer, 0, read);
			}
		}
		return totalSize;
	}

	private boolean isSafeEntryName(String name) {
		if (name.contains("..")) {
			return false;
		}
		if (name.startsWith("/") || name.startsWith("\\")) {
			return false;
		}
		if (name.length() > MAX_PATH_LENGTH) {
			return false;
		}
		if (name.contains("\0")) {
			return false;
		}
		return true;
	}
}

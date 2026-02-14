package com.is442.autograder.util;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * File operation utilities used across the grading pipeline.
 */
public class FileUtils {

    private FileUtils() {
    } // utility class

    /**
     * Recursively copy a directory tree from source to target.
     */
    public static void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Recursively delete a directory and all its contents.
     */
    public static void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir))
            return;
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Find all files with a given extension in a directory (non-recursive).
     */
    public static List<Path> findFilesByExtension(Path dir, String extension) throws IOException {
        List<Path> result = new ArrayList<>();
        if (!Files.isDirectory(dir))
            return result;
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith("." + extension))
                    .forEach(result::add);
        }
        return result;
    }

    /**
     * Find a specific file by name anywhere under a directory (recursive).
     */
    public static Path findFile(Path dir, String fileName) throws IOException {
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream
                    .filter(p -> Files.isRegularFile(p) && p.getFileName().toString().equals(fileName))
                    .findFirst()
                    .orElse(null);
        }
    }

    /**
     * Find a specific directory by name anywhere under a root (recursive).
     */
    public static Path findDirectory(Path root, String dirName) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(p -> Files.isDirectory(p) && p.getFileName().toString().equals(dirName))
                    .findFirst()
                    .orElse(null);
        }
    }

    /**
     * Create a temp directory for grading work.
     */
    public static Path createTempDir(String prefix) throws IOException {
        return Files.createTempDirectory(prefix);
    }
}

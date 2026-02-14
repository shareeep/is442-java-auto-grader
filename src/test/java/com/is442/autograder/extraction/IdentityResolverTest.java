package com.is442.autograder.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IdentityResolverTest {

    private final IdentityResolver resolver = new IdentityResolver();

    // --- deriveNameFromUsername ---

    @Test
    void deriveNameFromUsername_standardFormat() {
        assertEquals("Ping Lee", resolver.deriveNameFromUsername("ping.lee.2023"));
    }

    @Test
    void deriveNameFromUsername_noYear() {
        assertEquals("Ping Lee", resolver.deriveNameFromUsername("ping.lee"));
    }

    @Test
    void deriveNameFromUsername_singleName() {
        assertEquals("David", resolver.deriveNameFromUsername("david.2024"));
    }

    @Test
    void deriveNameFromUsername_allLowercase() {
        assertEquals("Xing Yan", resolver.deriveNameFromUsername("xing.yan.2023"));
    }

    @Test
    void deriveNameFromUsername_emptyString() {
        assertEquals("", resolver.deriveNameFromUsername(""));
    }

    @Test
    void deriveNameFromUsername_null() {
        assertEquals("", resolver.deriveNameFromUsername(null));
    }

    // --- toTitleCase ---

    @Test
    void toTitleCase_allUpper() {
        assertEquals("Ping Lee", IdentityResolver.toTitleCase("PING LEE"));
    }

    @Test
    void toTitleCase_allLower() {
        assertEquals("Yan Xing", IdentityResolver.toTitleCase("yan xing"));
    }

    @Test
    void toTitleCase_mixedCase() {
        assertEquals("Yan Xing", IdentityResolver.toTitleCase("Yan xing"));
    }

    @Test
    void toTitleCase_singleWord() {
        assertEquals("David", IdentityResolver.toTitleCase("david"));
    }

    @Test
    void toTitleCase_empty() {
        assertEquals("", IdentityResolver.toTitleCase(""));
    }

    @Test
    void toTitleCase_null() {
        assertEquals(null, IdentityResolver.toTitleCase(null));
    }

    // --- resolveFromFile ---

    @Test
    void resolveFromFile_validHeader(@TempDir Path tempDir) throws IOException {
        Path javaFile = tempDir.resolve("Q1a.java");
        Files.writeString(
                javaFile,
                """
                        /*
                         * Name: Ping Lee
                         * Email ID: ping.lee.2023
                         */
                        public class Q1a {}
                        """);

        Optional<com.is442.autograder.model.StudentIdentity> identity = resolver.resolveFromFile(javaFile);

        assertTrue(identity.isPresent());
        assertEquals("Ping Lee", identity.get().getName());
        assertEquals("ping.lee.2023", identity.get().getEmailId());
    }

    @Test
    void resolveFromFile_missingHeader(@TempDir Path tempDir) throws IOException {
        Path javaFile = tempDir.resolve("Q1a.java");
        Files.writeString(javaFile, "public class Q1a {}");

        Optional<com.is442.autograder.model.StudentIdentity> identity = resolver.resolveFromFile(javaFile);

        assertTrue(identity.isEmpty());
    }

    @Test
    void resolveFromFile_titleCasesName(@TempDir Path tempDir) throws IOException {
        Path javaFile = tempDir.resolve("Q1a.java");
        Files.writeString(
                javaFile,
                """
                        /*
                         * Name: yan xing
                         * Email ID: xing.yan.2023
                         */
                        public class Q1a {}
                        """);

        Optional<com.is442.autograder.model.StudentIdentity> identity = resolver.resolveFromFile(javaFile);

        assertTrue(identity.isPresent());
        assertEquals("Yan Xing", identity.get().getName());
    }
}

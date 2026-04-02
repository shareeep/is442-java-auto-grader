package com.is442.autograder.execution;

import java.io.File;
import java.util.Set;
import java.nio.file.Path;

import de.jplag.JPlag;
import de.jplag.JPlagResult;
import de.jplag.options.JPlagOptions;
import de.jplag.java.JavaLanguage;
import de.jplag.reporting.reportobject.ReportObjectFactory;
import de.jplag.exceptions.ExitException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlagiarismChecker {

    private static final Logger logger = LoggerFactory.getLogger(PlagiarismChecker.class);

    /**
     * Run JPlag plagiarism checks on the collected submission files.
     *
     * @param submissionsCodeDir The directory containing all the extracted student Java files 
     *                           (e.g., each subdirectory is a student's submission).
     * @param templateDir        The base code/template directory to ignore boilerplate code.
     * @param outputReportDir    The directory where the .jplag report will be generated.
     */
    public void runChecks(Path submissionsCodeDir, Path templateDir, Path outputReportDir) {
        logger.info("Starting JPlag plagiarism checks on {}", submissionsCodeDir);
        
        try {
            JavaLanguage language = new JavaLanguage();
            
            // JPlag options expects Set<File> for root submissions and for old submissions
            Set<File> submissionDirectories = Set.of(submissionsCodeDir.toFile());
            
            JPlagOptions options = new JPlagOptions(language, submissionDirectories, Set.of());
            
            if (templateDir != null && templateDir.toFile().exists() && templateDir.toFile().isDirectory()) {
                options = options.withBaseCodeSubmissionDirectory(templateDir.toFile());
            }
            
            JPlagResult result = JPlag.run(options);
            
            // Output report
            ReportObjectFactory reportObjectFactory = new ReportObjectFactory(outputReportDir.toFile());
            reportObjectFactory.createAndSaveReport(result);
            
            logger.info("JPlag plagiarism report generated at {}", outputReportDir);
            
        } catch (ExitException e) {
            logger.error("JPlag execution failed with exit exception", e);
        } catch (Exception e) {
            logger.error("Failed to execute JPlag plagiarism checks", e);
        }
    }
}

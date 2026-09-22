package com.jobera.config;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.jobera.entity.Resume;
import com.jobera.repository.ResumeRepository;

/**
 * Cleans up files that would otherwise accumulate on disk forever: generated-credential CSVs
 * (which contain new users' plaintext passwords, so a shorter retention is a security win, not
 * just tidiness) and resume files whose database row is gone (a deleted Resume that predates the
 * pairing in {@code ResumeService.deleteResume}, or a save that failed after the file was written).
 */
@Component
public class FileLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(FileLifecycleService.class);

    private static final int CREDENTIALS_RETENTION_DAYS = 14;
    private static final Pattern CREDENTIALS_FILE = Pattern.compile("credentials_job_[0-9]+_[0-9]+\\.csv");

    private final ResumeRepository resumeRepository;
    private final Path uploadsDir;
    private final Path externalResumesDir;

    public FileLifecycleService(ResumeRepository resumeRepository,
                                @Value("${app.uploads-dir:uploads}") String uploadsDir) {
        this.resumeRepository = resumeRepository;
        this.uploadsDir = Paths.get(uploadsDir);
        this.externalResumesDir = this.uploadsDir.resolve("external_resumes");
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    public void runDailyCleanup() {
        deleteExpiredCredentialFiles();
        deleteOrphanedResumeFiles();
    }

    private void deleteExpiredCredentialFiles() {
        if (!Files.isDirectory(externalResumesDir)) {
            return;
        }
        Instant cutoff = Instant.now().minus(CREDENTIALS_RETENTION_DAYS, ChronoUnit.DAYS);
        int deleted = 0;

        try (DirectoryStream<Path> entries = Files.newDirectoryStream(externalResumesDir)) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                if (!CREDENTIALS_FILE.matcher(name).matches()) {
                    continue;
                }
                if (Files.getLastModifiedTime(entry).toInstant().isBefore(cutoff) && Files.deleteIfExists(entry)) {
                    deleted++;
                }
            }
        } catch (IOException e) {
            log.warn("Failed to clean up expired credential files: {}", e.getMessage());
        }

        if (deleted > 0) {
            log.info("Deleted {} expired credential file(s) older than {} days", deleted, CREDENTIALS_RETENTION_DAYS);
        }
    }

    private void deleteOrphanedResumeFiles() {
        if (!Files.isDirectory(uploadsDir)) {
            return;
        }
        Set<String> referencedFileNames = new HashSet<>();
        for (Resume resume : resumeRepository.findAll()) {
            String filePath = resume.getFilePath();
            if (filePath != null) {
                referencedFileNames.add(fileNameOf(filePath));
            }
        }

        int deleted = 0;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(uploadsDir)) {
            for (Path entry : entries) {
                if (Files.isDirectory(entry)) {
                    continue;
                }
                String name = entry.getFileName().toString();
                if (looksLikeResumeUpload(name) && !referencedFileNames.contains(name) && Files.deleteIfExists(entry)) {
                    deleted++;
                }
            }
        } catch (IOException e) {
            log.warn("Failed to clean up orphaned resume files: {}", e.getMessage());
        }

        if (deleted > 0) {
            log.info("Deleted {} orphaned resume file(s) with no matching database record", deleted);
        }
    }

    /** Resumes are saved as "<epoch-millis>_<original-file-name>"; anything else in this root is left alone. */
    private boolean looksLikeResumeUpload(String fileName) {
        int underscore = fileName.indexOf('_');
        if (underscore <= 0) {
            return false;
        }
        try {
            Long.parseLong(fileName.substring(0, underscore));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String fileNameOf(String filePath) {
        int lastSlash = filePath.lastIndexOf('/');
        return lastSlash >= 0 ? filePath.substring(lastSlash + 1) : filePath;
    }
}

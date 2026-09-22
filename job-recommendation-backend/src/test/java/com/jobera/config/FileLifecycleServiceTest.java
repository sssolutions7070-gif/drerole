package com.jobera.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.Resume;
import com.jobera.repository.ResumeRepository;

@ExtendWith(MockitoExtension.class)
class FileLifecycleServiceTest {

    @Mock ResumeRepository resumeRepository;

    @TempDir Path uploadsDir;

    FileLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new FileLifecycleService(resumeRepository, uploadsDir.toString());
    }

    private void ageFile(Path file, int daysOld) throws IOException {
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minus(daysOld, ChronoUnit.DAYS)));
    }

    @Test
    void deletesCredentialFilesOlderThanTheRetentionWindow() throws IOException {
        Path externalDir = Files.createDirectories(uploadsDir.resolve("external_resumes"));
        Path oldFile = Files.createFile(externalDir.resolve("credentials_job_5_1000.csv"));
        ageFile(oldFile, 20);
        when(resumeRepository.findAll()).thenReturn(List.of());

        service.runDailyCleanup();

        assertFalse(Files.exists(oldFile));
    }

    @Test
    void keepsCredentialFilesWithinTheRetentionWindow() throws IOException {
        Path externalDir = Files.createDirectories(uploadsDir.resolve("external_resumes"));
        Path recentFile = Files.createFile(externalDir.resolve("credentials_job_5_2000.csv"));
        ageFile(recentFile, 2);
        when(resumeRepository.findAll()).thenReturn(List.of());

        service.runDailyCleanup();

        assertTrue(Files.exists(recentFile));
    }

    @Test
    void neverTouchesFilesThatDoNotMatchTheCredentialsPattern() throws IOException {
        Path externalDir = Files.createDirectories(uploadsDir.resolve("external_resumes"));
        Path otherFile = Files.createFile(externalDir.resolve("some_resume.pdf"));
        ageFile(otherFile, 100);
        when(resumeRepository.findAll()).thenReturn(List.of());

        service.runDailyCleanup();

        assertTrue(Files.exists(otherFile));
    }

    @Test
    void deletesResumeFilesWithNoMatchingDatabaseRow() throws IOException {
        Path orphan = Files.createFile(uploadsDir.resolve("1700000000000_orphan.pdf"));
        when(resumeRepository.findAll()).thenReturn(List.of());

        service.runDailyCleanup();

        assertFalse(Files.exists(orphan));
    }

    @Test
    void keepsResumeFilesThatAreReferencedByADatabaseRow() throws IOException {
        Path referenced = Files.createFile(uploadsDir.resolve("1700000000000_kept.pdf"));
        Resume resume = new Resume();
        resume.setFilePath("/uploads/1700000000000_kept.pdf");
        when(resumeRepository.findAll()).thenReturn(List.of(resume));

        service.runDailyCleanup();

        assertTrue(Files.exists(referenced));
    }

    @Test
    void neverDeletesFilesThatDoNotLookLikeResumeUploads() throws IOException {
        Path notAResume = Files.createFile(uploadsDir.resolve("readme.txt"));
        when(resumeRepository.findAll()).thenReturn(List.of());

        service.runDailyCleanup();

        assertTrue(Files.exists(notAResume));
    }

    @Test
    void neverDescendsIntoSubdirectoriesWhenLookingForOrphanedResumes() throws IOException {
        Path profilesDir = Files.createDirectories(uploadsDir.resolve("profiles"));
        Path profilePicture = Files.createFile(profilesDir.resolve("1700000000000_avatar.png"));
        when(resumeRepository.findAll()).thenReturn(List.of());

        service.runDailyCleanup();

        assertTrue(Files.exists(profilePicture));
    }

    @Test
    void missingDirectoriesDoNotCauseAnError() {
        Path missing = uploadsDir.resolve("does-not-exist");
        FileLifecycleService serviceWithMissingDir = new FileLifecycleService(resumeRepository, missing.toString());

        serviceWithMissingDir.runDailyCleanup();
    }
}

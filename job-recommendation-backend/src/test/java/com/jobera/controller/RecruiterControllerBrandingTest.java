package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import com.jobera.entity.Recruiter;
import com.jobera.service.RecruiterService;

/** Only the new richer-employer-branding additions (the extended /profile fields, and the
 *  logo/banner upload endpoints) - this controller had no prior dedicated test file. */
@ExtendWith(MockitoExtension.class)
class RecruiterControllerBrandingTest {

    @Mock RecruiterService recruiterService;

    RecruiterController controller;

    @BeforeEach
    void setUp() {
        controller = new RecruiterController(recruiterService);
    }

    @AfterEach
    void cleanUp() throws IOException {
        deleteTestFiles("uploads/company-logos");
        deleteTestFiles("uploads/company-banners");
    }

    private void deleteTestFiles(String dir) throws IOException {
        Path path = Paths.get(dir);
        if (Files.exists(path)) {
            try (var files = Files.list(path)) {
                files.filter(p -> p.getFileName().toString().startsWith("5_")).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            }
        }
    }

    @Test
    void updateProfilePassesTheBrandingFieldsThrough() {
        Recruiter saved = new Recruiter();
        when(recruiterService.updateRecruiterProfile(eq(5L), isNull(), isNull(), isNull(), isNull(), isNull(),
            isNull(), eq("Our mission"), eq("[\"Remote\"]"), eq("https://linkedin.com/x"), eq("https://twitter.com/x"),
            eq(2020), eq("NYC"))).thenReturn(saved);

        Map<String, String> request = Map.of(
            "missionStatement", "Our mission",
            "benefits", "[\"Remote\"]",
            "companyLinkedin", "https://linkedin.com/x",
            "companyTwitter", "https://twitter.com/x",
            "foundedYear", "2020",
            "headquarters", "NYC"
        );

        ResponseEntity<Map<String, Object>> response = controller.updateRecruiterProfile(5L, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void updateProfileToleratesAnUnparseableFoundedYear() {
        Recruiter saved = new Recruiter();
        when(recruiterService.updateRecruiterProfile(eq(5L), isNull(), isNull(), isNull(), isNull(), isNull(),
            isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull())).thenReturn(saved);

        ResponseEntity<Map<String, Object>> response = controller.updateRecruiterProfile(5L, Map.of("foundedYear", "not-a-year"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void uploadsACompanyLogoAndStoresItsUrl() throws IOException {
        Recruiter recruiter = new Recruiter();
        recruiter.setCompanyLogo("/uploads/company-logos/5_123_abc.png");
        when(recruiterService.updateCompanyLogo(eq(5L), any(String.class))).thenReturn(recruiter);
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", "fake-bytes".getBytes());

        ResponseEntity<Map<String, String>> response = controller.uploadCompanyLogo(5L, file);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().get("companyLogo").startsWith("/uploads/company-logos/5_"));
    }

    @Test
    void uploadsACompanyBannerAndStoresItsUrl() throws IOException {
        Recruiter recruiter = new Recruiter();
        recruiter.setCompanyBanner("/uploads/company-banners/5_123_abc.png");
        when(recruiterService.updateCompanyBanner(eq(5L), any(String.class))).thenReturn(recruiter);
        MockMultipartFile file = new MockMultipartFile("file", "banner.png", "image/png", "fake-bytes".getBytes());

        ResponseEntity<Map<String, String>> response = controller.uploadCompanyBanner(5L, file);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().get("companyBanner").startsWith("/uploads/company-banners/5_"));
    }

    @Test
    void rejectsAnEmptyLogoFile() throws IOException {
        MockMultipartFile empty = new MockMultipartFile("file", "logo.png", "image/png", new byte[0]);

        ResponseEntity<Map<String, String>> response = controller.uploadCompanyLogo(5L, empty);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}

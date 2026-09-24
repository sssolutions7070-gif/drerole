package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.jobera.entity.Recruiter;
import com.jobera.service.RecruiterVerificationService;

@ExtendWith(MockitoExtension.class)
class AdminVerificationControllerTest {

    @Mock RecruiterVerificationService recruiterVerificationService;

    AdminVerificationController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminVerificationController(recruiterVerificationService);
    }

    @Test
    void pendingReturnsTheList() {
        when(recruiterVerificationService.getPending()).thenReturn(List.of(Map.of("companyName", "Acme")));

        ResponseEntity<List<Map<String, Object>>> response = controller.pending();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void approveReturnsTheUpdatedStatus() {
        when(recruiterVerificationService.approve(100L)).thenReturn(new Recruiter());
        when(recruiterVerificationService.getStatus(100L)).thenReturn(Map.of("verificationStatus", "VERIFIED"));

        ResponseEntity<?> response = controller.approve(100L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void approveReturns404ForAnUnknownRecruiter() {
        when(recruiterVerificationService.approve(404L))
            .thenThrow(new RecruiterVerificationService.RecruiterNotFoundException(404L));

        ResponseEntity<?> response = controller.approve(404L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void downloadDocumentStreamsTheFile(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("registration.pdf");
        Files.writeString(file, "fake pdf content");
        when(recruiterVerificationService.getDocumentFile(100L)).thenReturn(file);

        ResponseEntity<?> response = controller.downloadDocument(100L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void downloadDocumentReturns404WhenNothingWasSubmitted() {
        when(recruiterVerificationService.getDocumentFile(100L))
            .thenThrow(new RecruiterVerificationService.DocumentNotFoundException(100L));

        ResponseEntity<?> response = controller.downloadDocument(100L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void rejectReturnsTheUpdatedStatus() {
        when(recruiterVerificationService.reject(100L, "bad doc")).thenReturn(new Recruiter());
        when(recruiterVerificationService.getStatus(100L)).thenReturn(Map.of("verificationStatus", "REJECTED"));

        ResponseEntity<?> response = controller.reject(100L, Map.of("reason", "bad doc"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}

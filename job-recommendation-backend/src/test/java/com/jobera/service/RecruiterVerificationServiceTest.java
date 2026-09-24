package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.jobera.entity.Recruiter;
import com.jobera.entity.User;
import com.jobera.repository.RecruiterRepository;

@ExtendWith(MockitoExtension.class)
class RecruiterVerificationServiceTest {

    @Mock RecruiterRepository recruiterRepository;

    RecruiterVerificationService service;

    User owner;
    Recruiter recruiter;

    @BeforeEach
    void setUp() {
        service = new RecruiterVerificationService(recruiterRepository);
        owner = new User();
        owner.setId(1L);
        recruiter = new Recruiter();
        recruiter.setId(100L);
        recruiter.setUser(owner);
        lenient().when(recruiterRepository.findById(100L)).thenReturn(Optional.of(recruiter));
        lenient().when(recruiterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private MultipartFile aDocument() {
        return new MockMultipartFile("file", "registration.pdf", "application/pdf", "fake pdf bytes".getBytes());
    }

    @Test
    void ownerCanSubmitADocumentAndItMovesToPending() {
        Recruiter result = service.submit(100L, 1L, aDocument());

        assertEquals(Recruiter.VERIFICATION_PENDING, result.getVerificationStatus());
        assertEquals(true, result.getVerificationDocumentPath().startsWith("/uploads/verifications/"));
    }

    @Test
    void nonOwnerCannotSubmitADocument() {
        assertThrows(RecruiterVerificationService.NotTheAccountOwnerException.class,
            () -> service.submit(100L, 2L, aDocument()));
    }

    @Test
    void submittingWithoutAFileFails() {
        assertThrows(RecruiterVerificationService.MissingDocumentException.class,
            () -> service.submit(100L, 1L, null));
    }

    @Test
    void submittingAnEmptyFileFails() {
        MultipartFile empty = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        assertThrows(RecruiterVerificationService.MissingDocumentException.class,
            () -> service.submit(100L, 1L, empty));
    }

    @Test
    void aFailedFileWriteIsWrappedInADocumentUploadException() throws IOException {
        MultipartFile broken = mock(MultipartFile.class);
        when(broken.isEmpty()).thenReturn(false);
        when(broken.getOriginalFilename()).thenReturn("doc.pdf");
        when(broken.getInputStream()).thenThrow(new IOException("disk full"));

        assertThrows(RecruiterVerificationService.DocumentUploadException.class,
            () -> service.submit(100L, 1L, broken));
    }

    @Test
    void statusReflectsWhatWasStored() {
        recruiter.setVerificationStatus(Recruiter.VERIFICATION_REJECTED);
        recruiter.setVerificationRejectionReason("Document was unreadable");

        Map<String, Object> status = service.getStatus(100L);

        assertEquals(Recruiter.VERIFICATION_REJECTED, status.get("verificationStatus"));
        assertEquals("Document was unreadable", status.get("verificationRejectionReason"));
    }

    @Test
    void getPendingReturnsAViewNotTheRawEntity() {
        recruiter.setCompanyName("Acme");
        recruiter.setVerificationStatus(Recruiter.VERIFICATION_PENDING);
        when(recruiterRepository.findByVerificationStatus(Recruiter.VERIFICATION_PENDING)).thenReturn(List.of(recruiter));

        List<Map<String, Object>> pending = service.getPending();

        assertEquals(1, pending.size());
        assertEquals("Acme", pending.get(0).get("companyName"));
        assertEquals(false, pending.get(0).containsKey("user"));
    }

    @Test
    void approvingSetsStatusToVerified() {
        Recruiter result = service.approve(100L);

        assertEquals(Recruiter.VERIFICATION_VERIFIED, result.getVerificationStatus());
    }

    @Test
    void rejectingSetsStatusAndReason() {
        Recruiter result = service.reject(100L, "Document didn't match company name");

        assertEquals(Recruiter.VERIFICATION_REJECTED, result.getVerificationStatus());
        assertEquals("Document didn't match company name", result.getVerificationRejectionReason());
    }

    @Test
    void getDocumentFileReturnsThePathForAnExistingSubmission() {
        Recruiter withDoc = service.submit(100L, 1L, aDocument());
        when(recruiterRepository.findById(100L)).thenReturn(Optional.of(withDoc));

        java.nio.file.Path path = service.getDocumentFile(100L);

        assertEquals(true, java.nio.file.Files.exists(path));
    }

    @Test
    void getDocumentFileFailsWhenNothingWasSubmitted() {
        assertThrows(RecruiterVerificationService.DocumentNotFoundException.class,
            () -> service.getDocumentFile(100L));
    }

    @Test
    void actingOnAnUnknownRecruiterFails() {
        when(recruiterRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(RecruiterVerificationService.RecruiterNotFoundException.class, () -> service.approve(404L));
    }
}

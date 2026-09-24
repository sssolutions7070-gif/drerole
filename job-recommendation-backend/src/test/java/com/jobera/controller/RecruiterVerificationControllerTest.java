package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.jobera.config.AuthInterceptor;
import com.jobera.entity.Recruiter;
import com.jobera.entity.User;
import com.jobera.service.RecruiterVerificationService;

import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
class RecruiterVerificationControllerTest {

    @Mock RecruiterVerificationService recruiterVerificationService;

    RecruiterVerificationController controller;

    @BeforeEach
    void setUp() {
        controller = new RecruiterVerificationController(recruiterVerificationService);
    }

    private HttpServletRequest requestFor(long userId) {
        User user = new User();
        user.setId(userId);
        HttpServletRequest request = mock(HttpServletRequest.class);
        lenient().when(request.getAttribute(AuthInterceptor.AUTHENTICATED_USER)).thenReturn(user);
        return request;
    }

    @Test
    void getStatusReturnsTheServiceResult() {
        when(recruiterVerificationService.getStatus(100L)).thenReturn(Map.of("verificationStatus", "NONE"));

        ResponseEntity<?> response = controller.getStatus(100L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void getStatusReturns404ForAnUnknownRecruiter() {
        when(recruiterVerificationService.getStatus(404L))
            .thenThrow(new RecruiterVerificationService.RecruiterNotFoundException(404L));

        ResponseEntity<?> response = controller.getStatus(404L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void submitReturnsTheUpdatedStatus() {
        MultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "bytes".getBytes());
        when(recruiterVerificationService.submit(100L, 1L, file)).thenReturn(new Recruiter());
        when(recruiterVerificationService.getStatus(100L)).thenReturn(Map.of("verificationStatus", "PENDING"));

        ResponseEntity<?> response = controller.submit(100L, file, requestFor(1L));

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void submitReturns403ForNonOwner() {
        MultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "bytes".getBytes());
        when(recruiterVerificationService.submit(100L, 2L, file))
            .thenThrow(new RecruiterVerificationService.NotTheAccountOwnerException());

        ResponseEntity<?> response = controller.submit(100L, file, requestFor(2L));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void submitReturns400WhenNoDocumentIsProvided() {
        when(recruiterVerificationService.submit(any(), any(), any()))
            .thenThrow(new RecruiterVerificationService.MissingDocumentException());

        ResponseEntity<?> response = controller.submit(100L, null, requestFor(1L));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
